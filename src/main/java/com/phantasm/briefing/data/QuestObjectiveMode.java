package com.phantasm.briefing.data;

import org.jetbrains.annotations.Nullable;
import java.util.Locale;

/** Controls whether objectives in a phase are activated one-by-one or together. */
public enum QuestObjectiveMode {
    SEQUENTIAL,
    FREE;

    public static QuestObjectiveMode fromSerializedName(@Nullable String serializedName) {
        String normalized = serializedName == null ? "" : serializedName.trim().toLowerCase(Locale.ROOT);
        return "free".equals(normalized) ? FREE : SEQUENTIAL;
    }

    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
