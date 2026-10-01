package com.phantasm.briefing.data;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import java.util.Objects;
import javax.annotation.Nonnull;

import static com.phantasm.briefing.data.JsonDataUtil.sanitize;
import static com.phantasm.briefing.data.JsonDataUtil.readText;

public record QuestMarkerSpec(
        String label,
        String dimension,
        double x,
        double y,
        double z
) {

    public static QuestMarkerSpec fromJson(@Nonnull String sourceDescription, @Nonnull JsonObject json) {
        String label = readText(json, "label");
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
}
