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
import java.util.LinkedHashSet;
import java.util.List;
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
    private volatile Map<String, List<QuestSpec>> questsByTargetNode = Map.of();
    private volatile Map<String, List<QuestSpec>> questsByTargetNpc = Map.of();

    private QuestDataManager() {
        super(GSON, DATA_DIRECTORY);
    }

    public static QuestDataManager getInstance() {
        return INSTANCE;
    }

    public Optional<QuestSpec> getQuest(String questId) {
        return JsonRegistryLoader.findWithLocalAlias(this.questsById, questId);
    }

    public Collection<QuestSpec> getAllQuests() {
        return this.questsById.values();
    }

    public List<QuestSpec> getQuestsTargetingNode(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return List.of();
        }
        return this.questsByTargetNode.getOrDefault(nodeId.trim(), List.of());
    }

    public List<QuestSpec> getQuestsTargetingNpc(String npcIdentity) {
        if (npcIdentity == null || npcIdentity.isBlank()) {
            return List.of();
        }
        return this.questsByTargetNpc.getOrDefault(npcIdentity.trim(), List.of());
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
        com.phantasm.briefing.service.MobKillClassifier.reloadOverrides();
        this.questsById = result.values();
        this.questsByTargetNode = buildTargetNodeIndex(this.questsById.values());
        this.questsByTargetNpc = buildTargetNpcIndex(this.questsById.values());
        LOGGER.info(
                "[PhantasmBriefing] Loaded {} quests (datapack={}, externalAdded={}, externalOverrides={})",
                this.questsById.size(),
                result.datapackCount(),
                result.externalAdded(),
                result.externalOverrides()
        );
    }

    private static Map<String, List<QuestSpec>> buildTargetNodeIndex(Collection<QuestSpec> quests) {
        Map<String, LinkedHashSet<QuestSpec>> mutable = new LinkedHashMap<>();
        for (QuestSpec quest : quests) {
            for (QuestPhaseSpec phase : quest.phases()) {
                addQuestForNodes(mutable, phase.targetNodeIds(), quest);
                for (QuestObjectiveSpec objective : phase.objectives()) {
                    addQuestForNodes(mutable, objective.targetNodeIds(), quest);
                }
            }
        }

        Map<String, List<QuestSpec>> result = new LinkedHashMap<>();
        mutable.forEach((nodeId, nodeQuests) -> result.put(nodeId, List.copyOf(nodeQuests)));
        return Map.copyOf(result);
    }

    private static Map<String, List<QuestSpec>> buildTargetNpcIndex(Collection<QuestSpec> quests) {
        Map<String, LinkedHashSet<QuestSpec>> mutable = new LinkedHashMap<>();
        for (QuestSpec quest : quests) {
            for (QuestPhaseSpec phase : quest.phases()) {
                for (QuestObjectiveSpec objective : phase.objectives()) {
                    addQuestForNpc(mutable, objective.hintNpcId(), quest);
                    if (objective.objectiveType() == QuestObjectiveType.INTERACT) {
                        addQuestForNpc(mutable, objective.targetId(), quest);
                    }
                }
            }
        }

        Map<String, List<QuestSpec>> result = new LinkedHashMap<>();
        mutable.forEach((npcId, npcQuests) -> result.put(npcId, List.copyOf(npcQuests)));
        return Map.copyOf(result);
    }

    private static void addQuestForNpc(
            Map<String, LinkedHashSet<QuestSpec>> index,
            String npcId,
            QuestSpec quest
    ) {
        if (npcId != null && !npcId.isBlank()) {
            index.computeIfAbsent(npcId.trim(), ignored -> new LinkedHashSet<>()).add(quest);
        }
    }

    private static void addQuestForNodes(
            Map<String, LinkedHashSet<QuestSpec>> index,
            List<String> nodeIds,
            QuestSpec quest
    ) {
        for (String nodeId : nodeIds) {
            if (!nodeId.isBlank()) {
                index.computeIfAbsent(nodeId, ignored -> new LinkedHashSet<>()).add(quest);
            }
        }
    }
}
