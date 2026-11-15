package com.phantasm.briefing.data;

import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import javax.annotation.Nonnull;

public record TradeCategorySpec(
        String categoryId,
        String title,
        int sortOrder
) {
    public static TradeCategorySpec fromJson(@Nonnull String sourceDescription, @Nonnull JsonObject json) {
        String categoryId = sanitize(GsonHelper.getAsString(json, "categoryId", ""));
        if (categoryId.isBlank()) {
            throw new IllegalArgumentException("Trade category id is blank: " + sourceDescription);
        }

        String title = TradeTextHelper.readText(json.get("displayName"), categoryId);
        int sortOrder = GsonHelper.getAsInt(json, "sortOrder", 0);
        return new TradeCategorySpec(categoryId, title, sortOrder);
    }

    @Nonnull
    private static String sanitize(String value) {
        return value == null ? "" : value.trim();
    }
}
