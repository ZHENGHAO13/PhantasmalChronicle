package com.phantasm.briefing.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.phantasm.briefing.data.JsonDataUtil.parseHintType;
import static com.phantasm.briefing.data.JsonDataUtil.readActions;
import static com.phantasm.briefing.data.JsonDataUtil.readStringArray;
import static com.phantasm.briefing.data.JsonDataUtil.readText;
import static com.phantasm.briefing.data.JsonDataUtil.readTextArray;
import static com.phantasm.briefing.data.JsonDataUtil.sanitize;

public record QuestPhaseSpec(
        String phaseId,
        String title,
        List<String> objectiveLines,
        List<String> targetNodeIds,
        String nextPhaseId,
        boolean completeQuestOnObjectivesCompleted,
        List<DialogueAction> actions,
        List<DialogueAction> completionActions,
        List<QuestObjectiveSpec> objectives,
        QuestHintType hintType,
        QuestObjectiveMode objectiveMode
) {
    public static QuestPhaseSpec fromJson(@Nonnull String sourceDescription, @Nonnull JsonObject json) {
        String phaseId = sanitize(GsonHelper.getAsString(json, "phaseId", ""));
        String title = readText(json, "title");
        if (phaseId.isBlank()) throw new IllegalArgumentException("Quest phase id is blank: " + sourceDescription);
        List<QuestObjectiveSpec> objectives = new ArrayList<>();
        if (json.has("objectives") && json.get("objectives").isJsonArray()) {
            JsonArray array = json.getAsJsonArray("objectives");
            for (int i = 0; i < array.size(); i++) {
                JsonElement element = array.get(i);
                if (element.isJsonObject()) objectives.add(QuestObjectiveSpec.fromJson(sourceDescription + "#objectives[" + i + "]", element.getAsJsonObject()));
            }
        }
        return new QuestPhaseSpec(
                phaseId,
                title,
                List.copyOf(readTextArray(json, "objectiveLines")),
                List.copyOf(readStringArray(json, "targetNodeIds")),
                sanitize(GsonHelper.getAsString(json, "nextPhaseId", "")),
                GsonHelper.getAsBoolean(json, "completeQuestOnObjectivesCompleted", false),
                List.copyOf(readActions(sourceDescription, json, "actions")),
                List.copyOf(readActions(sourceDescription, json, "completionActions")),
                List.copyOf(objectives),
                parseHintType(GsonHelper.getAsString(json, "hintType", "advance")),
                QuestObjectiveMode.fromSerializedName(GsonHelper.getAsString(json, "objectiveMode", "sequential"))
        );
    }

    public boolean targetsNode(String nodeId) {
        return nodeId != null && !nodeId.isBlank() && targetNodeIds.stream().anyMatch(nodeId::equals);
    }

    @Nullable
    public QuestObjectiveSpec objective(String objectiveId) {
        if (objectiveId == null || objectiveId.isBlank()) return null;
        for (QuestObjectiveSpec objective : objectives) if (objective.objectiveId().equals(objectiveId.trim())) return objective;
        return null;
    }
}
