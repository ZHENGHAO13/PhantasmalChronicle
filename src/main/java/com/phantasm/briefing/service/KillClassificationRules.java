package com.phantasm.briefing.service;

/** Pure classification policy; callers handle player/NPC exclusion first. */
public final class KillClassificationRules {
    private KillClassificationRules() {
    }

    public static KillTargetRule.Disposition classify(String spawnCategory, boolean neutralMob,
                                                      boolean monster, boolean animal,
                                                      KillTargetRule.Disposition override) {
        if (override != null) return override;
        if (neutralMob) return KillTargetRule.Disposition.NEUTRAL;
        if (monster || "MONSTER".equals(spawnCategory)) return KillTargetRule.Disposition.HOSTILE;
        if (animal) return KillTargetRule.Disposition.PASSIVE;
        return switch (spawnCategory == null ? "" : spawnCategory) {
            case "CREATURE", "AMBIENT", "WATER_CREATURE", "WATER_AMBIENT", "AXOLOTLS", "UNDERGROUND_WATER_CREATURE" -> KillTargetRule.Disposition.PASSIVE;
            default -> KillTargetRule.Disposition.UNKNOWN;
        };
    }
}
