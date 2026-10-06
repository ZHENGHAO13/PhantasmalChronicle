package com.phantasm.briefing.service;

import com.google.gson.JsonObject;
import com.phantasm.briefing.PhantasmBriefing;
import com.phantasm.briefing.integration.FTBIntegrationHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class BriefingConditionService {
    public static final String CURRENT_NAMESPACE = "phantasmbriefing:";
    private static final ThreadLocal<Set<String>> ACTIVE_OBJECTIVE_GUARD = ThreadLocal.withInitial(HashSet::new);

    private BriefingConditionService() {
    }

    public static boolean all(ServerPlayer player, Entity entity, List<JsonObject> conditions) {
        for (JsonObject condition : conditions) {
            if (!test(player, entity, condition)) {
                return false;
            }
        }
        return true;
    }

    public static boolean test(ServerPlayer player, Entity entity, JsonObject condition) {
        if (condition == null || condition.entrySet().isEmpty()) {
            return true;
        }

        String rawType = readString(condition, "condition", CURRENT_NAMESPACE + "always");
        String type = normalizeConditionType(rawType);
        return switch (type) {
            case CURRENT_NAMESPACE + "always" -> true;
            case CURRENT_NAMESPACE + "has_flag" ->
                    PlayerFlagService.hasFlag(player, readString(condition, "flag", ""));
            case CURRENT_NAMESPACE + "pb_quest_accepted" ->
                    QuestRuntimeService.isQuestActive(player, readString(condition, "questId", ""));
            case CURRENT_NAMESPACE + "pb_quest_available" ->
                    QuestRuntimeService.isQuestAvailable(player, readString(condition, "questId", ""));
            case CURRENT_NAMESPACE + "pb_quest_ready_to_turn_in" ->
                    QuestRuntimeService.isQuestReadyToTurnIn(player, readString(condition, "questId", ""));
            case CURRENT_NAMESPACE + "pb_quest_completed" ->
                    BriefingPlayerData.isQuestCompleted(player, readString(condition, "questId", ""));
            case CURRENT_NAMESPACE + "pb_quest_not_started" -> {
                String questId = readString(condition, "questId", "");
                yield !QuestRuntimeService.isQuestActive(player, questId)
                        && !BriefingPlayerData.isQuestCompleted(player, questId);
            }
            case CURRENT_NAMESPACE + "pb_quest_phase" -> {
                String questId = readString(condition, "questId", "");
                String phaseId = readString(condition, "phaseId", "");
                yield QuestRuntimeService.isPhaseActive(player, questId, phaseId);
            }
            case CURRENT_NAMESPACE + "pb_quest_phase_completed" -> {
                String questId = readString(condition, "questId", "");
                String phaseId = readString(condition, "phaseId", "");
                yield BriefingPlayerData.isPhaseCompleted(player, questId, phaseId);
            }
            case CURRENT_NAMESPACE + "pb_quest_objective_active" -> {
                String questId = readString(condition, "questId", "");
                String phaseId = readString(condition, "phaseId", "");
                String objectiveId = readString(condition, "objectiveId", "");
                yield testObjectiveActive(player, questId, phaseId, objectiveId);
            }
            case CURRENT_NAMESPACE + "pb_quest_objective_completed" -> {
                String questId = readString(condition, "questId", "");
                String objectiveId = readString(condition, "objectiveId", "");
                yield QuestRuntimeService.isObjectiveCompleted(player, questId, objectiveId);
            }
            case CURRENT_NAMESPACE + "ftb_quest_completed" ->
                    FTBIntegrationHelper.isQuestCompletedCached(player, readString(condition, "questId", ""));
            case CURRENT_NAMESPACE + "ftb_quest_not_completed" ->
                    !FTBIntegrationHelper.isQuestCompletedCached(player, readString(condition, "questId", ""));
            case CURRENT_NAMESPACE + "and" -> all(player, entity, readObjects(condition, "conditions"));
            case CURRENT_NAMESPACE + "or" -> any(player, entity, readObjects(condition, "conditions"));
            case CURRENT_NAMESPACE + "not" -> !test(player, entity, readObject(condition, "inner"));
            case CURRENT_NAMESPACE + "entity_name" -> entityNameMatches(entity, readString(condition, "namePattern", ""));
            case CURRENT_NAMESPACE + "villager_profession" -> villagerProfessionMatches(entity, readString(condition, "profession", ""));
            default -> {
                PhantasmBriefing.LOGGER.warn("[PhantasmBriefing] Unsupported condition type: {}", rawType);
                yield false;
            }
        };
    }

    public static void collectFtbQuestIds(List<JsonObject> conditions, Set<String> destination) {
        if (conditions == null || conditions.isEmpty() || destination == null) {
            return;
        }
        for (JsonObject condition : conditions) {
            if (condition == null || condition.entrySet().isEmpty()) {
                continue;
            }
            String type = normalizeConditionType(readString(condition, "condition", CURRENT_NAMESPACE + "always"));
            if (type.equals(CURRENT_NAMESPACE + "ftb_quest_completed")
                    || type.equals(CURRENT_NAMESPACE + "ftb_quest_not_completed")) {
                String questId = readString(condition, "questId", "").trim();
                if (!questId.isBlank()) {
                    destination.add(questId);
                }
                continue;
            }
            if (type.equals(CURRENT_NAMESPACE + "and") || type.equals(CURRENT_NAMESPACE + "or")) {
                collectFtbQuestIds(readObjects(condition, "conditions"), destination);
                continue;
            }
            if (type.equals(CURRENT_NAMESPACE + "not")) {
                collectFtbQuestIds(List.of(readObject(condition, "inner")), destination);
            }
        }
    }

    public static String normalizeConditionType(String type) {
        if (type == null || type.isBlank()) {
            return CURRENT_NAMESPACE + "always";
        }
        return type.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_');
    }


    private static boolean testObjectiveActive(ServerPlayer player, String questId, String phaseId, String objectiveId) {
        if (player == null || questId.isBlank() || phaseId.isBlank() || objectiveId.isBlank()) {
            return false;
        }
        String key = player.getUUID() + "|" + questId + "|" + phaseId + "|" + objectiveId;
        Set<String> guard = ACTIVE_OBJECTIVE_GUARD.get();
        if (!guard.add(key)) {
            return false;
        }
        try {
            return QuestRuntimeService.isObjectiveActive(player, questId, phaseId, objectiveId);
        } finally {
            guard.remove(key);
            if (guard.isEmpty()) {
                ACTIVE_OBJECTIVE_GUARD.remove();
            }
        }
    }

    private static boolean any(ServerPlayer player, Entity entity, List<JsonObject> conditions) {
        for (JsonObject condition : conditions) {
            if (test(player, entity, condition)) {
                return true;
            }
        }
        return false;
    }

    private static boolean entityNameMatches(Entity entity, String pattern) {
        if (entity == null || pattern.isBlank()) {
            return false;
        }
        String name = entity.getName().getString();
        return name.equals(pattern) || name.contains(pattern) || name.matches(pattern);
    }

    private static boolean villagerProfessionMatches(Entity entity, String profession) {
        if (!(entity instanceof Villager villager) || profession.isBlank()) {
            return false;
        }
        String id = villager.getVillagerData().getProfession().toString();
        return id.equals(profession) || id.endsWith(":" + profession);
    }

    private static List<JsonObject> readObjects(JsonObject owner, String key) {
        if (!owner.has(key) || !owner.get(key).isJsonArray()) {
            return List.of();
        }

        List<JsonObject> objects = new ArrayList<>();
        owner.getAsJsonArray(key).forEach(element -> {
            if (element.isJsonObject()) {
                objects.add(element.getAsJsonObject().deepCopy());
            }
        });
        return List.copyOf(objects);
    }

    private static JsonObject readObject(JsonObject owner, String key) {
        if (!owner.has(key) || !owner.get(key).isJsonObject()) {
            return new JsonObject();
        }
        return owner.getAsJsonObject(key).deepCopy();
    }

    private static String readString(JsonObject owner, String key, String fallback) {
        if (!owner.has(key) || !owner.get(key).isJsonPrimitive()) {
            return fallback;
        }
        return owner.get(key).getAsString();
    }

}
