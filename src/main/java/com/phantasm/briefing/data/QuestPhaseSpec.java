package com.phantasm.briefing.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public record QuestPhaseSpec(
        String phaseId,
        String title,
        List<String> objectiveLines,
        List<String> targetNodeIds,
        String nextPhaseId,
        boolean completeQuestOnObjectivesCompleted,
        List<DialogueAction> actions,
        List<QuestObjectiveSpec> objectives,
        QuestHintType hintType
) {
    public static QuestPhaseSpec fromJson(@Nonnull String sourceDescription, @Nonnull JsonObject json) {
        String phaseId = sanitize(GsonHelper.getAsString(json, "phaseId", ""));
        String title = sanitize(GsonHelper.getAsString(json, "title", ""));
        if (phaseId.isBlank()) {
            throw new IllegalArgumentException("Quest phase id is blank: " + sourceDescription);
        }

        List<String> objectiveLines = readStringArray(json, "objectiveLines");
        List<String> targetNodeIds = readStringArray(json, "targetNodeIds");
        String nextPhaseId = sanitize(GsonHelper.getAsString(json, "nextPhaseId", ""));
        boolean completeQuestOnObjectivesCompleted = GsonHelper.getAsBoolean(json, "completeQuestOnObjectivesCompleted", false);
        List<DialogueAction> actions = readActions(sourceDescription, json);
        List<QuestObjectiveSpec> objectives = readObjectives(sourceDescription, json);
        QuestHintType hintType = parseHintType(GsonHelper.getAsString(json, "hintType", "advance"));
        return new QuestPhaseSpec(
                phaseId,
                title,
                List.copyOf(objectiveLines),
                List.copyOf(targetNodeIds),
                nextPhaseId,
                completeQuestOnObjectivesCompleted,
                List.copyOf(actions),
                List.copyOf(objectives),
                hintType
        );
    }

    public boolean targetsNode(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return false;
        }
        return targetNodeIds.stream().anyMatch(nodeId::equals);
    }

    @Nullable
    public QuestObjectiveSpec objective(String objectiveId) {
        if (objectiveId == null || objectiveId.isBlank()) {
            return null;
        }
        for (QuestObjectiveSpec objective : objectives) {
            if (objective.objectiveId().equals(objectiveId.trim())) {
                return objective;
            }
        }
        return null;
    }

    @Nonnull
    private static List<String> readStringArray(@Nonnull JsonObject json, @Nonnull String key) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            return List.of();
        }

        JsonArray array = GsonHelper.getAsJsonArray(json, key);
        List<String> result = new ArrayList<>();
        array.forEach(element -> result.add(sanitize(element.getAsString())));
        return result;
    }

    @Nonnull
    private static List<QuestObjectiveSpec> readObjectives(@Nonnull String sourceDescription, @Nonnull JsonObject json) {
        if (!json.has("objectives") || !json.get("objectives").isJsonArray()) {
            return List.of();
        }

        JsonArray array = GsonHelper.getAsJsonArray(json, "objectives");
        List<QuestObjectiveSpec> result = new ArrayList<>();
        int index = 0;
        for (var element : array) {
            if (element.isJsonObject()) {
                result.add(QuestObjectiveSpec.fromJson(
                        sourceDescription + "#objectives[" + index + "]",
                        element.getAsJsonObject()
                ));
            }
            index++;
        }
        return result;
    }

    @Nonnull
    private static List<DialogueAction> readActions(@Nonnull String sourceDescription, @Nonnull JsonObject json) {
        if (!json.has("actions") || !json.get("actions").isJsonArray()) {
            return List.of();
        }

        List<DialogueAction> result = new ArrayList<>();
        int index = 0;
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "actions")) {
            if (element.isJsonObject()) {
                result.add(DialogueAction.fromJson(
                        sourceDescription + "#actions[" + index + "]",
                        element.getAsJsonObject()
                ));
            }
            index++;
        }
        return result;
    }

    @Nonnull
    private static QuestHintType parseHintType(String value) {
        String normalized = sanitize(value).replace('-', '_').toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return QuestHintType.ADVANCE;
        }
        return switch (normalized) {
            case "COMPLETE" -> QuestHintType.COMPLETE;
            case "AVAILABLE" -> QuestHintType.AVAILABLE;
            case "NONE" -> QuestHintType.NONE;
            default -> QuestHintType.ADVANCE;
        };
    }

    @Nonnull
    private static String sanitize(String value) {
        return value == null ? "" : value.trim();
    }
}
