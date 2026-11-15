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
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;

public final class TradeDataManager extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = JsonRegistryLoader.GSON;
    private static final String DATA_DIRECTORY = "phantasm_trades";
    private static final Path EXTERNAL_TRADE_DIRECTORY = JsonRegistryLoader.externalDirectory(DATA_DIRECTORY);

    private static final TradeDataManager INSTANCE = new TradeDataManager();

    private volatile Map<String, TradeSpec> tradesById = Map.of();

    private TradeDataManager() {
        super(GSON, DATA_DIRECTORY);
    }

    public static TradeDataManager getInstance() {
        return INSTANCE;
    }

    public Optional<TradeSpec> getTrade(String tradeId) {
        return JsonRegistryLoader.findWithLocalAlias(this.tradesById, tradeId);
    }

    @Override
    protected void apply(
            @Nonnull Map<ResourceLocation, JsonElement> jsonMap,
            @Nonnull ResourceManager resourceManager,
            @Nonnull ProfilerFiller profilerFiller
    ) {
        JsonRegistryLoader.Result<TradeSpec> result = JsonRegistryLoader.load(
                jsonMap,
                EXTERNAL_TRADE_DIRECTORY,
                "trade",
                TradeSpec::fromJson,
                TradeSpec::tradeId,
                LOGGER
        );
        this.tradesById = result.values();
        LOGGER.info(
                "[PhantasmBriefing] Loaded {} trades (datapack={}, externalAdded={}, externalOverrides={})",
                this.tradesById.size(),
                result.datapackCount(),
                result.externalAdded(),
                result.externalOverrides()
        );
    }
}
