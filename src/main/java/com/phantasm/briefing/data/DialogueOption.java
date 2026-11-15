package com.phantasm.briefing.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import net.minecraft.util.GsonHelper;
import javax.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

public record DialogueOption(
        String optionId,
        String label,
        String requiredQuestId,
        List<DialogueAction> actions,
        List<JsonObject> conditions
) {

    public static DialogueOption fromJson(@Nonnull String sourceDescription, @Nonnull JsonObject json) {
        String optionId = sanitize(GsonHelper.getAsString(json, "optionId", ""));
        String label = sanitize(GsonHelper.getAsString(json, "label", ""));
        String requiredQuestId = sanitize(GsonHelper.getAsString(json, "requiredQuestId", ""));

        if (optionId.isBlank()) {
            throw new IllegalArgumentException("Dialogue option id is blank: " + sourceDescription);
        }
        if (label.isBlank()) {
            throw new IllegalArgumentException("Dialogue option label is blank: " + sourceDescription);
        }

        List<DialogueAction> actions = new ArrayList<>();
        JsonArray actionArray = GsonHelper.getAsJsonArray(json, "actions");
        actionArray.forEach(element -> actions.add(DialogueAction.fromJson(sourceDescription + "#" + optionId, element.getAsJsonObject())));
        if (actions.isEmpty()) {
            throw new IllegalArgumentException("Dialogue option actions are empty: " + sourceDescription);
        }

        List<JsonObject> conditions = new ArrayList<>();
        if (json.has("conditions")) {
            for (JsonElement element : GsonHelper.getAsJsonArray(json, "conditions")) {
                if (element.isJsonObject()) {
                    conditions.add(element.getAsJsonObject().deepCopy());
                }
            }
        }

        return new DialogueOption(optionId, label, requiredQuestId, List.copyOf(actions), List.copyOf(conditions));
    }

    @Nonnull
    private static String sanitize(String value) {
        return value == null ? "" : value.trim();
    }
}
