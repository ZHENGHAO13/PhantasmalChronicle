package com.phantasm.briefing.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import java.util.Objects;
import javax.annotation.Nonnull;

public final class TradeTextHelper {
    private TradeTextHelper() {
    }

    @Nonnull
    public static String readText(JsonElement element, String fallback) {
        if (element == null || element.isJsonNull()) {
            return sanitize(fallback);
        }
        if (element.isJsonPrimitive()) {
            String value = sanitize(element.getAsString());
            return value.isBlank() ? sanitize(fallback) : value;
        }
        if (!element.isJsonObject()) {
            return sanitize(fallback);
        }

        JsonObject json = Objects.requireNonNull(element.getAsJsonObject());
        String mode = sanitize(GsonHelper.getAsString(json, "mode", ""));
        if ("literal".equals(mode)) {
            String value = sanitize(GsonHelper.getAsString(json, "value", ""));
            return value.isBlank() ? sanitize(fallback) : value;
        }
        if ("translate".equals(mode)) {
            String key = sanitize(GsonHelper.getAsString(json, "key", ""));
            return key.isBlank() ? sanitize(fallback) : key;
        }

        return sanitize(fallback);
    }

    @Nonnull
    private static String sanitize(String value) {
        return value == null ? "" : value.trim();
    }
}
