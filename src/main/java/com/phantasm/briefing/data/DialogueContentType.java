package com.phantasm.briefing.data;

import java.util.Locale;

public enum DialogueContentType {
    TEXT,
    IMAGE;

    public static DialogueContentType fromSerializedName(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return "IMAGE".equals(normalized) ? IMAGE : TEXT;
    }
}
