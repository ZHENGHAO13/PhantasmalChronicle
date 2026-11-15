package com.phantasm.briefing.service;

import com.google.gson.JsonObject;
import com.phantasm.briefing.PhantasmBriefing;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

import java.util.ArrayList;
import java.util.List;

public final class BriefingConditionService {
    public static final String CURRENT_NAMESPACE = "phantasmbriefing:";
    public static final String LEGACY_NAMESPACE = "chronicle_engine:";

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
                    PlayerFlagService.hasFlag(player, firstNonBlank(
                            readString(condition, "flag", ""),
                            readString(condition, "flagName", "")
                    ));
            case CURRENT_NAMESPACE + "quest_accepted" ->
                    QuestRuntimeService.isQuestActive(player, readString(condition, "questId", ""));
            case CURRENT_NAMESPACE + "quest_available" ->
                    QuestRuntimeService.isQuestAvailable(player, readString(condition, "questId", ""));
            case CURRENT_NAMESPACE + "quest_ready_to_turn_in" ->
                    QuestRuntimeService.isQuestReadyToTurnIn(player, readString(condition, "questId", ""));
            case CURRENT_NAMESPACE + "quest_completed" ->
                    QuestRuntimeService.isQuestCompleted(player, readString(condition, "questId", ""));
            case CURRENT_NAMESPACE + "quest_not_started" -> {
                String questId = readString(condition, "questId", "");
                yield !QuestRuntimeService.isQuestActive(player, questId)
                        && !QuestRuntimeService.isQuestCompleted(player, questId);
            }
            case CURRENT_NAMESPACE + "quest_phase" -> {
                String questId = readString(condition, "questId", "");
                String phaseId = readString(condition, "phaseId", "");
                yield QuestRuntimeService.isQuestActive(player, questId)
                        && phaseId.equals(QuestRuntimeService.phase(player, questId));
            }
            case CURRENT_NAMESPACE + "quest_objective_completed" -> {
                String questId = readString(condition, "questId", "");
                String objectiveId = readString(condition, "objectiveId", "");
                yield QuestRuntimeService.isObjectiveCompleted(player, questId, objectiveId);
            }
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

    public static String normalizeConditionType(String type) {
        if (type == null || type.isBlank()) {
            return CURRENT_NAMESPACE + "always";
        }
        if (type.startsWith(LEGACY_NAMESPACE)) {
            return CURRENT_NAMESPACE + type.substring(LEGACY_NAMESPACE.length());
        }
        return type;
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

    private static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }
}
