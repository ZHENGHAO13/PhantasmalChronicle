package com.phantasm.briefing.data;

import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import javax.annotation.Nonnull;

import static com.phantasm.briefing.data.JsonDataUtil.readText;
import static com.phantasm.briefing.data.JsonDataUtil.sanitize;

public record TradeCategorySpec(String categoryId, String title, int sortOrder) {
    public static TradeCategorySpec fromJson(@Nonnull String sourceDescription, @Nonnull JsonObject json) {
        String categoryId = sanitize(GsonHelper.getAsString(json, "categoryId", ""));
        if (categoryId.isBlank()) throw new IllegalArgumentException("Trade category id is blank: " + sourceDescription);
        String title = readText(json, "displayName");
        if (title.isBlank()) title = categoryId;
        return new TradeCategorySpec(categoryId, title, GsonHelper.getAsInt(json, "sortOrder", 0));
    }
}
