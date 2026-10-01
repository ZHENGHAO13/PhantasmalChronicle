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

import static com.phantasm.briefing.data.JsonDataUtil.readActions;
import static com.phantasm.briefing.data.JsonDataUtil.readText;
import static com.phantasm.briefing.data.JsonDataUtil.readTextArray;
import static com.phantasm.briefing.data.JsonDataUtil.sanitize;

/** Current quest schema. Old quest shapes are rewritten by ContentMigrationService before loading. */
public record QuestSpec(
        String questId,
        String title,
        String description,
        List<String> objectiveLines,
        String ftbQuestId,
        List<ManualReferenceSpec> manualRefs,
        boolean repeatable,
        boolean autoStart,
        String initialPhaseId,
        QuestPhaseMode phaseMode,
        List<QuestPhaseSpec> phases,
        List<JsonObject> unlockConditions,
        List<String> parentQuestIds,
        List<String> nextQuestIds,
        List<DialogueAction> acceptActions,
        List<DialogueAction> completeActions,
        String trackerTitle,
        QuestMarkerSpec marker
) {

    public static QuestSpec fromJson(@Nonnull String sourceDescription, @Nonnull JsonObject json) {
        String questId = sanitize(GsonHelper.getAsString(json, "questId", ""));
        String title = readText(json, "title");
        String description = readText(json, "description");
        String ftbQuestId = sanitize(GsonHelper.getAsString(json, "ftbQuestId", ""));
        boolean repeatable = GsonHelper.getAsBoolean(json, "repeatable", false);
        boolean autoStart = GsonHelper.getAsBoolean(json, "autoStart", false);
        String initialPhaseId = sanitize(GsonHelper.getAsString(json, "initialPhaseId", ""));
        QuestPhaseMode phaseMode = QuestPhaseMode.fromSerializedName(GsonHelper.getAsString(json, "phaseMode", "sequential"));
        String trackerTitle = readText(json, "trackerTitle");

        if (questId.isBlank()) throw new IllegalArgumentException("Quest id is blank: " + sourceDescription);
        if (title.isBlank()) throw new IllegalArgumentException("Quest title is blank: " + sourceDescription);

        List<QuestPhaseSpec> phases = new ArrayList<>();
        if (json.has("phases") && json.get("phases").isJsonArray()) {
            JsonArray phasesArray = json.getAsJsonArray("phases");
            for (int i = 0; i < phasesArray.size(); i++) {
                JsonElement element = phasesArray.get(i);
                if (element.isJsonObject()) {
                    phases.add(QuestPhaseSpec.fromJson(sourceDescription + "#phases[" + i + "]", element.getAsJsonObject()));
                }
            }
        }
        if (phases.isEmpty()) throw new IllegalArgumentException("Quest has no phases: " + sourceDescription);
        if (initialPhaseId.isBlank()) initialPhaseId = phases.get(0).phaseId();

        List<JsonObject> unlockConditions = readObjects(json, "unlockConditions");
        List<String> parentQuestIds = readStrings(json, "parentQuestIds");
        List<String> nextQuestIds = readStrings(json, "nextQuestIds");
        QuestMarkerSpec marker = json.has("marker") && json.get("marker").isJsonObject()
                ? QuestMarkerSpec.fromJson(sourceDescription + "#marker", json.getAsJsonObject("marker")) : null;

        return new QuestSpec(
                questId,
                title,
                description,
                List.copyOf(readTextArray(json, "objectiveLines")),
                ftbQuestId,
                ManualReferenceSpec.read(json),
                repeatable,
                autoStart,
                initialPhaseId,
                phaseMode,
                List.copyOf(phases),
                List.copyOf(unlockConditions),
                List.copyOf(parentQuestIds),
                List.copyOf(nextQuestIds),
                List.copyOf(readActions(sourceDescription, json, "acceptActions")),
                List.copyOf(readActions(sourceDescription, json, "completeActions")),
                trackerTitle,
                marker
        );
    }

    @Nonnull
    public List<String> allParentQuestIds() {
        LinkedHashSet<String> parents = new LinkedHashSet<>();
        for (String parentQuestId : parentQuestIds) {
            if (parentQuestId != null && !parentQuestId.isBlank()) parents.add(parentQuestId.trim());
        }
        return List.copyOf(parents);
    }

    @Nullable
    public QuestPhaseSpec phase(String phaseId) {
        if (phaseId == null || phaseId.isBlank()) return null;
        for (QuestPhaseSpec phase : phases) if (phase.phaseId().equals(phaseId.trim())) return phase;
        return null;
    }

    private static List<String> readStrings(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonArray()) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray(key)) {
            if (!element.isJsonPrimitive()) continue;
            String value = sanitize(element.getAsString());
            if (!value.isBlank() && !result.contains(value)) result.add(value);
        }
        return result;
    }

    private static List<JsonObject> readObjects(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonArray()) return List.of();
        List<JsonObject> result = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray(key)) if (element.isJsonObject()) result.add(element.getAsJsonObject().deepCopy());
        return result;
    }
}
