package com.phantasm.briefing.service;

import com.phantasm.briefing.data.DialogueDataManager;
import com.phantasm.briefing.data.DialogueNode;
import com.phantasm.briefing.data.DialogueOption;
import com.phantasm.briefing.data.QuestDataManager;
import com.phantasm.briefing.data.QuestObjectiveSpec;
import com.phantasm.briefing.data.QuestObjectiveType;
import com.phantasm.briefing.data.QuestPhaseSpec;
import com.phantasm.briefing.data.QuestSpec;
import com.phantasm.briefing.integration.FTBIntegrationHelper;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** FTB completion cache and FTB-backed quest objective handling. */
final class QuestFtbObjectiveService {
    private QuestFtbObjectiveService() {
    }

    static void refreshCompletionCache(ServerPlayer player) {
        if (player == null || !FTBIntegrationHelper.isFTBQuestsLoaded()) return;
        Set<String> questIds = new LinkedHashSet<>();
        for (QuestSpec quest : QuestDataManager.getInstance().getAllQuests()) {
            BriefingConditionService.collectFtbQuestIds(quest.unlockConditions(), questIds);
            for (String prerequisiteId : quest.allParentQuestIds()) {
                if (QuestDataManager.getInstance().getQuest(prerequisiteId).isEmpty()) {
                    questIds.add(prerequisiteId);
                }
            }
            for (QuestPhaseSpec phase : quest.phases()) {
                for (QuestObjectiveSpec objective : phase.objectives()) {
                    if (objective.objectiveType() == QuestObjectiveType.FTB_QUEST && !objective.targetId().isBlank()) {
                        questIds.add(objective.targetId());
                    }
                    BriefingConditionService.collectFtbQuestIds(objective.conditions(), questIds);
                }
            }
        }
        for (DialogueNode node : DialogueDataManager.getInstance().getAllNodes()) {
            BriefingConditionService.collectFtbQuestIds(node.conditions(), questIds);
            for (DialogueOption option : node.options()) {
                BriefingConditionService.collectFtbQuestIds(option.conditions(), questIds);
            }
        }
        FTBIntegrationHelper.refreshCompletionCache(player, questIds);
    }

    static void evaluateOnce(ServerPlayer player, String excludedFtbQuestId) {
        if (player == null || !FTBIntegrationHelper.isFTBQuestsLoaded()) return;
        boolean changed;
        int safety = 16;
        do {
            changed = false;
            outer:
            for (String questId : List.copyOf(BriefingPlayerData.activeQuestIds(player))) {
                QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
                if (quest == null) continue;
                for (QuestObjectiveSpec objective : QuestRuntimeService.activeObjectives(player, quest)) {
                    if (objective.objectiveType() != QuestObjectiveType.FTB_QUEST
                            || objective.targetId().isBlank()
                            || FTBIntegrationHelper.sameQuestId(objective.targetId(), excludedFtbQuestId)) {
                        continue;
                    }
                    if (FTBIntegrationHelper.hasCompletedQuest(player, objective.targetId())
                            && QuestRuntimeService.completeObjectiveFromFtb(
                            player, quest.questId(), objective.objectiveId(), excludedFtbQuestId)) {
                        changed = true;
                        break outer;
                    }
                }
            }
        } while (changed && --safety > 0);
    }

    static void onQuestCompleted(ServerPlayer player, String ftbQuestId) {
        if (player == null || ftbQuestId == null || ftbQuestId.isBlank()) return;
        List<ObjectiveCompletion> matchingObjectives = new ArrayList<>();
        for (String questId : List.copyOf(BriefingPlayerData.activeQuestIds(player))) {
            QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
            if (quest == null) continue;
            for (QuestObjectiveSpec objective : QuestRuntimeService.activeObjectives(player, quest)) {
                if (objective.objectiveType() == QuestObjectiveType.FTB_QUEST
                        && !objective.targetId().isBlank()
                        && FTBIntegrationHelper.sameQuestId(objective.targetId(), ftbQuestId)) {
                    matchingObjectives.add(new ObjectiveCompletion(quest.questId(), objective.objectiveId()));
                }
            }
        }
        for (ObjectiveCompletion completion : matchingObjectives) {
            QuestRuntimeService.completeObjectiveFromFtb(
                    player, completion.questId(), completion.objectiveId(), ftbQuestId);
        }
        QuestRuntimeService.ensureAutoStartedQuests(player);
    }

    private record ObjectiveCompletion(String questId, String objectiveId) {
    }
}
