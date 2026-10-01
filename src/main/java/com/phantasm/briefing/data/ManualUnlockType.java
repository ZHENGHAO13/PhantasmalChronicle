package com.phantasm.briefing.data;

import java.util.Locale;

public enum ManualUnlockType {
    ALWAYS,
    PHASE_COMPLETED,
    OBJECTIVE_COMPLETED,
    OBJECTIVE_ACTIVE;

    public static ManualUnlockType fromSerializedName(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PHASE_COMPLETED" -> PHASE_COMPLETED;
            case "OBJECTIVE_COMPLETED" -> OBJECTIVE_COMPLETED;
            case "OBJECTIVE_ACTIVE" -> OBJECTIVE_ACTIVE;
            default -> ALWAYS;
        };
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
