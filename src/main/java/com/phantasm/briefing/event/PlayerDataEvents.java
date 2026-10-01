package com.phantasm.briefing.event;

import com.phantasm.briefing.service.BriefingPlayerData;
import com.phantasm.briefing.service.DialogueFlowCoordinator;
import com.phantasm.briefing.service.DialogueShopService;
import com.phantasm.briefing.integration.FTBIntegrationHelper;
import com.phantasm.briefing.service.QuestRuntimeService;
import com.phantasm.briefing.service.QuestEntityHintService;
import com.phantasm.briefing.service.QuestTrackerService;
import com.phantasm.briefing.service.ManualVisibilityService;
import com.phantasm.briefing.service.PlayerDataMigrationService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class PlayerDataEvents {

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        QuestRuntimeService.clearAllAutomaticObjectiveStates();
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerDataMigrationService.migrate(player);
            QuestRuntimeService.refreshFtbCompletionCache(player);
            QuestRuntimeService.ensureAutoStartedQuests(player);
            QuestTrackerService.normalizeTracking(player);
            QuestRuntimeService.evaluateAutomaticObjectives(player);
            QuestRuntimeService.evaluateFtbObjectivesOnce(player);
            QuestRuntimeService.reconcileActiveQuestFlow(player);
            ManualVisibilityService.refreshUnlocks(player);
            QuestTrackerService.syncToClient(player);
        }
    }
    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        BriefingPlayerData.copyOnClone(event.getOriginal(), event.getEntity());
        if (event.getEntity() instanceof ServerPlayer player) {
            DialogueShopService.clear(player);
            DialogueFlowCoordinator.closePlayerSession(player);
            QuestRuntimeService.clearAutomaticObjectiveState(player);
            QuestEntityHintService.clear(player);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DialogueShopService.clear(player);
            FTBIntegrationHelper.clearCompletionCache(player);
            QuestRuntimeService.clearAutomaticObjectiveState(player);
            QuestEntityHintService.clear(player);
        }
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DialogueShopService.clear(player);
            DialogueFlowCoordinator.closePlayerSession(player);
            QuestEntityHintService.clear(player);
            QuestRuntimeService.invalidateAutomaticObjectives(player);
            QuestRuntimeService.evaluateAutomaticObjectives(player);
            QuestEntityHintService.requestSync(player);
        }
    }
}
