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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;

/** Loads only the current multi-node dialogue format. Old files are converted before reload. */
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
        return JsonRegistryLoader.findWithLocalAlias(this.dialoguesById, dialogueId);
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
        JsonRegistryLoader.Result<DialogueSpec> result = JsonRegistryLoader.load(
                jsonMap,
                EXTERNAL_DIALOGUE_DIRECTORY,
                "dialogue",
                DialogueSpec::fromJson,
                DialogueSpec::dialogueId,
                LOGGER
        );
        LinkedHashMap<String, DialogueNode> nodes = new LinkedHashMap<>();
        for (DialogueSpec dialogue : result.values().values()) {
            for (Map.Entry<String, DialogueNode> entry : dialogue.flattenNodes().entrySet()) {
                DialogueNode previous = nodes.put(entry.getKey(), entry.getValue());
                if (previous != null) {
                    LOGGER.warn("[PhantasmBriefing] Duplicate scoped dialogue node '{}'", entry.getKey());
                }
            }
        }
        this.dialoguesById = result.values();
        this.nodesById = Map.copyOf(nodes);
        LOGGER.info(
                "[PhantasmBriefing] Loaded {} current dialogue specs / {} scoped nodes (datapack={}, externalAdded={}, externalOverrides={})",
                this.dialoguesById.size(), this.nodesById.size(), result.datapackCount(), result.externalAdded(), result.externalOverrides()
        );
    }
}
