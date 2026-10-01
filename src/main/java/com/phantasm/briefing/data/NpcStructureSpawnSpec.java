package com.phantasm.briefing.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

import static com.phantasm.briefing.data.JsonDataUtil.sanitize;

public record NpcStructureSpawnSpec(
        String ruleId,
        String structureId,
        String dimension,
        String anchor,
        int offsetX,
        int offsetY,
        int offsetZ,
        int spreadRadius,
        boolean snapToSurface,
        float yaw,
        boolean noAi,
        boolean nameVisible,
        int villagerLevel,
        List<JsonObject> conditions
) {
    public static NpcStructureSpawnSpec fromJson(
            @Nonnull String sourceDescription,
            @Nonnull String bindingId,
            int index,
            @Nonnull JsonObject json
    ) {
        String ruleId = sanitize(GsonHelper.getAsString(json, "ruleId", bindingId + "/spawn_" + index));
        String structureId = normalizeResourceId(GsonHelper.getAsString(json, "structureId", ""), "minecraft");
        String dimension = normalizeResourceId(GsonHelper.getAsString(json, "dimension", ""), "minecraft");
        String anchor = sanitize(GsonHelper.getAsString(json, "anchor", "bottom_center")).toLowerCase();

        int offsetX = GsonHelper.getAsInt(json, "offsetX", 0);
        int offsetY = GsonHelper.getAsInt(json, "offsetY", 1);
        int offsetZ = GsonHelper.getAsInt(json, "offsetZ", 0);

        int spreadRadius = Math.max(0, Math.min(32, GsonHelper.getAsInt(json, "spreadRadius", 0)));
        boolean snapToSurface = GsonHelper.getAsBoolean(json, "snapToSurface", true);
        float yaw = GsonHelper.getAsFloat(json, "yaw", 0.0F);
        boolean noAi = GsonHelper.getAsBoolean(json, "noAi", false);
        boolean nameVisible = GsonHelper.getAsBoolean(json, "nameVisible", true);
        int villagerLevel = Math.max(1, Math.min(5, GsonHelper.getAsInt(json, "villagerLevel", 1)));
        List<JsonObject> conditions = new ArrayList<>();
        if (json.has("conditions") && json.get("conditions").isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray("conditions")) {
                if (element.isJsonObject()) {
                    conditions.add(element.getAsJsonObject().deepCopy());
                }
            }
        }

        if (ruleId.isBlank()) {
            throw new IllegalArgumentException("NPC spawn ruleId is blank: " + sourceDescription);
        }
        if (structureId.isBlank() || ResourceLocation.tryParse(structureId) == null) {
            throw new IllegalArgumentException("NPC spawn structureId is invalid: " + sourceDescription);
        }
        if (!dimension.isBlank() && ResourceLocation.tryParse(dimension) == null) {
            throw new IllegalArgumentException("NPC spawn dimension is invalid: " + sourceDescription);
        }
        if (!isSupportedAnchor(anchor)) {
            throw new IllegalArgumentException("NPC spawn anchor is invalid: " + anchor + " in " + sourceDescription);
        }

        return new NpcStructureSpawnSpec(
                ruleId,
                structureId,
                dimension,
                anchor,
                offsetX,
                offsetY,
                offsetZ,
                spreadRadius,
                snapToSurface,
                yaw,
                noAi,
                nameVisible,
                villagerLevel,
                List.copyOf(conditions)
        );
    }

    public boolean matches(String structureId, String dimensionId) {
        return this.structureId.equals(normalizeResourceId(structureId, "minecraft"))
                && (this.dimension.isBlank() || this.dimension.equals(normalizeResourceId(dimensionId, "minecraft")));
    }

    private static boolean isSupportedAnchor(String anchor) {
        return switch (anchor) {
            case "bottom_center", "center", "min", "max", "start_chunk" -> true;
            default -> false;
        };
    }

    private static String normalizeResourceId(String value, String defaultNamespace) {
        String normalized = sanitize(value).toLowerCase();
        if (normalized.isBlank()) {
            return "";
        }
        return normalized.contains(":") ? normalized : defaultNamespace + ":" + normalized;
    }
}
