package com.phantasm.briefing.data;

import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import javax.annotation.Nonnull;

import static com.phantasm.briefing.data.JsonDataUtil.sanitize;

public record QuestTreeLayoutSpec(
        String treeId,
        int x,
        int y
) {
    @Nonnull
    public static QuestTreeLayoutSpec fromJson(@Nonnull String sourceDescription, @Nonnull JsonObject json) {
        String treeId = sanitize(GsonHelper.getAsString(json, "treeId", ""));
        return new QuestTreeLayoutSpec(
                treeId,
                GsonHelper.getAsInt(json, "x", 0),
                GsonHelper.getAsInt(json, "y", 0)
        );
    }
}
