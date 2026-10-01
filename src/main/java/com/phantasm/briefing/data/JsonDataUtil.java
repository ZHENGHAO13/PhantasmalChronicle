package com.phantasm.briefing.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Parsing helpers for the current content schema. Old shapes are rewritten before runtime loading. */
public final class JsonDataUtil {
    private JsonDataUtil() {
    }

    @Nonnull
    public static String sanitize(String value) {
        return value == null ? "" : value.trim();
    }

    /** Reads a current-schema primitive text field. */
    @Nonnull
    public static String readText(@Nonnull JsonObject json, @Nonnull String key) {
        if (!json.has(key) || json.get(key).isJsonNull() || !json.get(key).isJsonPrimitive()) {
            return "";
        }
        return sanitize(json.get(key).getAsString());
    }

    /** Reads a current-schema array of primitive text strings. */
    @Nonnull
    public static List<String> readTextArray(@Nonnull JsonObject json, @Nonnull String key) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, key)) {
            if (!element.isJsonPrimitive()) {
                continue;
            }
            String value = sanitize(element.getAsString());
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    /** Reads a JSON-object array and returns defensive copies. */
    @Nonnull
    public static List<JsonObject> readObjectArray(@Nonnull JsonObject json, @Nonnull String key) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            return List.of();
        }
        List<JsonObject> values = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, key)) {
            if (element.isJsonObject()) {
                values.add(element.getAsJsonObject().deepCopy());
            }
        }
        return List.copyOf(values);
    }

    @Nonnull
    public static String normalizeDialogueAutoTrigger(String value) {
        return "player_attack".equals(sanitize(value).toLowerCase(Locale.ROOT)) ? "player_attack" : "none";
    }

    @Nonnull
    public static List<String> readStringArray(@Nonnull JsonObject json, @Nonnull String key) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, key)) {
            if (!element.isJsonPrimitive()) {
                continue;
            }
            String value = sanitize(element.getAsString());
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    @Nonnull
    public static List<DialogueAction> readActions(
            @Nonnull String sourceDescription,
            @Nonnull JsonObject json,
            @Nonnull String key
    ) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            return List.of();
        }
        List<DialogueAction> result = new ArrayList<>();
        int index = 0;
        for (JsonElement element : GsonHelper.getAsJsonArray(json, key)) {
            if (element.isJsonObject()) {
                result.add(DialogueAction.fromJson(
                        sourceDescription + "#" + key + "[" + index + "]",
                        element.getAsJsonObject()
                ));
            }
            index++;
        }
        return List.copyOf(result);
    }

    @Nonnull
    public static QuestHintType parseHintType(String value) {
        String normalized = sanitize(value).replace('-', '_').toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return QuestHintType.ADVANCE;
        }
        return switch (normalized) {
            case "COMPLETE" -> QuestHintType.COMPLETE;
            case "AVAILABLE" -> QuestHintType.AVAILABLE;
            case "NONE" -> QuestHintType.NONE;
            default -> QuestHintType.ADVANCE;
        };
    }
}
