package com.phantasm.briefing.service;

public final class ManualReadAccessPolicy {
    private ManualReadAccessPolicy() {
    }

    public static boolean canView(
            boolean questCompleted,
            boolean phaseCompleted,
            boolean phaseCurrent,
            boolean objectiveCurrent,
            boolean objectiveCompleted
    ) {
        return questCompleted
                || phaseCompleted
                || objectiveCompleted
                || (phaseCurrent && objectiveCurrent);
    }

    public static boolean canComplete(
            boolean questActive,
            boolean phaseCurrent,
            boolean objectiveCurrent,
            boolean objectiveCompleted
    ) {
        return questActive && phaseCurrent && objectiveCurrent && !objectiveCompleted;
    }
}
