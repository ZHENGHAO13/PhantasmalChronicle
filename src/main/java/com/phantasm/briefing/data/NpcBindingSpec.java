package com.phantasm.briefing.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.registries.ForgeRegistries;
import javax.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

import static com.phantasm.briefing.data.JsonDataUtil.sanitize;

public record NpcBindingSpec(
        String bindingId,
        String entityType,
        String questNodeId,
        List<String> questIds,
        String prerequisiteLockedText,
        String profession,
        String customName,
        NpcTypewriterSoundSpec typewriterSound,
        List<NpcStructureSpawnSpec> spawnRules
) {

    public static NpcBindingSpec fromJson(@Nonnull String sourceDescription, @Nonnull JsonObject json) {
        String bindingId = sanitize(GsonHelper.getAsString(json, "bindingId", ""));

        String entityType = sanitize(GsonHelper.getAsString(json, "entityType", ""));
        String questNodeId = sanitize(GsonHelper.getAsString(json, "questNodeId", ""));
        List<String> questIds = readQuestIds(json);
        String prerequisiteLockedText = sanitizeMultiline(GsonHelper.getAsString(json, "prerequisiteLockedText", ""));
        String profession = sanitize(GsonHelper.getAsString(json, "profession", ""));
        String customName = sanitize(GsonHelper.getAsString(json, "customName", ""));
        NpcTypewriterSoundSpec typewriterSound = NpcTypewriterSoundSpec.fromNpcJson(json);

        List<NpcStructureSpawnSpec> spawnRules = new ArrayList<>();
        if (json.has("spawns") && json.get("spawns").isJsonArray()) {
            JsonArray spawns = json.getAsJsonArray("spawns");
            for (int i = 0; i < spawns.size(); i++) {
                if (!spawns.get(i).isJsonObject()) {
                    throw new IllegalArgumentException("NPC spawn entry is not an object: " + sourceDescription + "#spawns[" + i + "]");
                }
                spawnRules.add(NpcStructureSpawnSpec.fromJson(
                        sourceDescription + "#spawns[" + i + "]",
                        bindingId,
                        spawnRules.size(),
                        spawns.get(i).getAsJsonObject()
                ));
            }
        }

        if (bindingId.isBlank()) {
            throw new IllegalArgumentException("NPC binding id is blank: " + sourceDescription);
        }
        if (entityType.isBlank()) {
            throw new IllegalArgumentException("NPC binding entityType is blank: " + sourceDescription);
        }
        return new NpcBindingSpec(
                bindingId,
                entityType,
                questNodeId,
                List.copyOf(questIds),
                prerequisiteLockedText,
                profession,
                customName,
                typewriterSound,
                List.copyOf(spawnRules)
        );
    }

    @Nonnull
    private static List<String> readQuestIds(@Nonnull JsonObject json) {
        List<String> result = new ArrayList<>();
        if (json.has("questIds") && json.get("questIds").isJsonArray()) {
            for (var element : json.getAsJsonArray("questIds")) {
                if (!element.isJsonPrimitive()) continue;
                String value = sanitize(element.getAsString());
                if (!value.isBlank() && !result.contains(value)) result.add(value);
            }
        }
        return result;
    }

    public boolean matches(Entity entity) {
        ResourceLocation entityTypeKey = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (entityTypeKey == null || !entityType.equals(entityTypeKey.toString())) {
            return false;
        }

        if (!customName.isBlank()) {
            if (!entity.hasCustomName()) {
                return false;
            }
            Component customEntityName = entity.getCustomName();
            if (customEntityName == null) {
                return false;
            }
            String entityName = sanitize(customEntityName.getString());
            if (!customName.equals(entityName)) {
                return false;
            }
        }

        if (!profession.isBlank()) {
            if (!(entity instanceof Villager villager)) {
                return false;
            }

            ResourceLocation professionKey = ForgeRegistries.VILLAGER_PROFESSIONS.getKey(villager.getVillagerData().getProfession());
            return professionKey != null && profession.equals(professionKey.toString());
        }

        return true;
    }

    @Nonnull
    private static String sanitizeMultiline(String value) {
        return value == null ? "" : value.strip();
    }
}
