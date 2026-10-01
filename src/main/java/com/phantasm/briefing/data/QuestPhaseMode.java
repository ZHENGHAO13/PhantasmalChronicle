package com.phantasm.briefing.data;

import java.util.Locale;

/** Controls whether quest phases progress one-by-one or remain active together. */
public enum QuestPhaseMode {
    SEQUENTIAL,
    FREE;

    public static QuestPhaseMode fromSerializedName(String serializedName) {
        String normalized = serializedName == null ? "" : serializedName.trim().toLowerCase(Locale.ROOT);
        return "free".equals(normalized) ? FREE : SEQUENTIAL;
    }

    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
