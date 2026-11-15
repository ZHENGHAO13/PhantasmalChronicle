package com.phantasm.briefing.service;

import com.phantasm.briefing.PhantasmBriefing;
import com.phantasm.briefing.data.NpcBindingDataManager;
import com.phantasm.briefing.data.NpcBindingSpec;
import com.phantasm.briefing.data.NpcStructureSpawnSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

public final class NpcStructureSpawnService {
    private static final String TAG_SPAWN_INSTANCE = "PhantasmBriefingNpcSpawnInstance";
    private static final String TAG_SURFACE_PLACEMENT_VERSION = "PhantasmBriefingNpcSurfacePlacementVersion";
    private static final int SURFACE_PLACEMENT_VERSION = 1;
    private static final int SURFACE_SEARCH_RADIUS = 32;
    private static final int PLAYER_SCAN_CHUNK_RADIUS = 2;

    private NpcStructureSpawnService() {
    }

    public static ScanResult processLoadedArea(ServerPlayer player, ChunkPos center) {
        ServerLevel level = player.serverLevel();
        ScanResult result = ScanResult.NO_MATCHING_STRUCTURE;
        for (int dz = -PLAYER_SCAN_CHUNK_RADIUS; dz <= PLAYER_SCAN_CHUNK_RADIUS; dz++) {
            for (int dx = -PLAYER_SCAN_CHUNK_RADIUS; dx <= PLAYER_SCAN_CHUNK_RADIUS; dx++) {
                ChunkPos chunkPos = new ChunkPos(center.x + dx, center.z + dz);
                if (level.getChunkSource().hasChunk(chunkPos.x, chunkPos.z)) {
                    result = result.combine(processLoadedChunk(player, chunkPos));
                }
            }
        }
        return result;
    }

    private static ScanResult processLoadedChunk(ServerPlayer player, ChunkPos chunkPos) {
        ServerLevel level = player.serverLevel();
        List<NpcBindingSpec> bindings = NpcBindingDataManager.getInstance().getStructureSpawnBindings();
        if (bindings.isEmpty()) {
            return ScanResult.NO_MATCHING_STRUCTURE;
        }

        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
        if (chunk == null) {
            return ScanResult.NO_MATCHING_STRUCTURE;
        }

        String dimensionId = level.dimension().location().toString();
        Registry<Structure> structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Set<String> configuredStructureIds = bindings.stream()
                .flatMap(binding -> binding.spawnRules().stream())
                .filter(rule -> rule.dimension().isBlank() || rule.dimension().equals(dimensionId))
                .map(NpcStructureSpawnSpec::structureId)
                .collect(Collectors.toSet());
        if (configuredStructureIds.isEmpty()) {
            return ScanResult.NO_MATCHING_STRUCTURE;
        }

        Set<StructureStart> starts = Collections.newSetFromMap(new IdentityHashMap<>());
        starts.addAll(chunk.getAllStarts().values());
        starts.addAll(level.structureManager().startsForStructure(chunkPos, structure -> {
            ResourceLocation key = structureRegistry.getKey(structure);
            return key != null && configuredStructureIds.contains(key.toString());
        }));

        ScanResult result = ScanResult.NO_MATCHING_STRUCTURE;
        for (StructureStart start : starts) {
            if (start == null || !start.isValid()) {
                continue;
            }
            ResourceLocation structureKey = structureRegistry.getKey(start.getStructure());
            if (structureKey == null || !configuredStructureIds.contains(structureKey.toString())) {
                continue;
            }
            result = result.combine(spawnForStructure(player, structureKey.toString(), start, bindings));
        }
        return result;
    }

    private static ScanResult spawnForStructure(
            ServerPlayer player,
            String structureId,
            StructureStart start,
            List<NpcBindingSpec> bindings
    ) {
        ServerLevel level = player.serverLevel();
        String dimensionId = level.dimension().location().toString();
        List<StructureSpawnTarget> targets = new ArrayList<>();
        for (NpcBindingSpec binding : bindings) {
            for (NpcStructureSpawnSpec rule : binding.spawnRules()) {
                if (rule.matches(structureId, dimensionId)) {
                    targets.add(new StructureSpawnTarget(binding, rule));
                }
            }
        }
        if (targets.isEmpty()) {
            return ScanResult.NO_MATCHING_STRUCTURE;
        }

        NpcStructureSpawnSavedData savedData = NpcStructureSpawnSavedData.get(level);
        String completionId = createStructureCompletionId(level, structureId, start, targets);
        if (BriefingPlayerData.hasCompletedNpcStructure(player, completionId)) {
            markPlayerStructureCompleted(player, completionId, start);
            return ScanResult.COMPLETE;
        }
        if (savedData.hasCompletedStructure(completionId)) {
            markPlayerStructureCompleted(player, completionId, start);
            return ScanResult.COMPLETE;
        }

        boolean allSpawned = true;
        for (StructureSpawnTarget target : targets) {
            if (!ensureSpawned(level, structureId, start, target.binding(), target.rule(), savedData)) {
                allSpawned = false;
            }
        }
        if (!allSpawned) {
            return ScanResult.PENDING;
        }

        savedData.markStructureCompleted(completionId);
        markPlayerStructureCompleted(player, completionId, start);
        return ScanResult.COMPLETE;
    }

    private static void markPlayerStructureCompleted(
            ServerPlayer player,
            String completionId,
            StructureStart start
    ) {
        BoundingBox box = start.getBoundingBox();
        BriefingPlayerData.markNpcStructureCompleted(
                player,
                completionId,
                player.level().dimension().location().toString(),
                NpcBindingDataManager.getInstance().getStructureSpawnFingerprint(),
                box.minX() >> 4,
                box.maxX() >> 4,
                box.minZ() >> 4,
                box.maxZ() >> 4
        );
    }

    private static boolean ensureSpawned(
            ServerLevel level,
            String structureId,
            StructureStart start,
            NpcBindingSpec binding,
            NpcStructureSpawnSpec rule,
            NpcStructureSpawnSavedData savedData
    ) {
        String instanceId = createInstanceId(structureId, start, binding, rule);
        Optional<LivingEntity> existingInstance = findExistingInstance(level, start, rule, instanceId);
        if (existingInstance.isPresent()) {
            LivingEntity entity = existingInstance.get();
            if (needsSurfaceMigration(entity, rule)) {
                BlockPos spawnPos = resolveSpawnPosition(level, start, rule);
                if (spawnPos == null) {
                    return false;
                }
                migrateLegacySurfacePlacement(level, entity, spawnPos, binding, rule);
            }
            savedData.markSpawned(instanceId);
            return true;
        }

        if (savedData.hasSpawned(instanceId)) {
            return true;
        }

        BlockPos spawnPos = resolveSpawnPosition(level, start, rule);
        if (spawnPos == null) {
            return false;
        }

        ChunkPos spawnChunk = new ChunkPos(spawnPos);
        if (!level.getChunkSource().hasChunk(spawnChunk.x, spawnChunk.z)) {
            return false;
        }

        if (spawnEntity(level, spawnPos, binding, rule, instanceId)) {
            savedData.markSpawned(instanceId);
            PhantasmBriefing.LOGGER.info(
                    "[PhantasmBriefing] Spawned NPC binding '{}' for structure '{}' at {}",
                    binding.bindingId(),
                    structureId,
                    spawnPos
            );
            return true;
        }
        return false;
    }

    private static boolean spawnEntity(
            ServerLevel level,
            BlockPos spawnPos,
            NpcBindingSpec binding,
            NpcStructureSpawnSpec rule,
            String instanceId
    ) {
        NpcSpawnService.SpawnResult result = NpcSpawnService.spawn(
                level,
                new net.minecraft.world.phys.Vec3(
                        spawnPos.getX() + 0.5D,
                        spawnPos.getY(),
                        spawnPos.getZ() + 0.5D
                ),
                rule.yaw(),
                binding,
                MobSpawnType.STRUCTURE,
                rule.noAi(),
                rule.nameVisible(),
                rule.villagerLevel(),
                livingEntity -> {
                    livingEntity.getPersistentData().putString(TAG_SPAWN_INSTANCE, instanceId);
                    livingEntity.getPersistentData().putInt(
                            TAG_SURFACE_PLACEMENT_VERSION,
                            SURFACE_PLACEMENT_VERSION
                    );
                }
        );
        if (!result.succeeded()) {
            PhantasmBriefing.LOGGER.warn(
                    "[PhantasmBriefing] Cannot spawn NPC binding '{}' at {}: {}",
                    binding.bindingId(),
                    spawnPos,
                    result.error()
            );
        }
        return result.succeeded();
    }

    private static Optional<LivingEntity> findExistingInstance(
            ServerLevel level,
            StructureStart start,
            NpcStructureSpawnSpec rule,
            String instanceId
    ) {
        BoundingBox box = start.getBoundingBox();
        double horizontalMargin = Math.max(
                64.0D,
                Math.max(Math.abs(rule.offsetX()), Math.abs(rule.offsetZ())) + 16.0D
        );
        AABB searchBox = new AABB(
                box.minX() - horizontalMargin,
                level.getMinBuildHeight(),
                box.minZ() - horizontalMargin,
                box.maxX() + horizontalMargin + 1.0D,
                level.getMaxBuildHeight(),
                box.maxZ() + horizontalMargin + 1.0D
        );
        return level.getEntitiesOfClass(
                LivingEntity.class,
                searchBox,
                entity -> instanceId.equals(entity.getPersistentData().getString(TAG_SPAWN_INSTANCE))
        ).stream().findFirst();
    }

    @Nullable
    private static BlockPos resolveSpawnPosition(ServerLevel level, StructureStart start, NpcStructureSpawnSpec rule) {
        BoundingBox box = start.getBoundingBox();
        BlockPos center = box.getCenter();
        BlockPos base = switch (rule.anchor()) {
            case "center" -> center;
            case "min" -> new BlockPos(box.minX(), box.minY(), box.minZ());
            case "max" -> new BlockPos(box.maxX(), box.minY(), box.maxZ());
            case "start_chunk" -> new BlockPos(
                    start.getChunkPos().getMiddleBlockX(),
                    box.minY(),
                    start.getChunkPos().getMiddleBlockZ()
            );
            default -> new BlockPos(center.getX(), box.minY(), center.getZ());
        };
        BlockPos configuredPosition = base.offset(rule.offsetX(), rule.offsetY(), rule.offsetZ());
        if (!rule.snapToSurface()) {
            return configuredPosition;
        }

        return findSafeSurfacePosition(level, configuredPosition, rule.offsetY());
    }

    @Nullable
    private static BlockPos findSafeSurfacePosition(ServerLevel level, BlockPos configuredPosition, int offsetY) {
        List<BlockPos> candidates = new ArrayList<>();
        for (int dz = -SURFACE_SEARCH_RADIUS; dz <= SURFACE_SEARCH_RADIUS; dz++) {
            for (int dx = -SURFACE_SEARCH_RADIUS; dx <= SURFACE_SEARCH_RADIUS; dx++) {
                int x = configuredPosition.getX() + dx;
                int z = configuredPosition.getZ() + dz;
                LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                if (chunk == null) {
                    continue;
                }

                int surfaceAirY = chunk.getHeight(
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        x & 15,
                        z & 15
                );
                int requestedY = surfaceAirY + offsetY - 1;
                BlockPos candidate = new BlockPos(x, requestedY, z);
                if (isSafeStandingPosition(level, candidate)) {
                    candidates.add(candidate);
                }
            }
        }

        if (candidates.isEmpty()) {
            PhantasmBriefing.LOGGER.warn(
                    "[PhantasmBriefing] No safe loaded surface position found near {}",
                    configuredPosition
            );
            return null;
        }

        List<Integer> sortedHeights = candidates.stream()
                .map(BlockPos::getY)
                .sorted()
                .toList();
        int terrainReferenceY = sortedHeights.get(sortedHeights.size() / 4);
        return candidates.stream()
                .min(Comparator
                        .comparingLong((BlockPos pos) -> surfaceCandidateScore(level, pos, configuredPosition, terrainReferenceY))
                        .thenComparingInt(BlockPos::getX)
                        .thenComparingInt(BlockPos::getZ))
                .orElse(null);
    }

    private static boolean isSafeStandingPosition(ServerLevel level, BlockPos feetPos) {
        if (feetPos.getY() <= level.getMinBuildHeight() || feetPos.getY() + 1 >= level.getMaxBuildHeight()) {
            return false;
        }
        if (!level.getWorldBorder().isWithinBounds(feetPos)) {
            return false;
        }

        BlockPos headPos = feetPos.above();
        BlockPos supportPos = feetPos.below();
        var feetState = level.getBlockState(feetPos);
        var headState = level.getBlockState(headPos);
        var supportState = level.getBlockState(supportPos);
        return feetState.getCollisionShape(level, feetPos).isEmpty()
                && headState.getCollisionShape(level, headPos).isEmpty()
                && feetState.getFluidState().isEmpty()
                && headState.getFluidState().isEmpty()
                && supportState.getFluidState().isEmpty()
                && supportState.isFaceSturdy(level, supportPos, Direction.UP);
    }

    private static long surfaceCandidateScore(
            ServerLevel level,
            BlockPos candidate,
            BlockPos configuredPosition,
            int terrainReferenceY
    ) {
        long dx = candidate.getX() - configuredPosition.getX();
        long dz = candidate.getZ() - configuredPosition.getZ();
        long horizontalDistance = dx * dx + dz * dz;
        long terrainHeightPenalty = Math.abs((long) candidate.getY() - terrainReferenceY);
        long shallowSupportPenalty = 4L - solidSupportDepth(level, candidate);
        return horizontalDistance + terrainHeightPenalty * 64L + shallowSupportPenalty * 512L;
    }

    private static int solidSupportDepth(ServerLevel level, BlockPos feetPos) {
        int depth = 0;
        for (int step = 1; step <= 4; step++) {
            BlockPos supportPos = feetPos.below(step);
            var supportState = level.getBlockState(supportPos);
            if (!supportState.getFluidState().isEmpty()
                    || supportState.getCollisionShape(level, supportPos).isEmpty()) {
                break;
            }
            depth++;
        }
        return depth;
    }

    private static boolean needsSurfaceMigration(LivingEntity entity, NpcStructureSpawnSpec rule) {
        return rule.snapToSurface()
                && entity.getPersistentData().getInt(TAG_SURFACE_PLACEMENT_VERSION) < SURFACE_PLACEMENT_VERSION;
    }

    private static void migrateLegacySurfacePlacement(
            ServerLevel level,
            LivingEntity entity,
            BlockPos spawnPos,
            NpcBindingSpec binding,
            NpcStructureSpawnSpec rule
    ) {
        entity.moveTo(
                spawnPos.getX() + 0.5D,
                spawnPos.getY(),
                spawnPos.getZ() + 0.5D,
                rule.yaw(),
                0.0F
        );
        entity.setYHeadRot(rule.yaw());
        entity.setYBodyRot(rule.yaw());
        entity.fallDistance = 0.0F;
        entity.getPersistentData().putInt(TAG_SURFACE_PLACEMENT_VERSION, SURFACE_PLACEMENT_VERSION);
        PhantasmBriefing.LOGGER.info(
                "[PhantasmBriefing] Moved legacy underground NPC binding '{}' to safe surface position {}",
                binding.bindingId(),
                spawnPos
        );
    }

    private static String createInstanceId(
            String structureId,
            StructureStart start,
            NpcBindingSpec binding,
            NpcStructureSpawnSpec rule
    ) {
        ChunkPos startChunk = start.getChunkPos();
        return structureId
                + "@" + startChunk.x + "," + startChunk.z
                + "|" + binding.bindingId()
                + "|" + rule.ruleId();
    }

    private static String createStructureCompletionId(
            ServerLevel level,
            String structureId,
            StructureStart start,
            List<StructureSpawnTarget> targets
    ) {
        String configuredRules = targets.stream()
                .map(target -> target.binding().bindingId() + "/" + target.rule().ruleId())
                .sorted()
                .collect(Collectors.joining(","));
        ChunkPos startChunk = start.getChunkPos();
        return level.dimension().location()
                + "|" + structureId
                + "@" + startChunk.x + "," + startChunk.z
                + "|" + configuredRules;
    }

    private record StructureSpawnTarget(NpcBindingSpec binding, NpcStructureSpawnSpec rule) {
    }

    public enum ScanResult {
        NO_MATCHING_STRUCTURE,
        COMPLETE,
        PENDING;

        private ScanResult combine(ScanResult other) {
            if (this == PENDING || other == PENDING) {
                return PENDING;
            }
            if (this == COMPLETE || other == COMPLETE) {
                return COMPLETE;
            }
            return NO_MATCHING_STRUCTURE;
        }
    }
}
