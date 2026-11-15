package com.phantasm.briefing.data;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import java.util.Objects;
import javax.annotation.Nonnull;

public record QuestMarkerSpec(
        String label,
        String dimension,
        double x,
        double y,
        double z
) {

    public static QuestMarkerSpec fromJson(@Nonnull String sourceDescription, @Nonnull JsonObject json) {
        String label = sanitize(GsonHelper.getAsString(json, "label", ""));
        String dimension = sanitize(GsonHelper.getAsString(json, "dimension", "minecraft:overworld"));
        String normalizedDimension = Objects.requireNonNull(dimension);
        if (ResourceLocation.tryParse(normalizedDimension) == null) {
            throw new IllegalArgumentException("Quest marker dimension is invalid: " + sourceDescription);
        }

        return new QuestMarkerSpec(
                label,
                normalizedDimension,
                GsonHelper.getAsDouble(json, "x"),
                GsonHelper.getAsDouble(json, "y", 64.0D),
                GsonHelper.getAsDouble(json, "z")
        );
    }

    @Nonnull
    private static String sanitize(String value) {
        return value == null ? "" : value.trim();
    }
}
