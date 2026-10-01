package com.phantasm.briefing.service;

/** Validates the exact handbook page that is allowed to complete a read-manual objective. */
public final class ManualReadCompletionPolicy {
    private ManualReadCompletionPolicy() {}

    public static boolean matches(String requiredManualId, String requiredLessonId, String requiredPageId,
                                  String viewedManualId, String viewedLessonId, String viewedPageId,
                                  boolean lastPage) {
        return requiredManualId != null && !requiredManualId.isBlank()
                && requiredManualId.equals(viewedManualId)
                && requiredLessonId != null && !requiredLessonId.isBlank()
                && requiredLessonId.equals(viewedLessonId)
                && viewedPageId != null && !viewedPageId.isBlank()
                && (requiredPageId == null || requiredPageId.isBlank()
                    ? lastPage : requiredPageId.equals(viewedPageId));
    }
}
