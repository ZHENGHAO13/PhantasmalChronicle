package com.phantasm.briefing.data;

import java.util.Locale;

public final class TutorialImagePath {
    private static final int MAX_FILENAME_LENGTH = 128;

    private TutorialImagePath() {
    }

    public static String normalize(String value) {
        String candidate = value == null ? "" : value.trim();
        if (candidate.isBlank()
                || candidate.length() > MAX_FILENAME_LENGTH
                || candidate.startsWith(".")
                || candidate.contains("/")
                || candidate.contains("\\")
                || candidate.contains(":")) {
            return "";
        }

        String lower = candidate.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".png")) {
            return "";
        }

        String stem = candidate.substring(0, candidate.length() - 4);
        if (stem.isBlank() || !stem.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            return "";
        }
        return stem + ".png";
    }
}
