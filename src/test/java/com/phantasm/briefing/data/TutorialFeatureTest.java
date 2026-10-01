package com.phantasm.briefing.data;

public final class TutorialFeatureTest {
    private TutorialFeatureTest() {
    }

    public static void main(String[] args) {
        parsesTutorialObjectiveAliases();
        acceptsSafePngNames();
        rejectsUnsafeImagePaths();
        supportsImageOnlyAndTextOnlyPages();
        limitsPageTextForNetworkSafety();
        appliesTutorialAccessRules();
        appliesTutorialCompletionRules();
    }

    private static void parsesTutorialObjectiveAliases() {
        expectEquals(QuestObjectiveType.TUTORIAL, QuestObjectiveType.parse("tutorial"));
        expectEquals(QuestObjectiveType.TUTORIAL, QuestObjectiveType.parse("image_tutorial"));
        expectEquals(QuestObjectiveType.TUTORIAL, QuestObjectiveType.parse("view_tutorial"));
    }

    private static void acceptsSafePngNames() {
        expectEquals("guide-page_01.png", TutorialImagePath.normalize(" guide-page_01.PNG "));
        expectEquals("", TutorialImagePath.normalize("guide.jpg"));
    }

    private static void rejectsUnsafeImagePaths() {
        expectEquals("", TutorialImagePath.normalize("../secret.png"));
        expectEquals("", TutorialImagePath.normalize("folder/secret.png"));
        expectEquals("", TutorialImagePath.normalize("C:\\secret.png"));
        expectEquals("", TutorialImagePath.normalize(".hidden.png"));
    }

    private static void supportsImageOnlyAndTextOnlyPages() {
        TutorialPageSpec imageOnly = new TutorialPageSpec("", "", "guide.png");
        TutorialPageSpec textOnly = new TutorialPageSpec("Title", "Description", "");
        TutorialPageSpec empty = new TutorialPageSpec(" ", " ", " ");
        expectTrue(imageOnly.hasContent());
        expectTrue(textOnly.hasContent());
        expectFalse(empty.hasContent());
    }

    private static void limitsPageTextForNetworkSafety() {
        TutorialPageSpec page = new TutorialPageSpec("T".repeat(300), "X".repeat(5000), "guide.png");
        expectEquals(256, page.title().length());
        expectEquals(4096, page.text().length());
    }

    private static void appliesTutorialAccessRules() {
        expectTrue(TutorialAccessPolicy.canView(true, false, false, false, false));
        expectTrue(TutorialAccessPolicy.canView(false, true, false, false, false));
        expectTrue(TutorialAccessPolicy.canView(false, false, true, true, false));
        expectFalse(TutorialAccessPolicy.canView(false, false, true, false, false));
        expectFalse(TutorialAccessPolicy.canView(false, false, false, false, false));
    }

    private static void appliesTutorialCompletionRules() {
        expectTrue(TutorialAccessPolicy.canComplete(true, true, true, false, true));
        expectFalse(TutorialAccessPolicy.canComplete(false, true, true, false, true));
        expectFalse(TutorialAccessPolicy.canComplete(true, false, true, false, true));
        expectFalse(TutorialAccessPolicy.canComplete(true, true, false, false, true));
        expectFalse(TutorialAccessPolicy.canComplete(true, true, true, true, true));
        expectFalse(TutorialAccessPolicy.canComplete(true, true, true, false, false));
    }

    private static void expectTrue(boolean value) {
        if (!value) {
            throw new AssertionError("Expected true");
        }
    }

    private static void expectFalse(boolean value) {
        if (value) {
            throw new AssertionError("Expected false");
        }
    }

    private static void expectEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }
}
