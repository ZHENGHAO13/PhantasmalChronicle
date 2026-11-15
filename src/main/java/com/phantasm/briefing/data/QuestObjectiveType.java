package com.phantasm.briefing.data;

import java.util.Locale;

public enum QuestObjectiveType {
    MANUAL,
    KILL,
    COLLECT,
    INTERACT,
    VISIT;

    public static QuestObjectiveType parse(String value) {
        String normalized = value == null ? "" : value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return MANUAL;
        }
        return switch (normalized) {
            case "KILL" -> KILL;
            case "COLLECT", "HAS_ITEM", "HAS_ITEMS" -> COLLECT;
            case "INTERACT", "TALK_TO", "TALK" -> INTERACT;
            case "VISIT", "LOCATION", "GO_TO", "REACH" -> VISIT;
            default -> MANUAL;
        };
    }
}
