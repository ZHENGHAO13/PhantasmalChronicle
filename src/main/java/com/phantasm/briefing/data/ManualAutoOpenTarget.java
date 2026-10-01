package com.phantasm.briefing.data;

/** Client navigation target for a one-shot handbook auto-open request. */
public record ManualAutoOpenTarget(
        String questId,
        String phaseId,
        String objectiveId,
        String manualId,
        String lessonId,
        String pageId
) {
}
