package com.phantasm.briefing.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;

public final class QuestDataManager extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = JsonRegistryLoader.GSON;
    private static final String DATA_DIRECTORY = "phantasm_quests";
    private static final Path EXTERNAL_QUEST_DIRECTORY = JsonRegistryLoader.externalDirectory(DATA_DIRECTORY);

    private static final QuestDataManager INSTANCE = new QuestDataManager();

    private volatile Map<String, QuestSpec> questsById = Map.of();

    private QuestDataManager() {
        super(GSON, DATA_DIRECTORY);
    }

    public static QuestDataManager getInstance() {
        return INSTANCE;
    }

    public Optional<QuestSpec> getQuest(String questId) {
        return JsonRegistryLoader.findExact(this.questsById, questId);
    }

    public Collection<QuestSpec> getAllQuests() {
        return this.questsById.values();
    }

    @Override
    protected void apply(
            @Nonnull Map<ResourceLocation, JsonElement> jsonMap,
            @Nonnull ResourceManager resourceManager,
            @Nonnull ProfilerFiller profilerFiller
    ) {
        JsonRegistryLoader.Result<QuestSpec> result = JsonRegistryLoader.load(
                jsonMap,
                EXTERNAL_QUEST_DIRECTORY,
                "quest",
                QuestSpec::fromJson,
                QuestSpec::questId,
                LOGGER
        );
        this.questsById = result.values();
        LOGGER.info(
                "[PhantasmBriefing] Loaded {} quests (datapack={}, externalAdded={}, externalOverrides={})",
                this.questsById.size(),
                result.datapackCount(),
                result.externalAdded(),
                result.externalOverrides()
        );
    }
}
