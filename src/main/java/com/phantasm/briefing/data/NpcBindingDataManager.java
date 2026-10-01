package com.phantasm.briefing.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private volatile Map<String, List<NpcBindingSpec>> bindingsByEntityType = Map.of();
    private volatile Map<String, NpcBindingSpec> bindingsById = Map.of();
    private volatile Map<String, NpcBindingSpec> bindingsByUniqueShortId = Map.of();
    private volatile List<NpcBindingSpec> structureSpawnBindings = List.of();
    private volatile Map<String, List<NpcBindingSpec>> structureBindingsByStructureId = Map.of();
    private volatile Set<String> globalStructureIds = Set.of();
    private volatile Map<String, Set<String>> structureIdsByDimension = Map.of();
    private volatile long reloadRevision;
    private volatile String structureSpawnFingerprint = "";

    private NpcBindingDataManager() {
        super(GSON, DATA_DIRECTORY);
    }

    public static NpcBindingDataManager getInstance() {
        return INSTANCE;
    }

    public Optional<NpcBindingSpec> findBinding(Entity entity) {
        ResourceLocation entityTypeKey = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (entityTypeKey == null) {
            return Optional.empty();
        }
        List<NpcBindingSpec> candidates = this.bindingsByEntityType.get(entityTypeKey.toString());
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        return candidates.stream().filter(binding -> binding.matches(entity)).findFirst();
    }

    public boolean mayMatchEntityType(Entity entity) {
        ResourceLocation entityTypeKey = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return entityTypeKey != null && this.bindingsByEntityType.containsKey(entityTypeKey.toString());
    }

    public Optional<NpcBindingSpec> getBinding(String bindingId) {
        if (bindingId == null || bindingId.isBlank()) {
            return Optional.empty();
        }
        String normalizedId = bindingId.trim();
        NpcBindingSpec exact = this.bindingsById.get(normalizedId);
        if (exact != null || normalizedId.contains(":")) {
            return Optional.ofNullable(exact);
        }
        return Optional.ofNullable(this.bindingsByUniqueShortId.get(normalizedId));
    }

    public List<String> getBindingIds() {
        return this.bindings.stream().map(NpcBindingSpec::bindingId).sorted().toList();
    }

    public List<NpcBindingSpec> getStructureSpawnBindings() {
        return this.structureSpawnBindings;
    }

    public List<NpcBindingSpec> getStructureSpawnBindings(String structureId) {
        if (structureId == null || structureId.isBlank()) {
            return List.of();
        }
        return this.structureBindingsByStructureId.getOrDefault(structureId.trim(), List.of());
    }

    public Set<String> getConfiguredStructureIds(String dimensionId) {
        LinkedHashSet<String> result = new LinkedHashSet<>(this.globalStructureIds);
        Set<String> dimensionSpecific = this.structureIdsByDimension.get(dimensionId == null ? "" : dimensionId.trim());
        if (dimensionSpecific != null) {
            result.addAll(dimensionSpecific);
        }
        return Set.copyOf(result);
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
        List<NpcBindingSpec> immutableBindings = List.copyOf(loadedBindings);
        BindingIndexes indexes = buildIndexes(immutableBindings);
        this.bindings = immutableBindings;
        this.bindingsByEntityType = indexes.bindingsByEntityType();
        this.bindingsById = indexes.bindingsById();
        this.bindingsByUniqueShortId = indexes.bindingsByUniqueShortId();
        this.structureSpawnBindings = indexes.structureSpawnBindings();
        this.structureBindingsByStructureId = indexes.structureBindingsByStructureId();
        this.globalStructureIds = indexes.globalStructureIds();
        this.structureIdsByDimension = indexes.structureIdsByDimension();
        this.structureSpawnFingerprint = createStructureSpawnFingerprint(immutableBindings);
        this.reloadRevision++;
        LOGGER.info(
                "[PhantasmBriefing] Loaded {} npc binding rules (datapack={}, external={})",
                this.bindings.size(),
                datapackCount,
                externalCount
        );
    }

    private static BindingIndexes buildIndexes(List<NpcBindingSpec> bindings) {
        Map<String, List<NpcBindingSpec>> byEntityType = new LinkedHashMap<>();
        Map<String, NpcBindingSpec> byId = new LinkedHashMap<>();
        Map<String, List<NpcBindingSpec>> byShortId = new LinkedHashMap<>();
        List<NpcBindingSpec> structureBindings = new ArrayList<>();
        Map<String, LinkedHashSet<NpcBindingSpec>> structureBindingsById = new LinkedHashMap<>();
        LinkedHashSet<String> globalStructureIds = new LinkedHashSet<>();
        Map<String, LinkedHashSet<String>> structureIdsByDimension = new LinkedHashMap<>();

        for (NpcBindingSpec binding : bindings) {
            byEntityType.computeIfAbsent(binding.entityType(), ignored -> new ArrayList<>()).add(binding);
            byId.putIfAbsent(binding.bindingId(), binding);
            byShortId.computeIfAbsent(resourcePath(binding.bindingId()), ignored -> new ArrayList<>()).add(binding);

            if (!binding.spawnRules().isEmpty()) {
                structureBindings.add(binding);
            }
            for (NpcStructureSpawnSpec rule : binding.spawnRules()) {
                structureBindingsById.computeIfAbsent(rule.structureId(), ignored -> new LinkedHashSet<>()).add(binding);
                if (rule.dimension().isBlank()) {
                    globalStructureIds.add(rule.structureId());
                } else {
                    structureIdsByDimension
                            .computeIfAbsent(rule.dimension(), ignored -> new LinkedHashSet<>())
                            .add(rule.structureId());
                }
            }
        }

        Map<String, List<NpcBindingSpec>> immutableByEntityType = new LinkedHashMap<>();
        byEntityType.forEach((key, value) -> immutableByEntityType.put(key, List.copyOf(value)));

        Map<String, NpcBindingSpec> uniqueShortIds = new LinkedHashMap<>();
        byShortId.forEach((key, value) -> {
            if (value.size() == 1) {
                uniqueShortIds.put(key, value.get(0));
            }
        });

        Map<String, List<NpcBindingSpec>> immutableStructureBindings = new LinkedHashMap<>();
        structureBindingsById.forEach((key, value) -> immutableStructureBindings.put(key, List.copyOf(value)));

        Map<String, Set<String>> immutableStructureIdsByDimension = new LinkedHashMap<>();
        structureIdsByDimension.forEach((key, value) -> immutableStructureIdsByDimension.put(key, Set.copyOf(value)));

        return new BindingIndexes(
                Map.copyOf(immutableByEntityType),
                Map.copyOf(byId),
                Map.copyOf(uniqueShortIds),
                List.copyOf(structureBindings),
                Map.copyOf(immutableStructureBindings),
                Set.copyOf(globalStructureIds),
                Map.copyOf(immutableStructureIdsByDimension)
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

    private record BindingIndexes(
            Map<String, List<NpcBindingSpec>> bindingsByEntityType,
            Map<String, NpcBindingSpec> bindingsById,
            Map<String, NpcBindingSpec> bindingsByUniqueShortId,
            List<NpcBindingSpec> structureSpawnBindings,
            Map<String, List<NpcBindingSpec>> structureBindingsByStructureId,
            Set<String> globalStructureIds,
            Map<String, Set<String>> structureIdsByDimension
    ) {
    }
}
