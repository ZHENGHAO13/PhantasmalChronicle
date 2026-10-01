package com.phantasm.briefing.service;

import com.phantasm.briefing.PhantasmBriefing;
import com.phantasm.briefing.data.NpcBindingSpec;
import com.phantasm.briefing.data.NpcStructureSpawnSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Resolves and maintains physical NPC placement inside generated structures.
 * Lifecycle/deduplication stays in {@link NpcStructureSpawnService}; this module only decides where an instance stands.
 */
final class NpcSpawnPlacementService {
    private static final String TAG_SURFACE_PLACEMENT_VERSION = "PhantasmBriefingNpcSurfacePlacementVersion";
    private static final int SURFACE_PLACEMENT_VERSION = 2;
    private static final int SURFACE_SEARCH_RADIUS = 32;
    private static final int MAX_SURFACE_CANDIDATES = 32;
    private static final int SURFACE_VERTICAL_PROBE = 8;

    private NpcSpawnPlacementService() {
    }

    @Nullable
    static BlockPos resolveSpawnPosition(ServerLevel level, StructureStart start,
                                         NpcStructureSpawnSpec rule, String instanceId) {
        List<BlockPos> candidates = resolveSpawnCandidates(level, start, rule, instanceId);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    static List<BlockPos> resolveSpawnCandidates(ServerLevel level, StructureStart start,
                                                  NpcStructureSpawnSpec rule, String instanceId) {
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
        configuredPosition = applyDeterministicSpread(configuredPosition, rule.spreadRadius(), instanceId);
        if (!rule.snapToSurface()) {
            return List.of(configuredPosition);
        }
        return findSafeSurfacePositions(level, configuredPosition, rule.offsetY());
    }

    static void markPlacementVersion(LivingEntity entity) {
        entity.getPersistentData().putInt(TAG_SURFACE_PLACEMENT_VERSION, SURFACE_PLACEMENT_VERSION);
    }

    static boolean needsSurfaceMigration(LivingEntity entity, NpcStructureSpawnSpec rule) {
        return rule.snapToSurface()
                && entity.getPersistentData().getInt(TAG_SURFACE_PLACEMENT_VERSION) < SURFACE_PLACEMENT_VERSION;
    }

    static void migrateOutdatedSurfacePlacement(
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
        markPlacementVersion(entity);
        PhantasmBriefing.LOGGER.info(
                "[PhantasmBriefing] Migrated outdated NPC binding '{}' to safe surface position {}",
                binding.bindingId(),
                spawnPos
        );
    }

    private static BlockPos applyDeterministicSpread(BlockPos origin, int spreadRadius, String deterministicSeed) {
        if (spreadRadius <= 0) {
            return origin;
        }
        long seed = 0xCBF29CE484222325L;
        String text = deterministicSeed == null ? "" : deterministicSeed;
        for (int i = 0; i < text.length(); i++) {
            seed ^= text.charAt(i);
            seed *= 0x100000001B3L;
        }
        Random random = new Random(seed);
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double distance = Math.max(1.0D, Math.sqrt(random.nextDouble()) * spreadRadius);
        int dx = (int) Math.round(Math.cos(angle) * distance);
        int dz = (int) Math.round(Math.sin(angle) * distance);
        if (dx == 0 && dz == 0) {
            dx = random.nextBoolean() ? 1 : -1;
        }
        return origin.offset(dx, 0, dz);
    }

    private static List<BlockPos> findSafeSurfacePositions(ServerLevel level, BlockPos configuredPosition, int offsetY) {
        List<BlockPos> candidates = new ArrayList<>();
        addSurfaceCandidate(level, configuredPosition, offsetY, 0, 0, candidates);
        for (int radius = 1; radius <= SURFACE_SEARCH_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                addSurfaceCandidate(level, configuredPosition, offsetY, dx, -radius, candidates);
                addSurfaceCandidate(level, configuredPosition, offsetY, dx, radius, candidates);
            }
            for (int dz = -radius + 1; dz < radius; dz++) {
                addSurfaceCandidate(level, configuredPosition, offsetY, -radius, dz, candidates);
                addSurfaceCandidate(level, configuredPosition, offsetY, radius, dz, candidates);
            }
            if (radius >= 8 && radius % 8 == 0 && candidates.size() >= 24) {
                break;
            }
        }

        if (candidates.isEmpty()) {
            // This is an expected transient state when the surrounding terrain is not loaded yet.
            // The caller has bounded retry logic, so do not spam the server log here.
            PhantasmBriefing.LOGGER.debug(
                    "[PhantasmBriefing] No safe loaded surface position found near {}",
                    configuredPosition
            );
            return List.of();
        }

        List<Integer> sortedHeights = candidates.stream()
                .map(BlockPos::getY)
                .sorted()
                .toList();
        int terrainReferenceY = sortedHeights.get(sortedHeights.size() / 4);
        return candidates.stream()
                .sorted(Comparator
                        .comparingLong((BlockPos pos) -> surfaceCandidateScore(level, pos, configuredPosition, terrainReferenceY))
                        .thenComparingInt(BlockPos::getX)
                        .thenComparingInt(BlockPos::getZ))
                .limit(MAX_SURFACE_CANDIDATES)
                .toList();
    }

    private static void addSurfaceCandidate(
            ServerLevel level,
            BlockPos configuredPosition,
            int offsetY,
            int dx,
            int dz,
            List<BlockPos> candidates
    ) {
        int x = configuredPosition.getX() + dx;
        int z = configuredPosition.getZ() + dz;
        LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        if (chunk == null) {
            return;
        }

        // Use the level API with absolute world coordinates. Calling ChunkAccess#getHeight with
        // local/absolute coordinates inconsistently can sample a different column from the one
        // we later validate, which previously produced candidates inside solid structure blocks.
        int heightmapY = level.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                x,
                z
        );

        // Existing editor/configs use offsetY=1 as the neutral/default surface offset. Preserve
        // that contract while probing a few blocks vertically so both heightmap conventions
        // (first-free vs highest-occupied) resolve to a genuinely standable feet position.
        int requestedY = heightmapY + offsetY - 1;
        BlockPos candidate = findNearestSafeStandingPosition(level, x, z, requestedY);
        if (candidate != null) {
            candidates.add(candidate);
        }
    }


    @Nullable
    private static BlockPos findNearestSafeStandingPosition(ServerLevel level, int x, int z, int requestedY) {
        BlockPos exact = new BlockPos(x, requestedY, z);
        if (isSafeStandingPosition(level, exact)) {
            return exact;
        }
        for (int delta = 1; delta <= SURFACE_VERTICAL_PROBE; delta++) {
            BlockPos above = new BlockPos(x, requestedY + delta, z);
            if (isSafeStandingPosition(level, above)) {
                return above;
            }
            BlockPos below = new BlockPos(x, requestedY - delta, z);
            if (isSafeStandingPosition(level, below)) {
                return below;
            }
        }
        return null;
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
}
