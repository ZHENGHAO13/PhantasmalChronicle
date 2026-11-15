package com.phantasm.briefing.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public record QuestSpec(
        String questId,
        String title,
        String description,
        List<String> objectiveLines,
        String ftbQuestId,
        String requiredQuestId,
        boolean repeatable,
        String initialPhaseId,
        List<QuestPhaseSpec> phases,
        List<JsonObject> unlockConditions,
        List<String> parentQuestIds,
        List<String> nextQuestIds,
        List<String> flagsToSetOnAccept,
        List<DialogueAction> acceptActions,
        List<DialogueAction> completeActions,
        String trackerTitle,
        QuestMarkerSpec marker,
        @Nullable QuestTreeLayoutSpec treeLayout
) {

    public static QuestSpec fromJson(@Nonnull String sourceDescription, @Nonnull JsonObject json) {
        String questId = sanitize(GsonHelper.getAsString(json, "questId", ""));
        String title = sanitize(GsonHelper.getAsString(json, "title", ""));
        String description = sanitize(GsonHelper.getAsString(json, "description", ""));
        String ftbQuestId = sanitize(GsonHelper.getAsString(json, "ftbQuestId", ""));
        String requiredQuestId = sanitize(GsonHelper.getAsString(json, "requiredQuestId", ""));
        boolean repeatable = GsonHelper.getAsBoolean(json, "repeatable", false);
        String initialPhaseId = sanitize(GsonHelper.getAsString(json, "initialPhaseId", ""));
        String trackerTitle = sanitize(GsonHelper.getAsString(json, "trackerTitle", ""));

        if (questId.isBlank()) {
            throw new IllegalArgumentException("Quest id is blank: " + sourceDescription);
        }
        if (title.isBlank()) {
            throw new IllegalArgumentException("Quest title is blank: " + sourceDescription);
        }

        List<String> objectiveLines = new ArrayList<>();
        if (json.has("objectiveLines")) {
            JsonArray objectiveArray = GsonHelper.getAsJsonArray(json, "objectiveLines");
            objectiveArray.forEach(element -> objectiveLines.add(sanitize(element.getAsString())));
        }
        List<QuestPhaseSpec> phases = new ArrayList<>();
        if (json.has("phases")) {
            JsonArray phasesArray = GsonHelper.getAsJsonArray(json, "phases");
            int index = 0;
            for (JsonElement element : phasesArray) {
                if (element.isJsonObject()) {
                    phases.add(QuestPhaseSpec.fromJson(sourceDescription + "#phases[" + index + "]", element.getAsJsonObject()));
                }
                index++;
            }
        }
        if (initialPhaseId.isBlank() && !phases.isEmpty()) {
            initialPhaseId = phases.get(0).phaseId();
        }
        List<JsonObject> unlockConditions = new ArrayList<>();
        if (json.has("unlockConditions")) {
            for (JsonElement element : GsonHelper.getAsJsonArray(json, "unlockConditions")) {
                if (element.isJsonObject()) {
                    unlockConditions.add(element.getAsJsonObject().deepCopy());
                }
            }
        }
        List<String> parentQuestIds = new ArrayList<>();
        if (json.has("parentQuestIds")) {
            JsonArray parentQuestArray = GsonHelper.getAsJsonArray(json, "parentQuestIds");
            parentQuestArray.forEach(element -> parentQuestIds.add(sanitize(element.getAsString())));
        }
        List<String> nextQuestIds = new ArrayList<>();
        if (json.has("nextQuestIds")) {
            JsonArray nextQuestArray = GsonHelper.getAsJsonArray(json, "nextQuestIds");
            nextQuestArray.forEach(element -> nextQuestIds.add(sanitize(element.getAsString())));
        }
        List<String> flagsToSetOnAccept = new ArrayList<>();
        if (json.has("flagsToSetOnAccept")) {
            JsonArray flagArray = GsonHelper.getAsJsonArray(json, "flagsToSetOnAccept");
            flagArray.forEach(element -> flagsToSetOnAccept.add(sanitize(element.getAsString())));
        }
        List<DialogueAction> acceptActions = readActions(sourceDescription, json, "acceptActions");
        List<DialogueAction> completeActions = readActions(sourceDescription, json, "completeActions");
        QuestMarkerSpec marker = json.has("marker")
                ? QuestMarkerSpec.fromJson(sourceDescription + "#marker", GsonHelper.getAsJsonObject(json, "marker"))
                : null;
        QuestTreeLayoutSpec treeLayout = json.has("treeLayout")
                ? QuestTreeLayoutSpec.fromJson(sourceDescription + "#treeLayout", GsonHelper.getAsJsonObject(json, "treeLayout"))
                : null;

        return new QuestSpec(
                questId,
                title,
                description,
                List.copyOf(objectiveLines),
                ftbQuestId,
                requiredQuestId,
                repeatable,
                initialPhaseId,
                List.copyOf(phases),
                List.copyOf(unlockConditions),
                List.copyOf(parentQuestIds),
                List.copyOf(nextQuestIds),
                List.copyOf(flagsToSetOnAccept),
                List.copyOf(acceptActions),
                List.copyOf(completeActions),
                trackerTitle,
                marker,
                treeLayout
        );
    }

    @Nonnull
    public List<String> allParentQuestIds() {
        LinkedHashSet<String> parents = new LinkedHashSet<>();
        if (!requiredQuestId.isBlank()) {
            parents.add(requiredQuestId);
        }
        for (String parentQuestId : parentQuestIds) {
            if (parentQuestId != null && !parentQuestId.isBlank()) {
                parents.add(parentQuestId.trim());
            }
        }
        return List.copyOf(parents);
    }

    @Nonnull
    public String resolvedTreeId() {
        if (treeLayout != null && !treeLayout.treeId().isBlank()) {
            return treeLayout.treeId();
        }
        return "default";
    }

    @Nullable
    public QuestPhaseSpec phase(String phaseId) {
        if (phaseId == null || phaseId.isBlank()) {
            return null;
        }
        for (QuestPhaseSpec phase : phases) {
            if (phase.phaseId().equals(phaseId.trim())) {
                return phase;
            }
        }
        return null;
    }

    @Nonnull
    private static List<DialogueAction> readActions(@Nonnull String sourceDescription, @Nonnull JsonObject json, @Nonnull String key) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            return List.of();
        }

        List<DialogueAction> result = new ArrayList<>();
        int index = 0;
        for (JsonElement element : GsonHelper.getAsJsonArray(json, key)) {
            if (element.isJsonObject()) {
                result.add(DialogueAction.fromJson(
                        sourceDescription + "#" + key + "[" + index + "]",
                        element.getAsJsonObject()
                ));
            }
            index++;
        }
        return result;
    }

    @Nonnull
    private static String sanitize(String value) {
        return value == null ? "" : value.trim();
    }
}
