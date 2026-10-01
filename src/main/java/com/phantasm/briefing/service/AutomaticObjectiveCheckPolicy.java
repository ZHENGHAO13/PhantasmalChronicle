package com.phantasm.briefing.service;

/** Server-side automatic-objective scheduling decisions; does not inspect the world. */
public final class AutomaticObjectiveCheckPolicy {
    private AutomaticObjectiveCheckPolicy() {
    }

    public static boolean shouldEvaluate(boolean hasCollect, boolean hasVisit,
                                         boolean inventoryChanged, boolean movedToVisit,
                                         boolean fallbackDue) {
        return (hasCollect && inventoryChanged)
                || (hasVisit && movedToVisit)
                || ((hasCollect || hasVisit) && fallbackDue);
    }
}
