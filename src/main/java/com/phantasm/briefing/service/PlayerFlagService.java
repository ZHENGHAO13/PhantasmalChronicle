package com.phantasm.briefing.service;

import com.phantasm.briefing.event.NpcStructureSpawnEvents;
import net.minecraft.server.level.ServerPlayer;

public final class PlayerFlagService {
    private PlayerFlagService() {
    }

    public static boolean hasFlag(ServerPlayer player, String flag) {
        return BriefingPlayerData.hasFlag(player, flag == null ? "" : flag.trim());
    }

    public static void setFlag(ServerPlayer player, String flag) {
        BriefingPlayerData.setFlag(player, flag == null ? "" : flag.trim());
        QuestRuntimeService.invalidateAutomaticObjectives(player);
        NpcStructureSpawnEvents.invalidatePlayer(player);
    }
}
