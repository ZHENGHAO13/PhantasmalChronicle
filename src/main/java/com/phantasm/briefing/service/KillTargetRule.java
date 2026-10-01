package com.phantasm.briefing.service;

import java.util.Locale;

/** Matching is deliberately independent of world/tick access. */
public final class KillTargetRule {
    public enum Disposition { HOSTILE, PASSIVE, NEUTRAL, UNKNOWN, EXCLUDED }

    private KillTargetRule() {
    }

    public static String normalizeScope(String scope) {
        return scope == null || scope.isBlank() ? "specific" : scope.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isValidScope(String scope) {
        return switch (normalizeScope(scope)) {
            case "specific", "hostile", "passive", "neutral" -> true;
            default -> false;
        };
    }

    public static boolean matches(String scope, String targetId, String killedEntityId, Disposition disposition) {
        if (disposition == null || disposition == Disposition.EXCLUDED) return false;
        return switch (normalizeScope(scope)) {
            case "specific" -> targetId != null && !targetId.isBlank() && targetId.equals(killedEntityId);
            case "hostile" -> disposition == Disposition.HOSTILE;
            case "passive" -> disposition == Disposition.PASSIVE;
            case "neutral" -> disposition == Disposition.NEUTRAL;
            default -> false;
        };
    }
}
