package com.phantasm.briefing.service;

import com.phantasm.briefing.data.QuestMarkerSpec;
import com.phantasm.briefing.data.QuestObjectiveSpec;
import com.phantasm.briefing.data.QuestPhaseSpec;
import com.phantasm.briefing.data.QuestSpec;
import com.phantasm.briefing.entity.StaticQuestNPCEntity;
import com.phantasm.briefing.registry.ModEntityTypes;
import com.phantasm.briefing.util.QuestNpcHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;


import static com.phantasm.briefing.data.JsonDataUtil.sanitize;

public final class StructureSearchCompatService {
    private StructureSearchCompatService() {
    }

    public static int reportLocatedStructure(ServerPlayer player, String structureId, QuestMarkerSpec marker) {
        String normalizedStructureId = normalizeStructureId(structureId);
        if (player == null || normalizedStructureId.isBlank() || marker == null) {
            return 0;
        }

        int changed = 0;
        for (String questId : BriefingPlayerData.activeQuestIds(player)) {
            QuestSpec quest = com.phantasm.briefing.data.QuestDataManager.getInstance().getQuest(questId).orElse(null);
            if (quest == null) {
                continue;
            }
            for (QuestObjectiveSpec objective : QuestRuntimeService.activeObjectives(player, quest)) {
                if (!matchesStructureObjective(objective, normalizedStructureId)) {
                    continue;
                }
                BriefingPlayerData.setRuntimeMarker(
                        player,
                        quest.questId(),
                        objective.objectiveId(),
                        normalizeMarkerForObjective(objective, marker)
                );
                changed++;
            }
        }

        if (changed > 0) {
            QuestRuntimeService.evaluateAutomaticObjectives(player);
            QuestTrackerService.syncToClient(player);
            QuestEntityHintService.requestSync(player);
        }
        return changed;
    }

    public static boolean matchesStructureObjective(QuestObjectiveSpec objective, String structureId) {
        return objective != null
                && objective.usesStructureSearch()
                && normalizeStructureId(objective.structureId()).equals(normalizeStructureId(structureId));
    }

    public static QuestMarkerSpec resolveMarker(ServerPlayer player, QuestSpec quest, QuestObjectiveSpec objective) {
        if (objective != null) {
            QuestMarkerSpec runtimeMarker = BriefingPlayerData.runtimeMarker(player, quest.questId(), objective.objectiveId());
            if (runtimeMarker != null) {
                return runtimeMarker;
            }
            if (objective.marker() != null) {
                return objective.marker();
            }
            if (objective.usesStructureSearch()) {
                return null;
            }
        }
        return quest == null ? null : quest.marker();
    }

    public static boolean hasResolvedMarker(ServerPlayer player, QuestSpec quest, QuestObjectiveSpec objective) {
        return resolveMarker(player, quest, objective) != null;
    }

    public static boolean spawnResolvedStructureNpc(ServerPlayer player, String questId, String objectiveId, String overrideQuestNodeId) {
        QuestSpec quest = com.phantasm.briefing.data.QuestDataManager.getInstance().getQuest(sanitize(questId)).orElse(null);
        QuestPhaseSpec phase = QuestRuntimeService.activePhaseForObjective(player, quest, objectiveId).orElse(null);
        QuestObjectiveSpec objective = phase == null ? null : phase.objective(objectiveId);
        if (quest == null || objective == null || !QuestRuntimeService.isObjectiveActive(player, quest, objective)) {
            return false;
        }

        QuestMarkerSpec marker = BriefingPlayerData.runtimeMarker(player, quest.questId(), objective.objectiveId());
        if (marker == null) {
            return false;
        }

        String questNodeId = sanitize(overrideQuestNodeId);
        if (questNodeId.isBlank()) {
            questNodeId = sanitize(objective.structureNodeId());
        }
        if (questNodeId.isBlank()) {
            return false;
        }

        ServerLevel level = resolveLevel(player, marker.dimension());
        if (level == null) {
            return false;
        }
        if (hasNearbyStructureNpc(level, marker, questNodeId)) {
            return true;
        }

        StaticQuestNPCEntity npc = ModEntityTypes.STATIC_QUEST_NPC.get().create(level);
        if (npc == null) {
            return false;
        }

        npc.setQuestNodeId(questNodeId);
        npc.moveTo(marker.x(), marker.y(), marker.z(), 0.0F, 0.0F);
        npc.setYHeadRot(0.0F);
        npc.setYBodyRot(0.0F);
        return level.addFreshEntity(npc);
    }

    public static int spawnResolvedStructureNpcsForStructure(ServerPlayer player, String structureId, String overrideQuestNodeId) {
        String normalizedStructureId = normalizeStructureId(structureId);
        if (player == null || normalizedStructureId.isBlank()) {
            return 0;
        }

        int spawned = 0;
        for (String questId : BriefingPlayerData.activeQuestIds(player)) {
            QuestSpec quest = com.phantasm.briefing.data.QuestDataManager.getInstance().getQuest(questId).orElse(null);
            if (quest == null) {
                continue;
            }
            for (QuestObjectiveSpec objective : QuestRuntimeService.activeObjectives(player, quest)) {
                if (!matchesStructureObjective(objective, normalizedStructureId)) {
                    continue;
                }
                String questNodeId = sanitize(overrideQuestNodeId);
                if (questNodeId.isBlank()) {
                    questNodeId = sanitize(objective.structureNodeId());
                }
                if (questNodeId.isBlank()) {
                    continue;
                }
                if (spawnResolvedStructureNpc(player, quest.questId(), objective.objectiveId(), questNodeId)) {
                    spawned++;
                }
            }
        }
        return spawned;
    }

    public static void clearQuestRuntimeData(ServerPlayer player, String questId) {
        BriefingPlayerData.clearRuntimeMarkers(player, questId);
    }

    public static String normalizeStructureId(String rawStructureId) {
        String normalized = sanitize(rawStructureId);
        if (normalized.isBlank()) {
            return "";
        }
        if (normalized.startsWith("structure.")) {
            String translationKey = normalized.substring("structure.".length());
            int split = translationKey.indexOf('.');
            if (split > 0 && split < translationKey.length() - 1) {
                return translationKey.substring(0, split) + ":" + translationKey.substring(split + 1);
            }
        }
        if (!normalized.contains(":")) {
            return "minecraft:" + normalized;
        }
        return normalized;
    }

    private static QuestMarkerSpec normalizeMarkerForObjective(QuestObjectiveSpec objective, QuestMarkerSpec marker) {
        String label = sanitize(marker.label());
        if (label.isBlank()) {
            label = objective.resolvedStructureLabel();
        }
        return new QuestMarkerSpec(
                label,
                sanitize(marker.dimension()),
                marker.x(),
                marker.y(),
                marker.z()
        );
    }

    private static boolean hasNearbyStructureNpc(ServerLevel level, QuestMarkerSpec marker, String questNodeId) {
        AABB searchBox = new AABB(
                marker.x() - 1.5D,
                marker.y() - 2.0D,
                marker.z() - 1.5D,
                marker.x() + 1.5D,
                marker.y() + 2.0D,
                marker.z() + 1.5D
        );
        return !level.getEntitiesOfClass(StaticQuestNPCEntity.class, searchBox, entity ->
                questNodeId.equals(QuestNpcHelper.resolveQuestNodeId(entity))
        ).isEmpty();
    }

    private static ServerLevel resolveLevel(ServerPlayer player, String dimensionId) {
        ResourceLocation dimensionKey = ResourceLocation.tryParse(sanitize(dimensionId));
        if (dimensionKey == null) {
            return null;
        }
        for (ServerLevel level : player.getServer().getAllLevels()) {
            if (level.dimension().location().equals(dimensionKey)) {
                return level;
            }
        }
        return null;
    }

}
