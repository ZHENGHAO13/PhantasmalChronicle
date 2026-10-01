package com.phantasm.briefing.data;

import com.phantasm.briefing.service.ManualReadAccessPolicy;
import com.phantasm.briefing.service.ManualReadCompletionPolicy;

public final class ManualReadFeatureTest {
    private ManualReadFeatureTest() {
    }

    public static void main(String[] args) {
        normalizesManualImageNames();
        appliesManualReadAccessRules();
        appliesManualReadCompletionRules();
    }

    private static void normalizesManualImageNames() {
        expectEquals("guide.png", ContentImagePath.normalize("guide.png"));
        expectEquals("", ContentImagePath.normalize("../guide.png"));
        expectEquals("", ContentImagePath.normalize("guide.jpg"));
    }

    private static void appliesManualReadAccessRules() {
        expectTrue(ManualReadAccessPolicy.canView(true, false, false, false, false));
        expectTrue(ManualReadAccessPolicy.canView(false, true, false, false, false));
        expectTrue(ManualReadAccessPolicy.canView(false, false, true, true, false));
        expectFalse(ManualReadAccessPolicy.canView(false, false, true, false, false));
        expectFalse(ManualReadAccessPolicy.canView(false, false, false, false, false));
    }

    private static void appliesManualReadCompletionRules() {
        expectTrue(ManualReadAccessPolicy.canComplete(true, true, true, false));
        expectFalse(ManualReadAccessPolicy.canComplete(false, true, true, false));
        expectFalse(ManualReadAccessPolicy.canComplete(true, false, true, false));
        expectFalse(ManualReadAccessPolicy.canComplete(true, true, false, false));
        expectFalse(ManualReadAccessPolicy.canComplete(true, true, true, true));

        expectTrue(ManualReadCompletionPolicy.matches(
                "phantasmbriefing:guide", "lesson_main", "page_2",
                "phantasmbriefing:guide", "lesson_main", "page_2", false));
        expectFalse(ManualReadCompletionPolicy.matches(
                "phantasmbriefing:guide", "lesson_main", "page_2",
                "phantasmbriefing:guide", "lesson_main", "page_1", false));
        // Migrated image-tutorial objectives use an empty required page and therefore complete on the last page.
        expectTrue(ManualReadCompletionPolicy.matches(
                "phantasmbriefing:guide", "lesson_main", "",
                "phantasmbriefing:guide", "lesson_main", "page_2", true));
        expectFalse(ManualReadCompletionPolicy.matches(
                "phantasmbriefing:guide", "lesson_main", "",
                "phantasmbriefing:guide", "lesson_main", "page_1", false));
        expectFalse(ManualReadCompletionPolicy.matches(
                "phantasmbriefing:guide", "lesson_main", "page_2",
                "phantasmbriefing:other", "lesson_main", "page_2", false));
    }

    private static void expectTrue(boolean value) {
        if (!value) throw new AssertionError("Expected true");
    }

    private static void expectFalse(boolean value) {
        if (value) throw new AssertionError("Expected false");
    }

    private static void expectEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }
}
