package com.phantasm.briefing.service;

import com.phantasm.briefing.data.QuestDataManager;
import com.phantasm.briefing.data.QuestObjectiveSpec;
import com.phantasm.briefing.data.QuestObjectiveType;
import com.phantasm.briefing.data.QuestPhaseMode;
import com.phantasm.briefing.data.QuestPhaseSpec;
import com.phantasm.briefing.data.QuestSpec;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Event-driven objective progress (kill, craft and interaction). */
final class QuestObjectiveEventService {
    private QuestObjectiveEventService() {
    }

    static void onEntityKilled(ServerPlayer player, String entityTypeId, KillTargetRule.Disposition disposition) {
        if (disposition == KillTargetRule.Disposition.EXCLUDED) return;
        progress(player, QuestObjectiveType.KILL, 1, objective ->
                KillTargetRule.matches(objective.killScope(), objective.targetId(), entityTypeId, disposition));
    }

    static void onItemCrafted(ServerPlayer player, String itemId, int craftedCount) {
        progress(player, QuestObjectiveType.CRAFT, Math.max(1, craftedCount),
                objective -> objective.targetId().equals(itemId));
    }

    static void onEntityInteracted(ServerPlayer player, String entityTypeId, String questNodeId, String npcIdentity) {
        progress(player, QuestObjectiveType.INTERACT, 1, objective ->
                objective.targetId().equals(entityTypeId)
                        || (!questNodeId.isBlank() && objective.targetId().equals(questNodeId))
                        || (!npcIdentity.isBlank() && objective.targetId().equals(npcIdentity)));
    }

    private static void progress(ServerPlayer player, QuestObjectiveType objectiveType, int amount,
                                 ObjectiveMatcher matcher) {
        for (String questId : List.copyOf(BriefingPlayerData.activeQuestIds(player))) {
            QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
            if (quest == null) continue;
            for (QuestObjectiveSpec objective : List.copyOf(QuestRuntimeService.activeObjectives(player, quest))) {
                if (objective.objectiveType() != objectiveType
                        || (objectiveType != QuestObjectiveType.KILL && objective.targetId().isBlank())
                        || !matcher.matches(objective)) {
                    continue;
                }
                QuestPhaseSpec phase = QuestRuntimeService.activePhaseForObjective(player, quest, objective.objectiveId())
                        .orElse(null);
                if (phase == null) continue;
                int progress = BriefingPlayerData.incrementObjectiveProgress(
                        player, quest.questId(), phase.phaseId(), objective.objectiveId(), Math.max(1, amount));
                if (progress >= objective.requiredCount()) {
                    QuestRuntimeService.completeObjective(player, quest.questId(), objective.objectiveId());
                    if (quest.phaseMode() != QuestPhaseMode.FREE
                            && !phase.phaseId().equals(BriefingPlayerData.phase(player, quest.questId()))) {
                        break;
                    }
                } else {
                    QuestTrackerService.syncToClient(player);
                    QuestEntityHintService.requestSync(player);
                }
            }
        }
    }

    @FunctionalInterface
    private interface ObjectiveMatcher {
        boolean matches(QuestObjectiveSpec objective);
    }
}
