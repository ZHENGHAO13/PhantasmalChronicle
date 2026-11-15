package com.phantasm.briefing.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import javax.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

public record DialogueNode(
        String nodeId,
        String title,
        List<String> lines,
        boolean triggerVFX,
        String ftbQuestIdToComplete,
        String nextNodeId,
        String requiredQuestId,
        DialoguePresentationMode presentationMode,
        List<DialogueOption> options,
        List<JsonObject> conditions
) {

    public static DialogueNode fromJson(@Nonnull ResourceLocation sourceId, @Nonnull JsonObject json) {
        return fromJson(sourceId.toString(), json);
    }

    public static DialogueNode fromJson(@Nonnull String sourceDescription, @Nonnull JsonObject json) {
        String nodeId = sanitize(GsonHelper.getAsString(json, "nodeId"));
        if (nodeId.isBlank()) {
            throw new IllegalArgumentException("Dialogue node id is blank: " + sourceDescription);
        }

        List<String> lines = new ArrayList<>();
        GsonHelper.getAsJsonArray(json, "lines").forEach(element -> lines.add(element.getAsString()));
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Dialogue node lines are empty: " + sourceDescription);
        }

        String title = sanitize(GsonHelper.getAsString(json, "title", "行动简报"));
        boolean triggerVfx = GsonHelper.getAsBoolean(json, "triggerVFX", false);
        String ftbQuestIdToComplete = sanitize(GsonHelper.getAsString(json, "ftbQuestIdToComplete", ""));
        String nextNodeId = sanitize(GsonHelper.getAsString(json, "nextNodeId", ""));
        String requiredQuestId = sanitize(GsonHelper.getAsString(json, "requiredQuestId", ""));
        DialoguePresentationMode presentationMode = DialoguePresentationMode.fromSerializedName(
                GsonHelper.getAsString(json, "presentation", DialoguePresentationMode.CINEMATIC.name())
        );
        List<DialogueOption> options = new ArrayList<>();
        if (json.has("options")) {
            GsonHelper.getAsJsonArray(json, "options").forEach(element -> options.add(DialogueOption.fromJson(sourceDescription, element.getAsJsonObject())));
        }
        List<JsonObject> conditions = new ArrayList<>();
        if (json.has("conditions")) {
            for (JsonElement element : GsonHelper.getAsJsonArray(json, "conditions")) {
                if (element.isJsonObject()) {
                    conditions.add(element.getAsJsonObject().deepCopy());
                }
            }
        }

        return new DialogueNode(
                nodeId,
                title.isBlank() ? "行动简报" : title,
                List.copyOf(lines),
                triggerVfx,
                ftbQuestIdToComplete,
                nextNodeId,
                requiredQuestId,
                presentationMode,
                List.copyOf(options),
                List.copyOf(conditions)
        );
    }

    @Nonnull
    private static String sanitize(String value) {
        return value == null ? "" : value.trim();
    }
}
