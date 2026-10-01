package com.phantasm.briefing.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.phantasm.briefing.PhantasmBriefing;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

final class JsonRegistryLoader {
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private JsonRegistryLoader() {
    }

    static Path externalDirectory(String dataDirectory) {
        return FMLPaths.CONFIGDIR.get()
                .resolve(PhantasmBriefing.MOD_ID)
                .resolve("briefing_pack")
                .resolve(dataDirectory);
    }

    static <T> Result<T> load(
            Map<ResourceLocation, JsonElement> resources,
            Path externalDirectory,
            String typeName,
            Decoder<T> decoder,
            Function<T, String> idResolver,
            Logger logger
    ) {
        logger.info("[PhantasmBriefing] {} external config directory: {}", typeName, externalDirectory.toAbsolutePath().normalize());
        Map<String, T> values = new LinkedHashMap<>();
        int datapackCount = loadDatapack(resources, typeName, decoder, idResolver, values, logger);
        ExternalCounts externalCounts = loadExternal(
                externalDirectory,
                typeName,
                decoder,
                idResolver,
                values,
                logger
        );
        logger.info("[PhantasmBriefing] {}: bundled={}, external scanned={}, imported={}, overrides={}, rejected={}, resolved total={}",
                typeName, datapackCount, externalCounts.scanned(), externalCounts.added(),
                externalCounts.overridden(), externalCounts.rejected(), values.size());
        return new Result<>(
                Map.copyOf(values),
                datapackCount,
                externalCounts.added(),
                externalCounts.overridden()
        );
    }

    static <T> Optional<T> findExact(Map<String, T> values, String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(values.get(rawId.trim()));
    }

    static <T> Optional<T> findWithLocalAlias(Map<String, T> values, String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return Optional.empty();
        }

        String id = rawId.trim();
        T direct = values.get(id);
        if (direct != null) {
            return Optional.of(direct);
        }

        if (!id.contains(":")) {
            T namespaced = values.get(PhantasmBriefing.MOD_ID + ":" + id);
            if (namespaced != null) {
                return Optional.of(namespaced);
            }
        }

        ResourceLocation resourceLocation = ResourceLocation.tryParse(id);
        if (resourceLocation == null || !PhantasmBriefing.MOD_ID.equals(resourceLocation.getNamespace())) {
            return Optional.empty();
        }
        return Optional.ofNullable(values.get(resourceLocation.getPath()));
    }

    private static <T> int loadDatapack(
            Map<ResourceLocation, JsonElement> resources,
            String typeName,
            Decoder<T> decoder,
            Function<T, String> idResolver,
            Map<String, T> destination,
            Logger logger
    ) {
        int count = 0;
        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            ResourceLocation source = entry.getKey();
            JsonElement element = entry.getValue();
            if (!element.isJsonObject()) {
                logger.warn("[PhantasmBriefing] Skipping non-object {} json: {}", typeName, source);
                continue;
            }

            try {
                T value = decoder.decode(source.toString(), element.getAsJsonObject());
                String id = idResolver.apply(value);
                T previous = destination.put(id, value);
                if (previous == null) {
                    count++;
                } else {
                    logger.warn("[PhantasmBriefing] Duplicate datapack {} id '{}' from {}", typeName, id, source);
                }
            } catch (RuntimeException exception) {
                logger.error("[PhantasmBriefing] Failed to parse {} {}: {}", typeName, source, exception.getMessage());
            }
        }
        return count;
    }

    private static <T> ExternalCounts loadExternal(
            Path directory,
            String typeName,
            Decoder<T> decoder,
            Function<T, String> idResolver,
            Map<String, T> destination,
            Logger logger
    ) {
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            logger.error("[PhantasmBriefing] Failed to prepare external {} directory {}: {}", typeName, directory, exception.getMessage());
            return ExternalCounts.EMPTY;
        }

        int added = 0;
        int overridden = 0;
        int scanned = 0;
        int rejected = 0;
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path file : paths.filter(Files::isRegularFile).filter(JsonRegistryLoader::isJsonFile).sorted().toList()) {
                scanned++;
                LoadOutcome outcome = loadExternalFile(file, directory, typeName, decoder, idResolver, destination, logger);
                if (outcome == LoadOutcome.ADDED) {
                    added++;
                } else if (outcome == LoadOutcome.OVERRIDDEN) {
                    overridden++;
                } else {
                    rejected++;
                }
            }
        } catch (IOException exception) {
            logger.error("[PhantasmBriefing] Failed to scan external {} directory {}: {}", typeName, directory, exception.getMessage());
        }
        return new ExternalCounts(added, overridden, scanned, rejected);
    }

    private static <T> LoadOutcome loadExternalFile(
            Path file,
            Path directory,
            String typeName,
            Decoder<T> decoder,
            Function<T, String> idResolver,
            Map<String, T> destination,
            Logger logger
    ) {
        String source = "external:" + directory.relativize(file).toString().replace('\\', '/');
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (!element.isJsonObject()) {
                logger.warn("[PhantasmBriefing] Skipping non-object external {} json: {}", typeName, file);
                return LoadOutcome.SKIPPED;
            }

            T value = decoder.decode(source, element.getAsJsonObject());
            String id = idResolver.apply(value);
            T previous = destination.put(id, value);
            if (previous == null) {
                logger.info("[PhantasmBriefing] Loaded external {} '{}' from {}", typeName, id, file);
                return LoadOutcome.ADDED;
            }

            logger.info("[PhantasmBriefing] External {} '{}' overrides bundled data from {}", typeName, id, file);
            return LoadOutcome.OVERRIDDEN;
        } catch (IOException | RuntimeException exception) {
            logger.error("[PhantasmBriefing] Failed to parse external {} {}: {}", typeName, file, exception.getMessage());
            return LoadOutcome.SKIPPED;
        }
    }

    static boolean isJsonFile(Path file) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json");
    }

    @FunctionalInterface
    interface Decoder<T> {
        T decode(String source, JsonObject json);
    }

    record Result<T>(Map<String, T> values, int datapackCount, int externalAdded, int externalOverrides) {
    }

    private record ExternalCounts(int added, int overridden, int scanned, int rejected) {
        private static final ExternalCounts EMPTY = new ExternalCounts(0, 0, 0, 0);
    }

    private enum LoadOutcome {
        ADDED,
        OVERRIDDEN,
        SKIPPED
    }
}
