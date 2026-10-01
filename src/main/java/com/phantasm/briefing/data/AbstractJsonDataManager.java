package com.phantasm.briefing.data;

import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Common reload/storage layer for simple ID-addressed content registries.
 * Domain managers keep their own public API while sharing the file loading implementation.
 */
abstract class AbstractJsonDataManager<T> extends SimpleJsonResourceReloadListener {
    private final Logger logger;
    private final Path externalDirectory;
    private final String contentLabel;
    private final JsonRegistryLoader.Decoder<T> parser;
    private final Function<T, String> idGetter;
    private volatile Map<String, T> values = Map.of();

    protected AbstractJsonDataManager(
            String dataDirectory,
            String contentLabel,
            JsonRegistryLoader.Decoder<T> parser,
            Function<T, String> idGetter,
            Logger logger
    ) {
        super(JsonRegistryLoader.GSON, dataDirectory);
        this.logger = logger;
        this.externalDirectory = JsonRegistryLoader.externalDirectory(dataDirectory);
        this.contentLabel = contentLabel;
        this.parser = parser;
        this.idGetter = idGetter;
    }

    protected final Optional<T> find(String id) {
        return JsonRegistryLoader.findWithLocalAlias(this.values, id);
    }

    protected final Collection<T> values() {
        return this.values.values();
    }

    protected final Map<String, T> valuesById() {
        return this.values;
    }

    @Override
    protected void apply(
            @Nonnull Map<ResourceLocation, JsonElement> jsonMap,
            @Nonnull ResourceManager resourceManager,
            @Nonnull ProfilerFiller profilerFiller
    ) {
        JsonRegistryLoader.Result<T> result = JsonRegistryLoader.load(
                jsonMap,
                this.externalDirectory,
                this.contentLabel,
                this.parser,
                this.idGetter,
                this.logger
        );
        this.values = result.values();
        this.logger.info(
                "[PhantasmBriefing] Loaded {} {}s (datapack={}, externalAdded={}, externalOverrides={})",
                this.values.size(), this.contentLabel,
                result.datapackCount(), result.externalAdded(), result.externalOverrides()
        );
        afterReload(this.values);
    }

    protected void afterReload(Map<String, T> loadedValues) {
    }
}
