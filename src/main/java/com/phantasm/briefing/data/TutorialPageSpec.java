package com.phantasm.briefing.data;

public record TutorialPageSpec(
        String title,
        String text,
        String image
) {
    private static final int MAX_TITLE_LENGTH = 256;
    private static final int MAX_TEXT_LENGTH = 4096;

    public TutorialPageSpec {
        title = sanitize(title, MAX_TITLE_LENGTH);
        text = sanitize(text, MAX_TEXT_LENGTH);
        image = TutorialImagePath.normalize(image);
    }

    public boolean hasContent() {
        return !title.isBlank() || !text.isBlank() || !image.isBlank();
    }

    private static String sanitize(String value, int maxLength) {
        String sanitized = value == null ? "" : value.trim();
        return sanitized.length() <= maxLength ? sanitized : sanitized.substring(0, maxLength);
    }
}
