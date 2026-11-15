package com.phantasm.briefing.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;

public record QuestObjectiveSpec(
        String objectiveId,
        String title,
        List<String> objectiveLines,
        List<String> targetNodeIds,
        QuestObjectiveType objectiveType,
        String targetId,
        int requiredCount,
        QuestMarkerSpec marker,
        String structureId,
        String structureLabel,
        String structureNodeId,
        double radius,
        List<JsonObject> conditions,
        List<DialogueAction> completionActions,
        QuestHintType hintType
) {
    public static QuestObjectiveSpec fromJson(@Nonnull String sourceDescription, @Nonnull JsonObject json) {
        String objectiveId = sanitize(GsonHelper.getAsString(json, "objectiveId", ""));
        String title = sanitize(GsonHelper.getAsString(json, "title", ""));
        if (objectiveId.isBlank()) {
            throw new IllegalArgumentException("Quest objective id is blank: " + sourceDescription);
        }

        List<String> objectiveLines = readStringArray(json, "objectiveLines");
        List<String> targetNodeIds = readStringArray(json, "targetNodeIds");
        QuestObjectiveType objectiveType = QuestObjectiveType.parse(GsonHelper.getAsString(json, "type", ""));
        String targetId = sanitize(GsonHelper.getAsString(json, "targetId", ""));
        int requiredCount = Math.max(1, GsonHelper.getAsInt(json, "requiredCount", 1));
        QuestMarkerSpec marker = json.has("marker") && json.get("marker").isJsonObject()
                ? QuestMarkerSpec.fromJson(sourceDescription + "#marker", GsonHelper.getAsJsonObject(json, "marker"))
                : null;
        String structureId = sanitize(GsonHelper.getAsString(json, "structureId", ""));
        String structureLabel = sanitize(GsonHelper.getAsString(json, "structureLabel", ""));
        String structureNodeId = sanitize(GsonHelper.getAsString(json, "structureNodeId", ""));
        double radius = Math.max(1.0D, GsonHelper.getAsDouble(json, "radius", 4.0D));
        List<JsonObject> conditions = readConditions(json);
        List<DialogueAction> completionActions = readActions(sourceDescription, json);
        QuestHintType hintType = parseHintType(GsonHelper.getAsString(json, "hintType", "advance"));
        return new QuestObjectiveSpec(
                objectiveId,
                title,
                List.copyOf(objectiveLines),
                List.copyOf(targetNodeIds),
                objectiveType,
                targetId,
                requiredCount,
                marker,
                structureId,
                structureLabel,
                structureNodeId,
                radius,
                List.copyOf(conditions),
                List.copyOf(completionActions),
                hintType
        );
    }

    public boolean targetsNode(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return false;
        }
        return targetNodeIds.stream().anyMatch(nodeId::equals);
    }

    public boolean isEventDriven() {
        return objectiveType != QuestObjectiveType.MANUAL;
    }

    public boolean hasMarker() {
        return marker != null;
    }

    public boolean usesStructureSearch() {
        return !structureId.isBlank();
    }

    public String resolvedStructureLabel() {
        if (!structureLabel.isBlank()) {
            return structureLabel;
        }
        if (!title.isBlank()) {
            return title;
        }
        return structureId;
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
    private static List<JsonObject> readConditions(@Nonnull JsonObject json) {
        if (!json.has("conditions") || !json.get("conditions").isJsonArray()) {
            return List.of();
        }

        List<JsonObject> result = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "conditions")) {
            if (element.isJsonObject()) {
                result.add(element.getAsJsonObject().deepCopy());
            }
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
