package com.phantasm.briefing.data;

import java.util.Locale;

public enum DialoguePresentationMode {
    BRIEFING,
    CINEMATIC;

    public static DialoguePresentationMode fromSerializedName(String value) {
        if (value == null || value.isBlank()) {
            return CINEMATIC;
        }

        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "BRIEFING" -> BRIEFING;
            case "CINEMATIC" -> CINEMATIC;
            default -> CINEMATIC;
        };
    }
}
