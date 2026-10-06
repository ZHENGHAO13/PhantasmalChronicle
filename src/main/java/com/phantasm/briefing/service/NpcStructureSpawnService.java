package com.phantasm.briefing.service;

import com.phantasm.briefing.PhantasmBriefing;
import com.phantasm.briefing.data.NpcBindingDataManager;
import com.phantasm.briefing.data.NpcBindingSpec;
import com.phantasm.briefing.data.NpcStructureSpawnSpec;
import com.phantasm.briefing.event.NpcStructureSpawnEvents;
import com.phantasm.briefing.util.QuestNpcHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

public final class NpcStructureSpawnService {
    private static final String TAG_SPAWN_INSTANCE = "PhantasmBriefingNpcSpawnInstance";
    private static final int PLAYER_SCAN_CHUNK_RADIUS = 2;
    private static final int ADMIN_SCAN_CHUNK_RADIUS = 6;
    private static final double ADMIN_NPC_SEARCH_RADIUS = 96.0D;
    private static final double ADMIN_STRUCTURE_SEARCH_RADIUS = 160.0D;
    private static final Set<String> VERIFIED_SPAWN_INSTANCES = ConcurrentHashMap.newKeySet();
    private static final int SPAWN_FAILURE_LOG_COOLDOWN_TICKS = 200;
    private static final Map<String, Long> LAST_SPAWN_FAILURE_LOG = new ConcurrentHashMap<>();

    private NpcStructureSpawnService() {
    }

    public static ScanResult processLoadedArea(ServerPlayer player, ChunkPos center) {
        return processLoadedArea(player, center, PLAYER_SCAN_CHUNK_RADIUS);
    }

    private static ScanResult processLoadedArea(ServerPlayer player, ChunkPos center, int radius) {
        ServerLevel level = player.serverLevel();
        ScanResult result = ScanResult.NO_MATCHING_STRUCTURE;
        Set<String> processedStructureStarts = new HashSet<>();
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                ChunkPos chunkPos = new ChunkPos(center.x + dx, center.z + dz);
                if (level.getChunkSource().hasChunk(chunkPos.x, chunkPos.z)) {
                    result = result.combine(processLoadedChunk(player, chunkPos, processedStructureStarts));
                }
            }
        }
        return result;
    }

    private static ScanResult processLoadedChunk(
            ServerPlayer player,
            ChunkPos chunkPos,
            Set<String> processedStructureStarts
    ) {
        ServerLevel level = player.serverLevel();
        NpcBindingDataManager bindingManager = NpcBindingDataManager.getInstance();
        if (bindingManager.getStructureSpawnBindings().isEmpty()) {
            return ScanResult.NO_MATCHING_STRUCTURE;
        }

        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
        if (chunk == null) {
            return ScanResult.NO_MATCHING_STRUCTURE;
        }

        String dimensionId = level.dimension().location().toString();
        Registry<Structure> structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Set<String> configuredStructureIds = bindingManager.getConfiguredStructureIds(dimensionId);
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
            if (!processedStructureStarts.add(structureScanKey(structureKey, start))) {
                continue;
            }
            result = result.combine(spawnForStructure(
                    player,
                    structureKey.toString(),
                    start,
                    bindingManager.getStructureSpawnBindings(structureKey.toString())
            ));
        }
        return result;
    }

    private static String structureScanKey(ResourceLocation structureKey, StructureStart start) {
        BoundingBox box = start.getBoundingBox();
        return structureKey + "|"
                + start.getChunkPos().toLong() + "|"
                + box.minX() + "," + box.minY() + "," + box.minZ() + ":"
                + box.maxX() + "," + box.maxY() + "," + box.maxZ();
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
            if (!BriefingConditionService.all(player, null, target.rule().conditions())) {
                allSpawned = false;
                continue;
            }
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
        if (savedData.hasSpawned(instanceId) && VERIFIED_SPAWN_INSTANCES.contains(instanceId)) {
            return true;
        }

        Optional<LivingEntity> existingInstance = findExistingInstance(level, start, rule, instanceId);
        if (existingInstance.isPresent()) {
            LivingEntity entity = existingInstance.get();
            if (NpcSpawnPlacementService.needsSurfaceMigration(entity, rule)) {
                BlockPos spawnPos = NpcSpawnPlacementService.resolveSpawnPosition(level, start, rule, instanceId);
                if (spawnPos == null) {
                    return false;
                }
                NpcSpawnPlacementService.migrateOutdatedSurfacePlacement(level, entity, spawnPos, binding, rule);
            }
            savedData.markSpawned(instanceId);
            VERIFIED_SPAWN_INSTANCES.add(instanceId);
            return true;
        }

        if (savedData.hasSpawned(instanceId)) {
            VERIFIED_SPAWN_INSTANCES.add(instanceId);
            return true;
        }

        List<BlockPos> spawnCandidates = NpcSpawnPlacementService.resolveSpawnCandidates(level, start, rule, instanceId);
        if (spawnCandidates.isEmpty()) {
            logNoSpawnCandidates(level, instanceId, structureId, binding, rule, start);
            return false;
        }

        NpcSpawnService.SpawnResult lastCollision = null;
        BlockPos lastCollisionPos = null;
        int attemptedCandidates = 0;
        for (BlockPos spawnPos : spawnCandidates) {
            ChunkPos spawnChunk = new ChunkPos(spawnPos);
            if (!level.getChunkSource().hasChunk(spawnChunk.x, spawnChunk.z)) {
                continue;
            }

            attemptedCandidates++;
            NpcSpawnService.SpawnResult result = spawnEntity(level, spawnPos, binding, rule, instanceId);
            if (result.succeeded()) {
                savedData.markSpawned(instanceId);
                VERIFIED_SPAWN_INSTANCES.add(instanceId);
                LAST_SPAWN_FAILURE_LOG.remove(instanceId);
                PhantasmBriefing.LOGGER.info(
                        "[PhantasmBriefing] Spawned NPC binding '{}' for structure '{}' at {} after {} candidate(s)",
                        binding.bindingId(),
                        structureId,
                        spawnPos,
                        attemptedCandidates
                );
                return true;
            }

            if (result.failureReason() == NpcSpawnService.FailureReason.COLLISION) {
                lastCollision = result;
                lastCollisionPos = spawnPos;
                continue;
            }

            logSpawnFailure(level, instanceId, binding, spawnPos, result, attemptedCandidates);
            return false;
        }

        if (lastCollision != null && lastCollisionPos != null) {
            logSpawnFailure(level, instanceId, binding, lastCollisionPos, lastCollision, attemptedCandidates);
        }
        return false;
    }

    private static void logNoSpawnCandidates(
            ServerLevel level,
            String instanceId,
            String structureId,
            NpcBindingSpec binding,
            NpcStructureSpawnSpec rule,
            StructureStart start
    ) {
        long now = level.getGameTime();
        Long previous = LAST_SPAWN_FAILURE_LOG.put(instanceId, now);
        if (previous != null && now - previous < SPAWN_FAILURE_LOG_COOLDOWN_TICKS) {
            return;
        }
        BoundingBox box = start.getBoundingBox();
        PhantasmBriefing.LOGGER.warn(
                "[PhantasmBriefing] No NPC spawn candidate found for binding '{}' in structure '{}' "
                        + "box=[{}, {}, {} -> {}, {}, {}], snapToSurface={}",
                binding.bindingId(),
                structureId,
                box.minX(), box.minY(), box.minZ(),
                box.maxX(), box.maxY(), box.maxZ(),
                rule.snapToSurface()
        );
    }

    private static NpcSpawnService.SpawnResult spawnEntity(
            ServerLevel level,
            BlockPos spawnPos,
            NpcBindingSpec binding,
            NpcStructureSpawnSpec rule,
            String instanceId
    ) {
        return NpcSpawnService.spawn(
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
                    NpcSpawnPlacementService.markPlacementVersion(livingEntity);
                }
        );
    }

    private static void logSpawnFailure(
            ServerLevel level,
            String instanceId,
            NpcBindingSpec binding,
            BlockPos spawnPos,
            NpcSpawnService.SpawnResult result,
            int attemptedCandidates
    ) {
        long now = level.getGameTime();
        Long previous = LAST_SPAWN_FAILURE_LOG.put(instanceId, now);
        if (previous != null && now - previous < SPAWN_FAILURE_LOG_COOLDOWN_TICKS) {
            return;
        }
        PhantasmBriefing.LOGGER.warn(
                "[PhantasmBriefing] Cannot spawn NPC binding '{}' after {} candidate(s), primary {}: {} (reason={})",
                binding.bindingId(),
                attemptedCandidates,
                spawnPos,
                result.error(),
                result.failureReason()
        );
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

    /**
     * Administrator-only escape hatch for removing one looked-at PhantasmalChronicle NPC.
     *
     * <p>This is intentionally not part of normal cleanup. It removes stale managed entities
     * left behind by an earlier saved state. The entity must carry a PhantasmalChronicle identity, structure
     * spawn-instance tag, or direct quest-NPC capability marker. When a stale structure instance
     * is present its saved spawn record is removed at the same time, so deleting the entity does
     * not leave another orphaned record behind.</p>
     */
    public static ManualNpcRemoval removeManagedNpcManually(LivingEntity entity) {
        if (entity == null || !(entity.level() instanceof ServerLevel level)) {
            return ManualNpcRemoval.NOT_MANAGED;
        }

        String instanceId = entity.getPersistentData().getString(TAG_SPAWN_INSTANCE).trim();
        SpawnInstanceParts parts = parseSpawnInstanceId(instanceId);
        String assignedBindingId = QuestNpcHelper.getAssignedNpcIdentity(entity);
        boolean directQuestNpc = QuestNpcHelper.isCapabilityQuestNPC(entity)
                || QuestNpcHelper.hasDirectQuestNpcMarker(entity);
        boolean resolvesAsQuestNpc = QuestNpcHelper.isQuestNPC(entity);

        if (instanceId.isBlank() && assignedBindingId.isBlank() && !directQuestNpc && !resolvesAsQuestNpc) {
            return ManualNpcRemoval.NOT_MANAGED;
        }

        String bindingId = parts != null ? parts.bindingId() : assignedBindingId;
        NpcStructureSpawnSavedData savedData = NpcStructureSpawnSavedData.get(level);
        int removedSpawnRecords = 0;
        int removedCompletionRecords = 0;

        if (!instanceId.isBlank()) {
            if (savedData.removeSpawned(instanceId)) {
                removedSpawnRecords++;
            }
            VERIFIED_SPAWN_INSTANCES.remove(instanceId);
            LAST_SPAWN_FAILURE_LOG.remove(instanceId);
        }

        // Deleted bindings can leave completion records even after the concrete
        // instance record is gone. Clear those only when the binding itself no longer exists,
        // avoiding collateral changes to a still-valid current binding.
        boolean bindingStillRegistered = !bindingId.isBlank()
                && NpcBindingDataManager.getInstance().getBinding(bindingId).isPresent();
        if (!bindingId.isBlank() && !bindingStillRegistered) {
            removedSpawnRecords += savedData.removeSpawnedInstancesForBinding(bindingId);
            removedCompletionRecords += savedData.removeCompletedStructuresForBinding(bindingId);
        }

        entity.discard();
        return new ManualNpcRemoval(
                true,
                bindingId,
                instanceId,
                bindingStillRegistered,
                removedSpawnRecords,
                removedCompletionRecords
        );
    }

    /**
     * Purges managed NPC state that is already orphaned before the current editor reload.
     *
     * <p>The current editor manifest and freshly loaded registry form the authoritative allow-list.
     * Only entities explicitly tagged by PhantasmalChronicle are eligible, so ordinary world mobs
     * are never scanned for rule matches or removed.</p>
     */
    public static RemovedBindingCleanup purgeOrphanedManagedNpcs(
            MinecraftServer server,
            Set<String> editorBindingIds
    ) {
        if (server == null) {
            return RemovedBindingCleanup.EMPTY;
        }

        Set<String> allowedBindingIds = new HashSet<>();
        if (editorBindingIds != null) {
            editorBindingIds.stream()
                    .filter(id -> id != null && !id.isBlank())
                    .map(String::trim)
                    .forEach(allowedBindingIds::add);
        }
        allowedBindingIds.addAll(NpcBindingDataManager.getInstance().getBindingIds());

        Set<String> orphanBindingIds = new LinkedHashSet<>();
        int discardedEntities = 0;
        int removedSpawnRecords = 0;
        int removedCompletionRecords = 0;

        for (ServerLevel level : server.getAllLevels()) {
            NpcStructureSpawnSavedData savedData = NpcStructureSpawnSavedData.get(level);

            for (String instanceId : savedData.getSpawnedInstances()) {
                SpawnInstanceParts parts = parseSpawnInstanceId(instanceId);
                if (parts != null && !allowedBindingIds.contains(parts.bindingId())) {
                    orphanBindingIds.add(parts.bindingId());
                }
            }

            List<LivingEntity> staleLoadedEntities = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof LivingEntity livingEntity)) {
                    continue;
                }
                SpawnInstanceParts parts = parseSpawnInstanceId(
                        livingEntity.getPersistentData().getString(TAG_SPAWN_INSTANCE)
                );
                String assignedBindingId = QuestNpcHelper.getAssignedNpcIdentity(livingEntity);
                String managedBindingId = parts != null ? parts.bindingId() : assignedBindingId;
                if (managedBindingId.isBlank() || allowedBindingIds.contains(managedBindingId)) {
                    continue;
                }
                orphanBindingIds.add(managedBindingId);
                staleLoadedEntities.add(livingEntity);
            }

            for (LivingEntity entity : staleLoadedEntities) {
                String instanceId = entity.getPersistentData().getString(TAG_SPAWN_INSTANCE);
                if (!instanceId.isBlank()) {
                    VERIFIED_SPAWN_INSTANCES.remove(instanceId);
                    LAST_SPAWN_FAILURE_LOG.remove(instanceId);
                }
                entity.discard();
                discardedEntities++;
            }

            for (String bindingId : Set.copyOf(orphanBindingIds)) {
                removedSpawnRecords += savedData.removeSpawnedInstancesForBinding(bindingId);
                removedCompletionRecords += savedData.removeCompletedStructuresForBinding(bindingId);
            }
        }

        VERIFIED_SPAWN_INSTANCES.removeIf(instanceId -> {
            SpawnInstanceParts parts = parseSpawnInstanceId(instanceId);
            return parts != null && !allowedBindingIds.contains(parts.bindingId());
        });
        LAST_SPAWN_FAILURE_LOG.keySet().removeIf(instanceId -> {
            SpawnInstanceParts parts = parseSpawnInstanceId(instanceId);
            return parts != null && !allowedBindingIds.contains(parts.bindingId());
        });

        return new RemovedBindingCleanup(
                discardedEntities,
                removedSpawnRecords,
                removedCompletionRecords
        );
    }

    /**
     * Purges structure-generated NPC instances whose binding IDs were removed by an editor
     * hot reload. This is invoked only after that reload; it adds no periodic world scan.
     */
    public static RemovedBindingCleanup purgeRemovedBindings(
            MinecraftServer server,
            Set<String> removedBindingIds
    ) {
        if (server == null || removedBindingIds == null || removedBindingIds.isEmpty()) {
            return RemovedBindingCleanup.EMPTY;
        }

        Set<String> normalizedBindingIds = removedBindingIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .collect(Collectors.toUnmodifiableSet());
        if (normalizedBindingIds.isEmpty()) {
            return RemovedBindingCleanup.EMPTY;
        }

        int discardedEntities = 0;
        int removedSpawnRecords = 0;
        int removedCompletionRecords = 0;
        for (ServerLevel level : server.getAllLevels()) {
            NpcStructureSpawnSavedData savedData = NpcStructureSpawnSavedData.get(level);
            for (String bindingId : normalizedBindingIds) {
                removedSpawnRecords += savedData.removeSpawnedInstancesForBinding(bindingId);
                removedCompletionRecords += savedData.removeCompletedStructuresForBinding(bindingId);
            }

            List<LivingEntity> staleLoadedEntities = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof LivingEntity livingEntity)) {
                    continue;
                }
                SpawnInstanceParts parts = parseSpawnInstanceId(
                        livingEntity.getPersistentData().getString(TAG_SPAWN_INSTANCE)
                );
                String assignedBindingId = QuestNpcHelper.getAssignedNpcIdentity(livingEntity);
                if ((parts != null && normalizedBindingIds.contains(parts.bindingId()))
                        || (!assignedBindingId.isBlank() && normalizedBindingIds.contains(assignedBindingId))) {
                    staleLoadedEntities.add(livingEntity);
                }
            }
            for (LivingEntity entity : staleLoadedEntities) {
                String instanceId = entity.getPersistentData().getString(TAG_SPAWN_INSTANCE);
                VERIFIED_SPAWN_INSTANCES.remove(instanceId);
                LAST_SPAWN_FAILURE_LOG.remove(instanceId);
                entity.discard();
                discardedEntities++;
            }
        }

        VERIFIED_SPAWN_INSTANCES.removeIf(instanceId -> {
            SpawnInstanceParts parts = parseSpawnInstanceId(instanceId);
            return parts != null && normalizedBindingIds.contains(parts.bindingId());
        });
        LAST_SPAWN_FAILURE_LOG.keySet().removeIf(instanceId -> {
            SpawnInstanceParts parts = parseSpawnInstanceId(instanceId);
            return parts != null && normalizedBindingIds.contains(parts.bindingId());
        });

        return new RemovedBindingCleanup(
                discardedEntities,
                removedSpawnRecords,
                removedCompletionRecords
        );
    }

    /**
     * Rejects an old structure-generated NPC when a previously unloaded chunk is loaded after
     * its binding was deleted. This is called from the already-existing entity-join handler.
     */
    public static boolean discardIfStaleGeneratedNpc(LivingEntity entity) {
        if (entity == null || NpcBindingDataManager.getInstance().getReloadRevision() <= 0L) {
            return false;
        }
        String instanceId = entity.getPersistentData().getString(TAG_SPAWN_INSTANCE);
        SpawnInstanceParts parts = parseSpawnInstanceId(instanceId);
        String assignedBindingId = QuestNpcHelper.getAssignedNpcIdentity(entity);
        String bindingId = parts != null ? parts.bindingId() : assignedBindingId;
        if (bindingId.isBlank() || NpcBindingDataManager.getInstance().getBinding(bindingId).isPresent()) {
            return false;
        }
        if (!instanceId.isBlank()) {
            VERIFIED_SPAWN_INSTANCES.remove(instanceId);
            LAST_SPAWN_FAILURE_LOG.remove(instanceId);
        }
        entity.discard();
        return true;
    }

    public static int reapplyNearbyGeneratedNpcBindings(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        int refreshed = 0;
        for (LivingEntity entity : nearbyGeneratedNpcs(player)) {
            SpawnInstanceParts parts = parseSpawnInstanceId(entity.getPersistentData().getString(TAG_SPAWN_INSTANCE));
            if (parts == null) {
                continue;
            }
            NpcBindingSpec binding = NpcBindingDataManager.getInstance().getBinding(parts.bindingId()).orElse(null);
            if (binding == null) {
                continue;
            }
            NpcStructureSpawnSpec rule = binding.spawnRules().stream()
                    .filter(candidate -> candidate.ruleId().equals(parts.ruleId()))
                    .findFirst()
                    .orElse(null);
            if (rule == null) {
                continue;
            }
            if (NpcSpawnService.reapplyConfiguration(
                    entity,
                    binding,
                    rule.noAi(),
                    rule.nameVisible(),
                    rule.villagerLevel()
            ).isBlank()) {
                refreshed++;
            }
        }
        return refreshed;
    }

    public static int refreshNearbyGeneratedNpcs(ServerPlayer player, @Nullable String bindingId) {
        if (player == null) {
            return 0;
        }
        ServerLevel level = player.serverLevel();
        int refreshed = 0;
        for (LivingEntity entity : nearbyGeneratedNpcs(player)) {
            SpawnInstanceParts parts = parseSpawnInstanceId(entity.getPersistentData().getString(TAG_SPAWN_INSTANCE));
            if (parts == null || !matchesBindingFilter(parts.bindingId(), bindingId)) {
                continue;
            }
            NpcBindingSpec binding = NpcBindingDataManager.getInstance().getBinding(parts.bindingId()).orElse(null);
            if (binding == null) {
                continue;
            }
            NpcStructureSpawnSpec rule = binding.spawnRules().stream()
                    .filter(candidate -> candidate.ruleId().equals(parts.ruleId()))
                    .findFirst()
                    .orElse(null);
            if (rule == null) {
                continue;
            }
            String error = NpcSpawnService.reapplyConfiguration(
                    entity,
                    binding,
                    rule.noAi(),
                    rule.nameVisible(),
                    rule.villagerLevel()
            );
            if (error.isBlank()) {
                refreshed++;
            } else {
                PhantasmBriefing.LOGGER.warn(
                        "[PhantasmBriefing] Cannot refresh NPC binding '{}' for instance '{}': {}",
                        parts.bindingId(),
                        entity.getPersistentData().getString(TAG_SPAWN_INSTANCE),
                        error
                );
            }
        }

        BriefingPlayerData.clearCompletedNpcStructures(player);
        NpcStructureSpawnEvents.invalidatePlayer(player);
        int recreated = reconcileMissingSavedInstances(player, bindingId);
        processLoadedArea(player, player.chunkPosition(), ADMIN_SCAN_CHUNK_RADIUS);
        return refreshed + recreated;
    }

    public static int respawnNearbyGeneratedNpcs(ServerPlayer player, @Nullable String bindingId) {
        if (player == null) {
            return 0;
        }
        ServerLevel level = player.serverLevel();
        NpcStructureSpawnSavedData savedData = NpcStructureSpawnSavedData.get(level);
        Set<String> instanceIds = new LinkedHashSet<>();
        List<LivingEntity> nearbyEntities = List.copyOf(nearbyGeneratedNpcs(player));
        for (LivingEntity entity : nearbyEntities) {
            String instanceId = entity.getPersistentData().getString(TAG_SPAWN_INSTANCE);
            SpawnInstanceParts parts = parseSpawnInstanceId(instanceId);
            if (parts != null && matchesBindingFilter(parts.bindingId(), bindingId)) {
                instanceIds.add(instanceId);
            }
        }
        for (String instanceId : savedData.getSpawnedInstances()) {
            SpawnInstanceParts parts = parseSpawnInstanceId(instanceId);
            if (parts != null
                    && matchesBindingFilter(parts.bindingId(), bindingId)
                    && isStructureInstanceNearPlayer(player, parts)) {
                instanceIds.add(instanceId);
            }
        }

        Set<String> affectedBindings = new LinkedHashSet<>();
        for (String instanceId : instanceIds) {
            SpawnInstanceParts parts = parseSpawnInstanceId(instanceId);
            if (parts != null) {
                affectedBindings.add(parts.bindingId());
            }
        }
        if (bindingId != null && !bindingId.isBlank()) {
            affectedBindings.add(bindingId.trim());
        }
        for (String affectedBinding : affectedBindings) {
            savedData.removeCompletedStructuresForBinding(affectedBinding);
        }

        BriefingPlayerData.clearCompletedNpcStructures(player);
        NpcStructureSpawnEvents.invalidatePlayer(player);
        int respawned = 0;
        for (String instanceId : instanceIds) {
            for (LivingEntity entity : nearbyEntities) {
                if (instanceId.equals(entity.getPersistentData().getString(TAG_SPAWN_INSTANCE))) {
                    entity.discard();
                }
            }
            if (respawnExactInstance(player, instanceId)) {
                respawned++;
            }
        }
        processLoadedArea(player, player.chunkPosition(), ADMIN_SCAN_CHUNK_RADIUS);
        return respawned;
    }

    private static int reconcileMissingSavedInstances(ServerPlayer player, @Nullable String bindingId) {
        ServerLevel level = player.serverLevel();
        NpcStructureSpawnSavedData savedData = NpcStructureSpawnSavedData.get(level);
        int recreated = 0;
        for (String instanceId : List.copyOf(savedData.getSpawnedInstances())) {
            SpawnInstanceParts parts = parseSpawnInstanceId(instanceId);
            if (parts == null
                    || !matchesBindingFilter(parts.bindingId(), bindingId)
                    || !isStructureInstanceNearPlayer(player, parts)) {
                continue;
            }
            NpcBindingSpec binding = NpcBindingDataManager.getInstance().getBinding(parts.bindingId()).orElse(null);
            NpcStructureSpawnSpec rule = binding == null ? null : binding.spawnRules().stream()
                    .filter(candidate -> candidate.ruleId().equals(parts.ruleId()))
                    .findFirst()
                    .orElse(null);
            StructureStart start = rule == null ? null : findStructureStart(level, parts);
            if (binding == null || rule == null || start == null) {
                continue;
            }
            if (findExistingInstance(level, start, rule, instanceId).isPresent()) {
                VERIFIED_SPAWN_INSTANCES.add(instanceId);
                continue;
            }
            savedData.removeSpawned(instanceId);
            savedData.removeCompletedStructuresForBinding(parts.bindingId());
            VERIFIED_SPAWN_INSTANCES.remove(instanceId);
            if (BriefingConditionService.all(player, null, rule.conditions())
                    && ensureSpawned(level, parts.structureId(), start, binding, rule, savedData)) {
                recreated++;
            }
        }
        return recreated;
    }

    private static boolean respawnExactInstance(ServerPlayer player, String instanceId) {
        SpawnInstanceParts parts = parseSpawnInstanceId(instanceId);
        if (parts == null) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        NpcStructureSpawnSavedData savedData = NpcStructureSpawnSavedData.get(level);
        savedData.removeSpawned(instanceId);
        VERIFIED_SPAWN_INSTANCES.remove(instanceId);
        NpcBindingSpec binding = NpcBindingDataManager.getInstance().getBinding(parts.bindingId()).orElse(null);
        if (binding == null) {
            return false;
        }
        NpcStructureSpawnSpec rule = binding.spawnRules().stream()
                .filter(candidate -> candidate.ruleId().equals(parts.ruleId()))
                .findFirst()
                .orElse(null);
        if (rule == null || !rule.matches(parts.structureId(), level.dimension().location().toString())) {
            return false;
        }
        StructureStart start = findStructureStart(level, parts);
        if (start == null || !BriefingConditionService.all(player, null, rule.conditions())) {
            return false;
        }
        return ensureSpawned(level, parts.structureId(), start, binding, rule, savedData);
    }

    @Nullable
    private static StructureStart findStructureStart(ServerLevel level, SpawnInstanceParts parts) {
        ResourceLocation structureKey = ResourceLocation.tryParse(parts.structureId());
        if (structureKey == null) {
            return null;
        }
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Structure structure = registry.get(structureKey);
        if (structure == null) {
            return null;
        }
        ChunkPos startChunk = new ChunkPos(parts.startChunkX(), parts.startChunkZ());
        LevelChunk chunk = level.getChunk(parts.startChunkX(), parts.startChunkZ());
        StructureStart direct = chunk.getAllStarts().get(structure);
        if (isMatchingStructureStart(direct, startChunk)) {
            return direct;
        }
        for (StructureStart candidate : level.structureManager().startsForStructure(startChunk, value -> value == structure)) {
            if (isMatchingStructureStart(candidate, startChunk)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isMatchingStructureStart(@Nullable StructureStart start, ChunkPos expectedChunk) {
        return start != null
                && start.isValid()
                && start.getChunkPos().x == expectedChunk.x
                && start.getChunkPos().z == expectedChunk.z;
    }

    private static boolean isStructureInstanceNearPlayer(ServerPlayer player, SpawnInstanceParts parts) {
        double x = (parts.startChunkX() << 4) + 8.0D;
        double z = (parts.startChunkZ() << 4) + 8.0D;
        double dx = x - player.getX();
        double dz = z - player.getZ();
        return dx * dx + dz * dz <= ADMIN_STRUCTURE_SEARCH_RADIUS * ADMIN_STRUCTURE_SEARCH_RADIUS;
    }

    private static List<LivingEntity> nearbyGeneratedNpcs(ServerPlayer player) {
        AABB searchBox = player.getBoundingBox().inflate(
                ADMIN_NPC_SEARCH_RADIUS,
                Math.max(32.0D, ADMIN_NPC_SEARCH_RADIUS / 2.0D),
                ADMIN_NPC_SEARCH_RADIUS
        );
        return player.serverLevel().getEntitiesOfClass(
                LivingEntity.class,
                searchBox,
                entity -> !entity.getPersistentData().getString(TAG_SPAWN_INSTANCE).isBlank()
        );
    }

    private static boolean matchesBindingFilter(String actualBindingId, @Nullable String requestedBindingId) {
        return requestedBindingId == null
                || requestedBindingId.isBlank()
                || actualBindingId.equals(requestedBindingId.trim());
    }

    @Nullable
    private static SpawnInstanceParts parseSpawnInstanceId(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            return null;
        }
        String[] parts = instanceId.split("\\|", -1);
        if (parts.length < 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            return null;
        }
        int at = parts[0].lastIndexOf('@');
        int comma = parts[0].lastIndexOf(',');
        if (at <= 0 || comma <= at + 1 || comma >= parts[0].length() - 1) {
            return null;
        }
        try {
            String structureId = parts[0].substring(0, at);
            int startChunkX = Integer.parseInt(parts[0].substring(at + 1, comma));
            int startChunkZ = Integer.parseInt(parts[0].substring(comma + 1));
            return new SpawnInstanceParts(structureId, startChunkX, startChunkZ, parts[1], parts[2]);
        } catch (NumberFormatException ignored) {
            return null;
        }
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

    public record ManualNpcRemoval(
            boolean removed,
            String bindingId,
            String instanceId,
            boolean bindingStillRegistered,
            int removedSpawnRecords,
            int removedCompletionRecords
    ) {
        private static final ManualNpcRemoval NOT_MANAGED = new ManualNpcRemoval(
                false, "", "", false, 0, 0
        );
    }

    public record RemovedBindingCleanup(
            int discardedEntities,
            int removedSpawnRecords,
            int removedCompletionRecords
    ) {
        private static final RemovedBindingCleanup EMPTY = new RemovedBindingCleanup(0, 0, 0);
    }

    private record StructureSpawnTarget(NpcBindingSpec binding, NpcStructureSpawnSpec rule) {
    }

    private record SpawnInstanceParts(String structureId, int startChunkX, int startChunkZ, String bindingId, String ruleId) {
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
