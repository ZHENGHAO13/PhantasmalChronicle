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

public final class ShopDataManager extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = JsonRegistryLoader.GSON;
    private static final String DATA_DIRECTORY = "phantasm_shops";
    private static final Path EXTERNAL_SHOP_DIRECTORY = JsonRegistryLoader.externalDirectory(DATA_DIRECTORY);

    private static final ShopDataManager INSTANCE = new ShopDataManager();

    private volatile Map<String, ShopSpec> shopsById = Map.of();

    private ShopDataManager() {
        super(GSON, DATA_DIRECTORY);
    }

    public static ShopDataManager getInstance() {
        return INSTANCE;
    }

    public Optional<ShopSpec> getShop(String shopId) {
        return JsonRegistryLoader.findWithLocalAlias(this.shopsById, shopId);
    }

    @Override
    protected void apply(
            @Nonnull Map<ResourceLocation, JsonElement> jsonMap,
            @Nonnull ResourceManager resourceManager,
            @Nonnull ProfilerFiller profilerFiller
    ) {
        JsonRegistryLoader.Result<ShopSpec> result = JsonRegistryLoader.load(
                jsonMap,
                EXTERNAL_SHOP_DIRECTORY,
                "shop",
                ShopSpec::fromJson,
                ShopSpec::shopId,
                LOGGER
        );
        this.shopsById = result.values();
        LOGGER.info(
                "[PhantasmBriefing] Loaded {} shops (datapack={}, externalAdded={}, externalOverrides={})",
                this.shopsById.size(),
                result.datapackCount(),
                result.externalAdded(),
                result.externalOverrides()
        );
    }
}
