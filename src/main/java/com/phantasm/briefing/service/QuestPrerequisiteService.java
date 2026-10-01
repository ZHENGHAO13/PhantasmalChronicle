package com.phantasm.briefing.service;

import com.google.gson.JsonObject;
import com.phantasm.briefing.data.QuestDataManager;
import com.phantasm.briefing.data.QuestSpec;
import com.phantasm.briefing.integration.FTBIntegrationHelper;

import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashSet;
import java.util.List;

/** Central prerequisite evaluation for quest acceptance. */
final class QuestPrerequisiteService {
    private QuestPrerequisiteService() {
    }


    /**
     * Returns PB quest IDs that structurally gate this quest, including phase-completion prerequisites.
     * FTB IDs deliberately stay out of the PB quest tree.
     */
    static List<String> parentPbQuestIds(QuestSpec quest) {
        if (quest == null) {
            return List.of();
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String prerequisiteId : quest.allParentQuestIds()) {
            if (QuestDataManager.getInstance().getQuest(prerequisiteId).isPresent()) {
                ids.add(prerequisiteId);
            }
        }
        collectPhasePrerequisiteQuestIds(quest.unlockConditions(), ids);
        return List.copyOf(ids);
    }

    private static void collectPhasePrerequisiteQuestIds(List<JsonObject> conditions, LinkedHashSet<String> destination) {
        if (conditions == null || conditions.isEmpty()) {
            return;
        }
        for (JsonObject condition : conditions) {
            if (condition == null || condition.entrySet().isEmpty()) {
                continue;
            }
            String type = BriefingConditionService.normalizeConditionType(readString(condition, "condition"));
            if (type.equals(BriefingConditionService.CURRENT_NAMESPACE + "pb_quest_phase_completed")) {
                String questId = readString(condition, "questId").trim();
                if (!questId.isBlank() && QuestDataManager.getInstance().getQuest(questId).isPresent()) {
                    destination.add(questId);
                }
                continue;
            }
            if ((type.equals(BriefingConditionService.CURRENT_NAMESPACE + "and")
                    || type.equals(BriefingConditionService.CURRENT_NAMESPACE + "or"))
                    && condition.has("conditions") && condition.get("conditions").isJsonArray()) {
                java.util.ArrayList<JsonObject> children = new java.util.ArrayList<>();
                condition.getAsJsonArray("conditions").forEach(element -> {
                    if (element.isJsonObject()) children.add(element.getAsJsonObject());
                });
                collectPhasePrerequisiteQuestIds(children, destination);
                continue;
            }
            if (type.equals(BriefingConditionService.CURRENT_NAMESPACE + "not")
                    && condition.has("inner") && condition.get("inner").isJsonObject()) {
                // NOT is not a structural parent dependency: completing the referenced phase would make it false.
                continue;
            }
        }
    }

    private static String readString(JsonObject owner, String key) {
        return owner.has(key) && owner.get(key).isJsonPrimitive() ? owner.get(key).getAsString() : "";
    }

    static boolean areSatisfied(ServerPlayer player, QuestSpec quest) {
        if (player == null || quest == null) {
            return false;
        }
        for (String prerequisiteId : quest.allParentQuestIds()) {
            if (QuestDataManager.getInstance().getQuest(prerequisiteId).isPresent()) {
                if (!BriefingPlayerData.isQuestCompleted(player, prerequisiteId)) {
                    return false;
                }
            } else if (!FTBIntegrationHelper.isQuestCompletedCached(player, prerequisiteId)) {
                // Current editor stores PB and FTB prerequisite IDs in the same ordered list.
                return false;
            }
        }
        return BriefingConditionService.all(player, null, quest.unlockConditions());
    }
}
