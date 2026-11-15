package com.phantasm.briefing.service;

import com.phantasm.briefing.PhantasmBriefing;
import com.phantasm.briefing.data.QuestMarkerSpec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

public final class BriefingPlayerData {
    private static final String ROOT = PhantasmBriefing.MOD_ID;
    private static final String FLAGS = "flags";
    private static final String QUESTS = "quests";
    private static final String COMPLETED_OBJECTIVES = "completedObjectives";
    private static final String OBJECTIVE_PROGRESS = "objectiveProgress";
    private static final String RUNTIME_MARKERS = "runtimeMarkers";
    private static final String TRACKED_QUESTS = "trackedQuests";
    private static final String TRACKER_HUD_ENABLED = "trackerHudEnabled";
    private static final String COMPLETED_NPC_STRUCTURES = "completedNpcStructures";
    private static final String NPC_STRUCTURE_ID = "id";
    private static final String NPC_STRUCTURE_DIMENSION = "dimension";
    private static final String NPC_STRUCTURE_FINGERPRINT = "fingerprint";
    private static final String NPC_STRUCTURE_MIN_CHUNK_X = "minChunkX";
    private static final String NPC_STRUCTURE_MAX_CHUNK_X = "maxChunkX";
    private static final String NPC_STRUCTURE_MIN_CHUNK_Z = "minChunkZ";
    private static final String NPC_STRUCTURE_MAX_CHUNK_Z = "maxChunkZ";

    private BriefingPlayerData() {
    }

    public static CompoundTag root(Player player) {
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(ROOT, Tag.TAG_COMPOUND)) {
            persistent.put(ROOT, new CompoundTag());
        }
        return persistent.getCompound(ROOT);
    }

    public static CompoundTag flags(Player player) {
        CompoundTag root = root(player);
        if (!root.contains(FLAGS, Tag.TAG_COMPOUND)) {
            root.put(FLAGS, new CompoundTag());
        }
        return root.getCompound(FLAGS);
    }

    public static boolean hasFlag(Player player, String flag) {
        return !flag.isBlank() && flags(player).getBoolean(flag);
    }

    public static void setFlag(Player player, String flag) {
        if (flag != null && !flag.isBlank()) {
            flags(player).putBoolean(flag.trim(), true);
        }
    }

    public static CompoundTag quests(Player player) {
        CompoundTag root = root(player);
        if (!root.contains(QUESTS, Tag.TAG_COMPOUND)) {
            root.put(QUESTS, new CompoundTag());
        }
        return root.getCompound(QUESTS);
    }

    public static CompoundTag quest(Player player, String questId) {
        CompoundTag quests = quests(player);
        if (!quests.contains(questId, Tag.TAG_COMPOUND)) {
            quests.put(questId, new CompoundTag());
        }
        return quests.getCompound(questId);
    }

    public static boolean isQuestActive(Player player, String questId) {
        return "active".equals(quest(player, questId).getString("status"));
    }

    public static boolean isQuestCompleted(Player player, String questId) {
        return "completed".equals(quest(player, questId).getString("status"));
    }

    public static String phase(Player player, String questId) {
        return quest(player, questId).getString("phase");
    }

    public static void setPhase(Player player, String questId, String phaseId) {
        if (questId == null || questId.isBlank()) {
            return;
        }
        quest(player, questId).putString("phase", phaseId == null ? "" : phaseId.trim());
    }

    public static List<String> completedObjectiveIds(Player player, String questId) {
        ListTag completed = quest(player, questId).getList(COMPLETED_OBJECTIVES, Tag.TAG_STRING);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < completed.size(); i++) {
            String objectiveId = completed.getString(i);
            if (!objectiveId.isBlank() && !result.contains(objectiveId)) {
                result.add(objectiveId);
            }
        }
        return result;
    }

    public static boolean isObjectiveCompleted(Player player, String questId, String objectiveId) {
        return !objectiveId.isBlank() && completedObjectiveIds(player, questId).contains(objectiveId);
    }

    public static void completeObjective(Player player, String questId, String objectiveId) {
        if (questId == null || questId.isBlank() || objectiveId == null || objectiveId.isBlank()) {
            return;
        }

        List<String> completed = new ArrayList<>(completedObjectiveIds(player, questId));
        if (!completed.contains(objectiveId.trim())) {
            completed.add(objectiveId.trim());
        }
        setCompletedObjectiveIds(player, questId, completed);
    }

    public static void clearCompletedObjectives(Player player, String questId) {
        if (questId == null || questId.isBlank()) {
            return;
        }
        quest(player, questId).remove(COMPLETED_OBJECTIVES);
    }

    public static int objectiveProgress(Player player, String questId, String objectiveId) {
        if (questId == null || questId.isBlank() || objectiveId == null || objectiveId.isBlank()) {
            return 0;
        }
        return objectiveProgressTag(player, questId).getInt(objectiveId.trim());
    }

    public static void setObjectiveProgress(Player player, String questId, String objectiveId, int progress) {
        if (questId == null || questId.isBlank() || objectiveId == null || objectiveId.isBlank()) {
            return;
        }
        CompoundTag progressTag = objectiveProgressTag(player, questId);
        if (progress <= 0) {
            progressTag.remove(objectiveId.trim());
            if (progressTag.isEmpty()) {
                quest(player, questId).remove(OBJECTIVE_PROGRESS);
            }
            return;
        }
        progressTag.putInt(objectiveId.trim(), progress);
    }

    public static int incrementObjectiveProgress(Player player, String questId, String objectiveId, int delta) {
        int next = Math.max(0, objectiveProgress(player, questId, objectiveId) + Math.max(0, delta));
        setObjectiveProgress(player, questId, objectiveId, next);
        return next;
    }

    public static void clearObjectiveProgresses(Player player, String questId) {
        if (questId == null || questId.isBlank()) {
            return;
        }
        quest(player, questId).remove(OBJECTIVE_PROGRESS);
    }

    public static QuestMarkerSpec runtimeMarker(Player player, String questId, String objectiveId) {
        if (questId == null || questId.isBlank() || objectiveId == null || objectiveId.isBlank()) {
            return null;
        }
        CompoundTag markers = runtimeMarkersTag(player, questId, false);
        if (markers == null || !markers.contains(objectiveId.trim(), Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag markerTag = markers.getCompound(objectiveId.trim());
        return new QuestMarkerSpec(
                markerTag.getString("label"),
                markerTag.getString("dimension"),
                markerTag.getDouble("x"),
                markerTag.getDouble("y"),
                markerTag.getDouble("z")
        );
    }

    public static void setRuntimeMarker(Player player, String questId, String objectiveId, QuestMarkerSpec marker) {
        if (marker == null || questId == null || questId.isBlank() || objectiveId == null || objectiveId.isBlank()) {
            return;
        }
        CompoundTag markerTag = new CompoundTag();
        markerTag.putString("label", marker.label());
        markerTag.putString("dimension", marker.dimension());
        markerTag.putDouble("x", marker.x());
        markerTag.putDouble("y", marker.y());
        markerTag.putDouble("z", marker.z());
        runtimeMarkersTag(player, questId, true).put(objectiveId.trim(), markerTag);
    }

    public static void clearRuntimeMarker(Player player, String questId, String objectiveId) {
        if (questId == null || questId.isBlank() || objectiveId == null || objectiveId.isBlank()) {
            return;
        }
        CompoundTag markers = runtimeMarkersTag(player, questId, false);
        if (markers == null) {
            return;
        }
        markers.remove(objectiveId.trim());
        if (markers.isEmpty()) {
            quest(player, questId).remove(RUNTIME_MARKERS);
        }
    }

    public static void clearRuntimeMarkers(Player player, String questId) {
        if (questId == null || questId.isBlank()) {
            return;
        }
        quest(player, questId).remove(RUNTIME_MARKERS);
    }

    public static List<String> activeQuestIds(Player player) {
        CompoundTag quests = quests(player);
        List<String> activeQuestIds = new ArrayList<>();
        for (String questId : quests.getAllKeys()) {
            if ("active".equals(quests.getCompound(questId).getString("status"))) {
                activeQuestIds.add(questId);
            }
        }
        return activeQuestIds;
    }

    public static List<String> questIds(Player player) {
        return new ArrayList<>(quests(player).getAllKeys());
    }

    public static void removeQuest(Player player, String questId) {
        if (questId == null || questId.isBlank()) {
            return;
        }
        quests(player).remove(questId.trim());
    }

    private static void setCompletedObjectiveIds(Player player, String questId, List<String> objectiveIds) {
        ListTag completed = new ListTag();
        for (String objectiveId : objectiveIds.stream().distinct().toList()) {
            if (!objectiveId.isBlank()) {
                completed.add(StringTag.valueOf(objectiveId));
            }
        }
        quest(player, questId).put(COMPLETED_OBJECTIVES, completed);
    }

    private static CompoundTag objectiveProgressTag(Player player, String questId) {
        CompoundTag quest = quest(player, questId);
        if (!quest.contains(OBJECTIVE_PROGRESS, Tag.TAG_COMPOUND)) {
            quest.put(OBJECTIVE_PROGRESS, new CompoundTag());
        }
        return quest.getCompound(OBJECTIVE_PROGRESS);
    }

    private static CompoundTag runtimeMarkersTag(Player player, String questId, boolean create) {
        CompoundTag quest = quest(player, questId);
        if (!quest.contains(RUNTIME_MARKERS, Tag.TAG_COMPOUND)) {
            if (!create) {
                return null;
            }
            quest.put(RUNTIME_MARKERS, new CompoundTag());
        }
        return quest.getCompound(RUNTIME_MARKERS);
    }

    public static void setTrackedQuestIds(Player player, List<String> questIds) {
        ListTag tracked = new ListTag();
        for (String questId : questIds.stream().distinct().limit(2).toList()) {
            if (!questId.isBlank()) {
                tracked.add(StringTag.valueOf(questId));
            }
        }
        root(player).put(TRACKED_QUESTS, tracked);
    }

    public static List<String> trackedQuestIds(Player player) {
        ListTag tracked = root(player).getList(TRACKED_QUESTS, Tag.TAG_STRING);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < tracked.size(); i++) {
            String questId = tracked.getString(i);
            if (!questId.isBlank() && !result.contains(questId)) {
                result.add(questId);
            }
        }
        return result;
    }

    public static void untrackQuest(Player player, String questId) {
        List<String> tracked = new ArrayList<>(trackedQuestIds(player));
        if (tracked.remove(questId)) {
            setTrackedQuestIds(player, tracked);
        }
    }

    public static boolean isTrackerHudEnabled(Player player) {
        CompoundTag root = root(player);
        return !root.contains(TRACKER_HUD_ENABLED, Tag.TAG_BYTE) || root.getBoolean(TRACKER_HUD_ENABLED);
    }

    public static void setTrackerHudEnabled(Player player, boolean enabled) {
        root(player).putBoolean(TRACKER_HUD_ENABLED, enabled);
    }

    public static boolean hasCompletedNpcStructure(Player player, String completionId) {
        if (completionId == null || completionId.isBlank()) {
            return false;
        }
        ListTag completed = root(player).getList(COMPLETED_NPC_STRUCTURES, Tag.TAG_COMPOUND);
        for (int i = 0; i < completed.size(); i++) {
            if (completionId.equals(completed.getCompound(i).getString(NPC_STRUCTURE_ID))) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasCompletedNpcStructureAt(
            Player player,
            String dimension,
            int chunkX,
            int chunkZ,
            String fingerprint
    ) {
        ListTag completed = root(player).getList(COMPLETED_NPC_STRUCTURES, Tag.TAG_COMPOUND);
        for (int i = 0; i < completed.size(); i++) {
            CompoundTag entry = completed.getCompound(i);
            if (dimension.equals(entry.getString(NPC_STRUCTURE_DIMENSION))
                    && fingerprint.equals(entry.getString(NPC_STRUCTURE_FINGERPRINT))
                    && chunkX >= entry.getInt(NPC_STRUCTURE_MIN_CHUNK_X)
                    && chunkX <= entry.getInt(NPC_STRUCTURE_MAX_CHUNK_X)
                    && chunkZ >= entry.getInt(NPC_STRUCTURE_MIN_CHUNK_Z)
                    && chunkZ <= entry.getInt(NPC_STRUCTURE_MAX_CHUNK_Z)) {
                return true;
            }
        }
        return false;
    }

    public static void markNpcStructureCompleted(
            Player player,
            String completionId,
            String dimension,
            String fingerprint,
            int minChunkX,
            int maxChunkX,
            int minChunkZ,
            int maxChunkZ
    ) {
        if (completionId == null || completionId.isBlank()) {
            return;
        }
        ListTag completed = root(player).getList(COMPLETED_NPC_STRUCTURES, Tag.TAG_COMPOUND);
        for (int i = 0; i < completed.size(); i++) {
            CompoundTag existing = completed.getCompound(i);
            if (completionId.equals(existing.getString(NPC_STRUCTURE_ID))) {
                writeNpcStructureCompletion(
                        existing,
                        completionId,
                        dimension,
                        fingerprint,
                        minChunkX,
                        maxChunkX,
                        minChunkZ,
                        maxChunkZ
                );
                root(player).put(COMPLETED_NPC_STRUCTURES, completed);
                return;
            }
        }

        CompoundTag entry = new CompoundTag();
        writeNpcStructureCompletion(
                entry,
                completionId,
                dimension,
                fingerprint,
                minChunkX,
                maxChunkX,
                minChunkZ,
                maxChunkZ
        );
        completed.add(entry);
        root(player).put(COMPLETED_NPC_STRUCTURES, completed);
    }

    private static void writeNpcStructureCompletion(
            CompoundTag entry,
            String completionId,
            String dimension,
            String fingerprint,
            int minChunkX,
            int maxChunkX,
            int minChunkZ,
            int maxChunkZ
    ) {
        entry.putString(NPC_STRUCTURE_ID, completionId);
        entry.putString(NPC_STRUCTURE_DIMENSION, dimension);
        entry.putString(NPC_STRUCTURE_FINGERPRINT, fingerprint);
        entry.putInt(NPC_STRUCTURE_MIN_CHUNK_X, minChunkX);
        entry.putInt(NPC_STRUCTURE_MAX_CHUNK_X, maxChunkX);
        entry.putInt(NPC_STRUCTURE_MIN_CHUNK_Z, minChunkZ);
        entry.putInt(NPC_STRUCTURE_MAX_CHUNK_Z, maxChunkZ);
    }

    public static void copyOnClone(Player original, Player clone) {
        if (original.getPersistentData().contains(ROOT, Tag.TAG_COMPOUND)) {
            clone.getPersistentData().put(ROOT, original.getPersistentData().getCompound(ROOT).copy());
        }
    }
}
