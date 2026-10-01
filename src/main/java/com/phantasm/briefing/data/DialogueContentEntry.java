package com.phantasm.briefing.data;

import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import static com.phantasm.briefing.data.JsonDataUtil.readText;

public record DialogueContentEntry(DialogueContentType type, String value) {
    public DialogueContentEntry {
        type = type == null ? DialogueContentType.TEXT : type;
        value = value == null ? "" : value.trim();
    }

    public static DialogueContentEntry text(String text) {
        return new DialogueContentEntry(DialogueContentType.TEXT, text == null ? "" : text);
    }

    public static DialogueContentEntry image(String image) {
        String normalized = ContentImagePath.normalize(image);
        return new DialogueContentEntry(DialogueContentType.IMAGE, normalized);
    }

    public boolean isText() {
        return type == DialogueContentType.TEXT;
    }

    public boolean isImage() {
        return type == DialogueContentType.IMAGE;
    }

    public static DialogueContentEntry fromJson(JsonObject json) {
        DialogueContentType type = DialogueContentType.fromSerializedName(GsonHelper.getAsString(json, "type", "text"));
        if (type == DialogueContentType.IMAGE) {
            String image = ContentImagePath.normalize(GsonHelper.getAsString(json, "image", ""));
            if (image.isBlank()) throw new IllegalArgumentException("Dialogue image path is blank or invalid");
            return image(image);
        }
        String text = readText(json, "text");
        if (text.isBlank()) throw new IllegalArgumentException("Dialogue text content is blank");
        return text(text);
    }
}
