package com.phantasm.briefing.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

public final class DialogueDataManager extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = JsonRegistryLoader.GSON;
    private static final String DATA_DIRECTORY = "phantasm_dialogues";
    private static final Path EXTERNAL_DIALOGUE_DIRECTORY = JsonRegistryLoader.externalDirectory(DATA_DIRECTORY);

    private static final DialogueDataManager INSTANCE = new DialogueDataManager();

    private volatile Map<String, DialogueNode> nodesById = Map.of();
    private volatile Map<String, DialogueSpec> dialoguesById = Map.of();

    private DialogueDataManager() {
        super(GSON, DATA_DIRECTORY);
    }

    public static DialogueDataManager getInstance() {
        return INSTANCE;
    }

    public Optional<DialogueNode> getNode(String nodeId) {
        return Optional.ofNullable(this.nodesById.get(nodeId));
    }

    public Optional<DialogueSpec> getDialogue(String dialogueId) {
        return Optional.ofNullable(this.dialoguesById.get(dialogueId));
    }

    public Collection<DialogueNode> getAllNodes() {
        return this.nodesById.values();
    }

    @Override
    protected void apply(
            @Nonnull Map<ResourceLocation, JsonElement> jsonMap,
            @Nonnull ResourceManager resourceManager,
            @Nonnull ProfilerFiller profilerFiller
    ) {
        Map<String, DialogueNode> loadedNodes = new LinkedHashMap<>();
        Map<String, DialogueSpec> loadedDialogues = new LinkedHashMap<>();
        int datapackCount = 0;

        for (Map.Entry<ResourceLocation, JsonElement> entry : jsonMap.entrySet()) {
            ResourceLocation sourceId = entry.getKey();
            JsonElement element = entry.getValue();

            if (!element.isJsonObject()) {
                LOGGER.warn("[PhantasmBriefing] Skipping non-object dialogue json: {}", sourceId);
                continue;
            }

            try {
                var object = element.getAsJsonObject();
                if (DialogueSpec.looksLikeDialogueSpec(object)) {
                    DialogueSpec dialogue = DialogueSpec.fromJson(sourceId.toString(), object);
                    boolean overridden = registerDialogueSpec(loadedNodes, loadedDialogues, dialogue);
                    if (overridden) {
                        LOGGER.warn("[PhantasmBriefing] Duplicate datapack dialogue id '{}' from {}", dialogue.dialogueId(), sourceId);
                    } else {
                        datapackCount++;
                    }
                    continue;
                }

                DialogueNode node = DialogueNode.fromJson(sourceId, object);
                DialogueNode previous = loadedNodes.put(node.nodeId(), node);
                if (previous != null) {
                    LOGGER.warn("[PhantasmBriefing] Duplicate datapack dialogue node id '{}' from {}", node.nodeId(), sourceId);
                } else {
                    datapackCount++;
                }
            } catch (RuntimeException exception) {
                LOGGER.error("[PhantasmBriefing] Failed to parse dialogue node {}: {}", sourceId, exception.getMessage());
            }
        }

        int overrideCount = loadExternalNodes(loadedNodes, loadedDialogues);
        int externalCount = countExternalOnlyNodes(loadedNodes.size(), datapackCount);

        this.nodesById = Map.copyOf(loadedNodes);
        this.dialoguesById = Map.copyOf(loadedDialogues);
        LOGGER.info(
                "[PhantasmBriefing] Loaded {} dialogue nodes and {} dialogue specs (datapack={}, externalAdded={}, externalOverrides={})",
                this.nodesById.size(),
                this.dialoguesById.size(),
                datapackCount,
                externalCount,
                overrideCount
        );
    }

    private static int loadExternalNodes(Map<String, DialogueNode> loadedNodes, Map<String, DialogueSpec> loadedDialogues) {
        try {
            Files.createDirectories(EXTERNAL_DIALOGUE_DIRECTORY);
        } catch (IOException exception) {
            LOGGER.error("[PhantasmBriefing] Failed to prepare external dialogue directory {}: {}", EXTERNAL_DIALOGUE_DIRECTORY, exception.getMessage());
            return 0;
        }

        int overrideCount = 0;
        try (Stream<Path> stream = Files.walk(EXTERNAL_DIALOGUE_DIRECTORY)) {
            for (Path filePath : stream.filter(Files::isRegularFile).filter(JsonRegistryLoader::isJsonFile).sorted().toList()) {
                if (loadExternalNode(filePath, loadedNodes, loadedDialogues)) {
                    overrideCount++;
                }
            }
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("[PhantasmBriefing] Failed to scan external dialogue directory {}: {}", EXTERNAL_DIALOGUE_DIRECTORY, exception.getMessage());
        }
        return overrideCount;
    }

    private static boolean loadExternalNode(Path filePath, Map<String, DialogueNode> loadedNodes, Map<String, DialogueSpec> loadedDialogues) {
        String sourceDescription = "external:" + EXTERNAL_DIALOGUE_DIRECTORY.relativize(filePath).toString().replace('\\', '/');
        try (Reader reader = Files.newBufferedReader(filePath)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (!element.isJsonObject()) {
                LOGGER.warn("[PhantasmBriefing] Skipping non-object external dialogue json: {}", filePath);
                return false;
            }

            var object = element.getAsJsonObject();
            if (DialogueSpec.looksLikeDialogueSpec(object)) {
                DialogueSpec dialogue = DialogueSpec.fromJson(sourceDescription, object);
                boolean overridden = registerDialogueSpec(loadedNodes, loadedDialogues, dialogue);
                if (overridden) {
                    LOGGER.info("[PhantasmBriefing] External dialogue '{}' overrides bundled data from {}", dialogue.dialogueId(), filePath);
                    return true;
                }

                LOGGER.info("[PhantasmBriefing] Loaded external dialogue '{}' from {}", dialogue.dialogueId(), filePath);
                return false;
            }

            DialogueNode node = DialogueNode.fromJson(sourceDescription, object);
            DialogueNode previous = loadedNodes.put(node.nodeId(), node);
            if (previous != null) {
                LOGGER.info("[PhantasmBriefing] External dialogue node '{}' overrides bundled data from {}", node.nodeId(), filePath);
                return true;
            }

            LOGGER.info("[PhantasmBriefing] Loaded external dialogue node '{}' from {}", node.nodeId(), filePath);
            return false;
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("[PhantasmBriefing] Failed to parse external dialogue node {}: {}", filePath, exception.getMessage());
            return false;
        }
    }

    private static boolean registerDialogueSpec(
            Map<String, DialogueNode> loadedNodes,
            Map<String, DialogueSpec> loadedDialogues,
            DialogueSpec dialogue
    ) {
        DialogueSpec previousDialogue = loadedDialogues.put(dialogue.dialogueId(), dialogue);
        if (previousDialogue != null) {
            String prefix = dialogue.dialogueId() + "::";
            loadedNodes.entrySet().removeIf(entry -> entry.getKey().startsWith(prefix));
        }
        loadedNodes.putAll(dialogue.flattenNodes());
        return previousDialogue != null;
    }

    private static int countExternalOnlyNodes(int totalCount, int datapackCount) {
        return Math.max(0, totalCount - datapackCount);
    }
}
