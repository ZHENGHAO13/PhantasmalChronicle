package com.phantasm.briefing.data;

/** One persistent, addressable illustrated handbook page. */
public record ManualPageSpec(String pageId, String title, String text, String image) {
    public boolean hasContent() {
        return !title.isBlank() || !text.isBlank() || !image.isBlank();
    }
}
