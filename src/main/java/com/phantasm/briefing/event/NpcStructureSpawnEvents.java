package com.phantasm.briefing.event;

import com.phantasm.briefing.data.NpcBindingDataManager;
import com.phantasm.briefing.service.BriefingPlayerData;
import com.phantasm.briefing.service.NpcStructureSpawnService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NpcStructureSpawnEvents {
    private static final int FALLBACK_SCAN_INTERVAL = 100;
    private static final int MAX_SCAN_ATTEMPTS = 4;

    private static final Map<UUID, PlayerScanState> PLAYER_SCAN_STATES = new ConcurrentHashMap<>();

    public static void invalidatePlayer(ServerPlayer player) {
        if (player != null) {
            PLAYER_SCAN_STATES.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        NpcBindingDataManager manager = NpcBindingDataManager.getInstance();
        if (manager.getStructureSpawnBindings().isEmpty()) {
            return;
        }

        ChunkPos chunkPos = player.chunkPosition();
        String dimension = player.level().dimension().location().toString();
        long revision = manager.getReloadRevision();
        long gameTime = player.serverLevel().getGameTime();
        PlayerScanState previous = PLAYER_SCAN_STATES.get(player.getUUID());
        boolean locationChanged = previous == null
                || previous.chunkPosition() != chunkPos.toLong()
                || previous.reloadRevision() != revision
                || !previous.dimension().equals(dimension);
        if (!locationChanged) {
            if (previous.completed() || previous.scanAttempts() >= MAX_SCAN_ATTEMPTS) {
                return;
            }
            if (gameTime - previous.lastScanGameTime() < FALLBACK_SCAN_INTERVAL) {
                return;
            }
        }

        if (locationChanged && BriefingPlayerData.hasCompletedNpcStructureAt(
                player,
                dimension,
                chunkPos.x,
                chunkPos.z,
                manager.getStructureSpawnFingerprint()
        )) {
            PLAYER_SCAN_STATES.put(
                    player.getUUID(),
                    new PlayerScanState(dimension, chunkPos.toLong(), revision, gameTime, 0, true)
            );
            return;
        }

        int scanAttempts = locationChanged ? 1 : previous.scanAttempts() + 1;
        NpcStructureSpawnService.ScanResult result = NpcStructureSpawnService.processLoadedArea(
                player,
                chunkPos
        );
        PLAYER_SCAN_STATES.put(
                player.getUUID(),
                new PlayerScanState(
                        dimension,
                        chunkPos.toLong(),
                        revision,
                        gameTime,
                        scanAttempts,
                        result == NpcStructureSpawnService.ScanResult.COMPLETE
                )
        );
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PLAYER_SCAN_STATES.remove(event.getEntity().getUUID());
    }

    private record PlayerScanState(
            String dimension,
            long chunkPosition,
            long reloadRevision,
            long lastScanGameTime,
            int scanAttempts,
            boolean completed
    ) {
    }
}
