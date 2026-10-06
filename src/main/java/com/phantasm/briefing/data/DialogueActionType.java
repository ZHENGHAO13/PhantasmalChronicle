package com.phantasm.briefing.data;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public enum DialogueActionType {
    COMPLETE_QUEST,
    COMPLETE_OBJECTIVE,
    START_QUEST,
    SET_QUEST_PHASE,
    OPEN_NODE,
    OPEN_SHOP,
    GIVE_ITEM,
    DELIVER_ITEM,
    SET_FLAG,
    CLOSE_DIALOGUE,
    COMPLETE_FTB_QUEST,
    REMOVE_CONTEXT_NPC;

    public static DialogueActionType fromSerializedName(@Nullable String serializedName) {
        if (serializedName == null || serializedName.isBlank()) {
            throw new IllegalArgumentException("Dialogue action type is blank");
        }

        String normalized = serializedName.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        return DialogueActionType.valueOf(normalized);
    }

    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
