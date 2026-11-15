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

public final class WalletDataManager extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = JsonRegistryLoader.GSON;
    private static final String DATA_DIRECTORY = "phantasm_wallet";
    private static final Path EXTERNAL_WALLET_DIRECTORY = JsonRegistryLoader.externalDirectory(DATA_DIRECTORY);

    private static final WalletDataManager INSTANCE = new WalletDataManager();

    private volatile Map<String, WalletSpec> walletsById = Map.of();

    private WalletDataManager() {
        super(GSON, DATA_DIRECTORY);
    }

    public static WalletDataManager getInstance() {
        return INSTANCE;
    }

    public Optional<WalletSpec> getWallet(String walletId) {
        return JsonRegistryLoader.findWithLocalAlias(this.walletsById, walletId);
    }

    public Collection<WalletSpec> getAllWallets() {
        return this.walletsById.values();
    }

    @Override
    protected void apply(
            @Nonnull Map<ResourceLocation, JsonElement> jsonMap,
            @Nonnull ResourceManager resourceManager,
            @Nonnull ProfilerFiller profilerFiller
    ) {
        JsonRegistryLoader.Result<WalletSpec> result = JsonRegistryLoader.load(
                jsonMap,
                EXTERNAL_WALLET_DIRECTORY,
                "wallet",
                WalletSpec::fromJson,
                WalletSpec::walletId,
                LOGGER
        );
        this.walletsById = result.values();
        LOGGER.info(
                "[PhantasmBriefing] Loaded {} wallets (datapack={}, externalAdded={}, externalOverrides={})",
                this.walletsById.size(),
                result.datapackCount(),
                result.externalAdded(),
                result.externalOverrides()
        );
    }
}
