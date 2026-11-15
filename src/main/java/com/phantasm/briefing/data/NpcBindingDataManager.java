package com.phantasm.briefing.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

public final class NpcBindingDataManager extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = JsonRegistryLoader.GSON;
    private static final String DATA_DIRECTORY = "phantasm_npc_bindings";
    private static final Path EXTERNAL_BINDING_DIRECTORY = JsonRegistryLoader.externalDirectory(DATA_DIRECTORY);

    private static final NpcBindingDataManager INSTANCE = new NpcBindingDataManager();

    private volatile List<NpcBindingSpec> bindings = List.of();
    private volatile long reloadRevision;
    private volatile String structureSpawnFingerprint = "";

    private NpcBindingDataManager() {
        super(GSON, DATA_DIRECTORY);
    }

    public static NpcBindingDataManager getInstance() {
        return INSTANCE;
    }

    public Optional<NpcBindingSpec> findBinding(Entity entity) {
        return this.bindings.stream().filter(binding -> binding.matches(entity)).findFirst();
    }

    public Optional<NpcBindingSpec> getBinding(String bindingId) {
        if (bindingId == null || bindingId.isBlank()) {
            return Optional.empty();
        }
        String normalizedId = bindingId.trim();
        Optional<NpcBindingSpec> exactMatch = this.bindings.stream()
                .filter(binding -> binding.bindingId().equals(normalizedId))
                .findFirst();
        if (exactMatch.isPresent() || normalizedId.contains(":")) {
            return exactMatch;
        }

        List<NpcBindingSpec> shortMatches = this.bindings.stream()
                .filter(binding -> resourcePath(binding.bindingId()).equals(normalizedId))
                .toList();
        return shortMatches.size() == 1 ? Optional.of(shortMatches.get(0)) : Optional.empty();
    }

    public List<String> getBindingIds() {
        return this.bindings.stream().map(NpcBindingSpec::bindingId).sorted().toList();
    }

    public List<NpcBindingSpec> getStructureSpawnBindings() {
        return this.bindings.stream().filter(binding -> !binding.spawnRules().isEmpty()).toList();
    }

    public long getReloadRevision() {
        return this.reloadRevision;
    }

    public String getStructureSpawnFingerprint() {
        return this.structureSpawnFingerprint;
    }

    @Override
    protected void apply(
            @Nonnull Map<ResourceLocation, JsonElement> jsonMap,
            @Nonnull ResourceManager resourceManager,
            @Nonnull ProfilerFiller profilerFiller
    ) {
        List<NpcBindingSpec> loadedBindings = new ArrayList<>();
        int datapackCount = 0;

        for (Map.Entry<ResourceLocation, JsonElement> entry : jsonMap.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                LOGGER.warn("[PhantasmBriefing] Skipping non-object npc binding json: {}", entry.getKey());
                continue;
            }

            try {
                loadedBindings.add(NpcBindingSpec.fromJson(entry.getKey().toString(), entry.getValue().getAsJsonObject()));
                datapackCount++;
            } catch (RuntimeException exception) {
                LOGGER.error("[PhantasmBriefing] Failed to parse npc binding {}: {}", entry.getKey(), exception.getMessage());
            }
        }

        int externalCount = loadExternalBindings(loadedBindings);
        this.bindings = List.copyOf(loadedBindings);
        this.structureSpawnFingerprint = createStructureSpawnFingerprint(this.bindings);
        this.reloadRevision++;
        LOGGER.info(
                "[PhantasmBriefing] Loaded {} npc binding rules (datapack={}, external={})",
                this.bindings.size(),
                datapackCount,
                externalCount
        );
    }

    private static int loadExternalBindings(List<NpcBindingSpec> loadedBindings) {
        try {
            Files.createDirectories(EXTERNAL_BINDING_DIRECTORY);
        } catch (IOException exception) {
            LOGGER.error("[PhantasmBriefing] Failed to prepare external npc binding directory {}: {}", EXTERNAL_BINDING_DIRECTORY, exception.getMessage());
            return 0;
        }

        int externalCount = 0;
        try (Stream<Path> stream = Files.walk(EXTERNAL_BINDING_DIRECTORY)) {
            for (Path filePath : stream.filter(Files::isRegularFile).filter(JsonRegistryLoader::isJsonFile).sorted().toList()) {
                if (loadExternalBinding(filePath, loadedBindings)) {
                    externalCount++;
                }
            }
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("[PhantasmBriefing] Failed to scan external npc binding directory {}: {}", EXTERNAL_BINDING_DIRECTORY, exception.getMessage());
        }
        return externalCount;
    }

    private static boolean loadExternalBinding(Path filePath, List<NpcBindingSpec> loadedBindings) {
        String sourceDescription = "external:" + EXTERNAL_BINDING_DIRECTORY.relativize(filePath).toString().replace('\\', '/');
        try (Reader reader = Files.newBufferedReader(filePath)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (!element.isJsonObject()) {
                LOGGER.warn("[PhantasmBriefing] Skipping non-object external npc binding json: {}", filePath);
                return false;
            }

            loadedBindings.add(NpcBindingSpec.fromJson(sourceDescription, element.getAsJsonObject()));
            LOGGER.info("[PhantasmBriefing] Loaded external npc binding from {}", filePath);
            return true;
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("[PhantasmBriefing] Failed to parse external npc binding {}: {}", filePath, exception.getMessage());
            return false;
        }
    }

    private static String createStructureSpawnFingerprint(List<NpcBindingSpec> bindings) {
        List<String> entries = bindings.stream()
                .filter(binding -> !binding.spawnRules().isEmpty())
                .flatMap(binding -> binding.spawnRules().stream().map(rule ->
                        binding.bindingId() + "|"
                                + binding.entityType() + "|"
                                + binding.questNodeId() + "|"
                                + binding.profession() + "|"
                                + binding.customName() + "|"
                                + rule
                ))
                .sorted()
                .toList();
        return UUID.nameUUIDFromBytes(
                String.join("\n", entries).getBytes(StandardCharsets.UTF_8)
        ).toString();
    }

    private static String resourcePath(String id) {
        int separator = id.indexOf(':');
        return separator >= 0 ? id.substring(separator + 1) : id;
    }
}
