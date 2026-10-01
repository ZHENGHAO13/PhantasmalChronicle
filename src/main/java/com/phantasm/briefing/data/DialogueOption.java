package com.phantasm.briefing.data;

import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import javax.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

import static com.phantasm.briefing.data.JsonDataUtil.readActions;
import static com.phantasm.briefing.data.JsonDataUtil.readObjectArray;
import static com.phantasm.briefing.data.JsonDataUtil.readText;
import static com.phantasm.briefing.data.JsonDataUtil.sanitize;

/** A current-format player choice. Visibility is expressed only through conditions. */
public record DialogueOption(
        String optionId,
        String label,
        List<DialogueAction> actions,
        List<JsonObject> conditions
) {

    public static DialogueOption fromJson(@Nonnull String sourceDescription, @Nonnull JsonObject json) {
        String optionId = sanitize(GsonHelper.getAsString(json, "optionId", ""));
        String label = readText(json, "label");
        if (optionId.isBlank()) {
            throw new IllegalArgumentException("Dialogue option id is blank: " + sourceDescription);
        }
        if (label.isBlank()) {
            throw new IllegalArgumentException("Dialogue option label is blank: " + sourceDescription);
        }

        List<DialogueAction> actions = new ArrayList<>(readActions(
                sourceDescription + "#" + optionId, json, "actions"
        ));
        // nextNodeId remains part of the current editor model because it is a concise navigation field.
        String nextNodeId = sanitize(GsonHelper.getAsString(json, "nextNodeId", ""));
        if (!nextNodeId.isBlank()) {
            actions.add(new DialogueAction(DialogueActionType.OPEN_NODE, nextNodeId));
        }
        if (actions.isEmpty()) {
            actions.add(new DialogueAction(DialogueActionType.CLOSE_DIALOGUE, ""));
        }

        return new DialogueOption(
                optionId,
                label,
                List.copyOf(actions),
                readObjectArray(json, "conditions")
        );
    }
}
