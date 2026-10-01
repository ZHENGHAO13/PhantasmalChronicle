package com.phantasm.briefing.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;

import static com.phantasm.briefing.data.JsonDataUtil.parseHintType;
import static com.phantasm.briefing.data.JsonDataUtil.readActions;
import static com.phantasm.briefing.data.JsonDataUtil.readStringArray;
import static com.phantasm.briefing.data.JsonDataUtil.readText;
import static com.phantasm.briefing.data.JsonDataUtil.readTextArray;
import static com.phantasm.briefing.data.JsonDataUtil.sanitize;

public record QuestObjectiveSpec(
        String objectiveId,
        String title,
        List<String> objectiveLines,
        List<String> targetNodeIds,
        String hintNpcId,
        QuestObjectiveType objectiveType,
        String targetId,
        String killScope,
        int requiredCount,
        QuestMarkerSpec marker,
        String structureId,
        String structureLabel,
        String structureNodeId,
        double radius,
        List<ManualReferenceSpec> manualRefs,
        boolean autoOpenManual,
        List<JsonObject> conditions,
        List<DialogueAction> completionActions,
        QuestHintType hintType
) {
    public static QuestObjectiveSpec fromJson(@Nonnull String sourceDescription, @Nonnull JsonObject json) {
        String objectiveId = sanitize(GsonHelper.getAsString(json, "objectiveId", ""));
        String title = readText(json, "title");
        if (objectiveId.isBlank()) {
            throw new IllegalArgumentException("Quest objective id is blank: " + sourceDescription);
        }

        List<String> objectiveLines = readTextArray(json, "objectiveLines");
        List<String> targetNodeIds = readStringArray(json, "targetNodeIds");
        String hintNpcId = sanitize(GsonHelper.getAsString(json, "hintNpcId", ""));
        QuestObjectiveType objectiveType = QuestObjectiveType.parse(GsonHelper.getAsString(json, "type", ""));
        String targetId = sanitize(GsonHelper.getAsString(json, "targetId", ""));
        String killScope = sanitize(GsonHelper.getAsString(json, "killScope", "specific")).toLowerCase(Locale.ROOT);
        if (objectiveType == QuestObjectiveType.KILL && !com.phantasm.briefing.service.KillTargetRule.isValidScope(killScope)) {
            throw new IllegalArgumentException("Unknown kill scope for " + sourceDescription + ": " + killScope);
        }
        int requiredCount = Math.max(1, GsonHelper.getAsInt(json, "requiredCount", 1));
        QuestMarkerSpec marker = json.has("marker") && json.get("marker").isJsonObject()
                ? QuestMarkerSpec.fromJson(sourceDescription + "#marker", GsonHelper.getAsJsonObject(json, "marker"))
                : null;
        String structureId = sanitize(GsonHelper.getAsString(json, "structureId", ""));
        String structureLabel = sanitize(GsonHelper.getAsString(json, "structureLabel", ""));
        String structureNodeId = sanitize(GsonHelper.getAsString(json, "structureNodeId", ""));
        double radius = Math.max(1.0D, GsonHelper.getAsDouble(json, "radius", 4.0D));
        List<ManualReferenceSpec> manualRefs = ManualReferenceSpec.read(json);
        boolean autoOpenManual = objectiveType == QuestObjectiveType.READ_MANUAL
                && GsonHelper.getAsBoolean(json, "autoOpenManual", false);
        if (objectiveType == QuestObjectiveType.READ_MANUAL && manualRefs.isEmpty()) {
            throw new IllegalArgumentException("Read-manual objective has no manual reference: " + sourceDescription);
        }
        List<JsonObject> conditions = readConditions(json);
        List<DialogueAction> completionActions = readActions(sourceDescription, json, "actions");
        QuestHintType hintType = parseHintType(GsonHelper.getAsString(json, "hintType", "advance"));
        return new QuestObjectiveSpec(
                objectiveId,
                title,
                List.copyOf(objectiveLines),
                List.copyOf(targetNodeIds),
                hintNpcId,
                objectiveType,
                targetId,
                killScope,
                requiredCount,
                marker,
                structureId,
                structureLabel,
                structureNodeId,
                radius,
                manualRefs,
                autoOpenManual,
                List.copyOf(conditions),
                List.copyOf(completionActions),
                hintType
        );
    }

    public boolean targetsNode(String nodeId) {
        return nodeId != null && !nodeId.isBlank() && targetNodeIds.stream().anyMatch(nodeId::equals);
    }

    public boolean targetsNpc(String npcIdentity) {
        if (npcIdentity == null || npcIdentity.isBlank()) return false;
        String normalized = npcIdentity.trim();
        return normalized.equals(hintNpcId)
                || (objectiveType == QuestObjectiveType.INTERACT && normalized.equals(targetId));
    }

    public boolean isEventDriven() {
        return objectiveType != QuestObjectiveType.MANUAL && objectiveType != QuestObjectiveType.READ_MANUAL;
    }

    public boolean hasMarker() { return marker != null; }

    public boolean usesStructureSearch() { return !structureId.isBlank(); }

    public String resolvedStructureLabel() {
        if (!structureLabel.isBlank()) return structureLabel;
        if (!title.isBlank()) return title;
        return structureId;
    }

    @Nonnull
    private static List<JsonObject> readConditions(@Nonnull JsonObject json) {
        if (!json.has("conditions") || !json.get("conditions").isJsonArray()) return List.of();
        List<JsonObject> result = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "conditions")) {
            if (element.isJsonObject()) result.add(element.getAsJsonObject().deepCopy());
        }
        return result;
    }
}
