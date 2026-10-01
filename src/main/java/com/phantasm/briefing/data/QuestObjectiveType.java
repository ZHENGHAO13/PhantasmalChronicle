package com.phantasm.briefing.data;

import java.util.Locale;

public enum QuestObjectiveType {
    MANUAL,
    KILL,
    COLLECT,
    CRAFT,
    INTERACT,
    VISIT,
    FTB_QUEST,
    READ_MANUAL;

    public static QuestObjectiveType parse(String value) {
        String normalized = value == null ? "" : value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return MANUAL;
        }
        return switch (normalized) {
            case "KILL" -> KILL;
            case "COLLECT" -> COLLECT;
            case "CRAFT" -> CRAFT;
            case "INTERACT" -> INTERACT;
            case "VISIT" -> VISIT;
            case "FTB_QUEST" -> FTB_QUEST;
            case "READ_MANUAL" -> READ_MANUAL;
            default -> MANUAL;
        };
    }
}
