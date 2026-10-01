package com.phantasm.briefing.data;

public final class TutorialAccessPolicy {
    private TutorialAccessPolicy() {
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
            boolean objectiveCompleted,
            boolean tutorialViewed
    ) {
        return questActive && phaseCurrent && objectiveCurrent && !objectiveCompleted && tutorialViewed;
    }
}
