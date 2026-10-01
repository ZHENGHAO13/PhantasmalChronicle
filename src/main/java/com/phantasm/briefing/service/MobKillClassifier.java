package com.phantasm.briefing.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.phantasm.briefing.PhantasmBriefing;
import com.phantasm.briefing.api.IQuestNPC;
import com.phantasm.briefing.util.QuestNpcHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Called only for player-attributed deaths, never from a tick/area scan. */
public final class MobKillClassifier {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Path OVERRIDES_FILE = FMLPaths.CONFIGDIR.get()
            .resolve(PhantasmBriefing.MOD_ID).resolve("mob_categories.json");
    private static final long MAX_CONFIG_BYTES = 1024L * 1024L;
    private static final int MAX_OVERRIDES = 4096;
    private static final Map<EntityType<?>, KillTargetRule.Disposition> CATEGORY_CACHE = new ConcurrentHashMap<>();
    private static volatile Map<String, KillTargetRule.Disposition> overrides = Map.of();

    private MobKillClassifier() {
    }

    public static KillTargetRule.Disposition classify(LivingEntity entity) {
        // Overrides must never turn players or story NPCs into countable kills.
        if (entity instanceof Player || entity instanceof IQuestNPC
                || (QuestNpcHelper.isPotentialQuestNpc(entity) && QuestNpcHelper.isQuestNPC(entity))) {
            return KillTargetRule.Disposition.EXCLUDED;
        }
        return CATEGORY_CACHE.computeIfAbsent(entity.getType(), type -> {
            ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(type);
            KillTargetRule.Disposition override = key == null ? null : overrides.get(key.toString());
            return KillClassificationRules.classify(type.getCategory().name(), entity instanceof NeutralMob,
                    entity instanceof Monster, entity instanceof Animal, override);
        });
    }

    /** Read optional server-side overrides once per quest data reload. Never read files during deaths. */
    public static void reloadOverrides() {
        if (!Files.isRegularFile(OVERRIDES_FILE)) {
            overrides = Map.of();
            CATEGORY_CACHE.clear();
            return;
        }
        try {
            if (Files.size(OVERRIDES_FILE) > MAX_CONFIG_BYTES) {
                throw new IllegalArgumentException("mob_categories.json is larger than 1 MB");
            }
            Map<String, KillTargetRule.Disposition> result = new HashMap<>();
            try (Reader reader = Files.newBufferedReader(OVERRIDES_FILE, StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                JsonObject entries = json.getAsJsonObject("overrides");
                if (entries == null) throw new IllegalArgumentException("Missing overrides object");
                if (entries.size() > MAX_OVERRIDES) throw new IllegalArgumentException("Too many overrides");
                for (Map.Entry<String, JsonElement> entry : entries.entrySet()) {
                    ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                    if (id == null || !entry.getValue().isJsonPrimitive()
                            || !entry.getValue().getAsJsonPrimitive().isString()) {
                        throw new IllegalArgumentException("Invalid entity category override: " + entry.getKey());
                    }
                    KillTargetRule.Disposition disposition = switch (entry.getValue().getAsString().trim().toLowerCase(java.util.Locale.ROOT)) {
                        case "hostile" -> KillTargetRule.Disposition.HOSTILE;
                        case "passive" -> KillTargetRule.Disposition.PASSIVE;
                        case "neutral" -> KillTargetRule.Disposition.NEUTRAL;
                        case "exclude" -> KillTargetRule.Disposition.EXCLUDED;
                        default -> throw new IllegalArgumentException("Unknown category for " + id);
                    };
                    result.put(id.toString(), disposition);
                }
            }
            overrides = Map.copyOf(result);
            CATEGORY_CACHE.clear();
            LOGGER.info("[PhantasmBriefing] Loaded {} mob category overrides from {}", overrides.size(), OVERRIDES_FILE);
        } catch (Exception exception) {
            // Preserve last known-good categories and cached results, rather than changing rules after a bad edit.
            LOGGER.error("[PhantasmBriefing] Invalid mob category overrides at {}: {}. Previous rules are kept.",
                    OVERRIDES_FILE, exception.getMessage());
        }
    }
}
