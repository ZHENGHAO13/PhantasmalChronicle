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
import java.util.Optional;
import javax.annotation.Nullable;

public final class BriefingPlayerData {
    private static final String ROOT = PhantasmBriefing.MOD_ID;
    private static final String FLAGS = "flags";
    private static final String QUESTS = "quests";
    private static final String COMPLETED_OBJECTIVES = "completedObjectives";
    private static final String COMPLETED_PHASES = "completedPhases";
    private static final String ACTIVATED_PHASES = "activatedPhases";
    private static final String OBJECTIVE_PROGRESS = "objectiveProgress";
    private static final String AUTO_OPENED_MANUAL_OBJECTIVES = "autoOpenedManualObjectives";
    private static final String UNLOCKED_MANUAL_LESSONS = "unlockedManualLessons";
    private static final String RUNTIME_MARKERS = "runtimeMarkers";
    private static final String TRACKED_QUESTS = "trackedQuests";
    private static final String RECOMMENDED_QUESTS = "recommendedQuests";
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

    @Nullable
    private static CompoundTag rootIfPresent(Player player) {
        CompoundTag persistent = player.getPersistentData();
        return persistent.contains(ROOT, Tag.TAG_COMPOUND) ? persistent.getCompound(ROOT) : null;
    }

    public static CompoundTag flags(Player player) {
        CompoundTag root = root(player);
        if (!root.contains(FLAGS, Tag.TAG_COMPOUND)) {
            root.put(FLAGS, new CompoundTag());
        }
        return root.getCompound(FLAGS);
    }

    public static boolean hasFlag(Player player, String flag) {
        if (flag == null || flag.isBlank()) {
            return false;
        }
        CompoundTag root = rootIfPresent(player);
        return root != null
                && root.contains(FLAGS, Tag.TAG_COMPOUND)
                && root.getCompound(FLAGS).getBoolean(flag.trim());
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
        String normalizedId = questId == null ? "" : questId.trim();
        if (!quests.contains(normalizedId, Tag.TAG_COMPOUND)) {
            quests.put(normalizedId, new CompoundTag());
        }
        return quests.getCompound(normalizedId);
    }

    @Nullable
    public static CompoundTag questIfPresent(Player player, String questId) {
        if (questId == null || questId.isBlank()) {
            return null;
        }
        CompoundTag root = rootIfPresent(player);
        if (root == null || !root.contains(QUESTS, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag quests = root.getCompound(QUESTS);
        String normalizedId = questId.trim();
        return quests.contains(normalizedId, Tag.TAG_COMPOUND) ? quests.getCompound(normalizedId) : null;
    }

    public static boolean isQuestActive(Player player, String questId) {
        CompoundTag state = questIfPresent(player, questId);
        return state != null && "active".equals(state.getString("status"));
    }

    public static boolean isQuestCompleted(Player player, String questId) {
        CompoundTag state = questIfPresent(player, questId);
        return state != null && "completed".equals(state.getString("status"));
    }

    public static String phase(Player player, String questId) {
        CompoundTag state = questIfPresent(player, questId);
        return state == null ? "" : state.getString("phase");
    }

    public static void setPhase(Player player, String questId, String phaseId) {
        if (questId == null || questId.isBlank()) {
            return;
        }
        quest(player, questId).putString("phase", phaseId == null ? "" : phaseId.trim());
    }

    public static List<String> completedObjectiveIds(Player player, String questId) {
        CompoundTag state = questIfPresent(player, questId);
        if (state == null) {
            return List.of();
        }
        ListTag completed = state.getList(COMPLETED_OBJECTIVES, Tag.TAG_STRING);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < completed.size(); i++) {
            String objectiveId = completed.getString(i);
            if (!objectiveId.isBlank() && !result.contains(objectiveId)) {
                result.add(objectiveId);
            }
        }
        return result;
    }

    public static boolean isObjectiveCompleted(Player player, String questId, String phaseId, String objectiveId) {
        if (phaseId == null || phaseId.isBlank() || objectiveId == null || objectiveId.isBlank()) {
            return false;
        }
        return completedObjectiveIds(player, questId).contains(objectiveStorageKey(phaseId, objectiveId));
    }

    public static void completeObjective(Player player, String questId, String phaseId, String objectiveId) {
        if (questId == null || questId.isBlank() || phaseId == null || phaseId.isBlank()
                || objectiveId == null || objectiveId.isBlank()) {
            return;
        }
        String key = objectiveStorageKey(phaseId, objectiveId);
        List<String> completed = new ArrayList<>(completedObjectiveIds(player, questId));
        if (!completed.contains(key)) {
            completed.add(key);
        }
        setCompletedObjectiveIds(player, questId, completed);
    }

    public static int objectiveProgress(Player player, String questId, String phaseId, String objectiveId) {
        if (phaseId == null || phaseId.isBlank() || objectiveId == null || objectiveId.isBlank()) {
            return 0;
        }
        return objectiveProgressByStorageKey(player, questId, objectiveStorageKey(phaseId, objectiveId));
    }

    public static void setObjectiveProgress(Player player, String questId, String phaseId, String objectiveId, int progress) {
        if (phaseId == null || phaseId.isBlank() || objectiveId == null || objectiveId.isBlank()) {
            return;
        }
        setObjectiveProgressByStorageKey(player, questId, objectiveStorageKey(phaseId, objectiveId), progress);
    }

    public static int incrementObjectiveProgress(Player player, String questId, String phaseId, String objectiveId, int delta) {
        int next = Math.max(0, objectiveProgress(player, questId, phaseId, objectiveId) + Math.max(0, delta));
        setObjectiveProgress(player, questId, phaseId, objectiveId, next);
        return next;
    }

    public static List<String> completedPhaseIds(Player player, String questId) {
        CompoundTag state = questIfPresent(player, questId);
        if (state == null) {
            return List.of();
        }
        ListTag completed = state.getList(COMPLETED_PHASES, Tag.TAG_STRING);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < completed.size(); i++) {
            String phaseId = completed.getString(i);
            if (!phaseId.isBlank() && !result.contains(phaseId)) {
                result.add(phaseId);
            }
        }
        return List.copyOf(result);
    }

    public static boolean isPhaseCompleted(Player player, String questId, String phaseId) {
        return phaseId != null && !phaseId.isBlank() && completedPhaseIds(player, questId).contains(phaseId.trim());
    }

    public static void completePhase(Player player, String questId, String phaseId) {
        if (questId == null || questId.isBlank() || phaseId == null || phaseId.isBlank()) {
            return;
        }
        List<String> completed = new ArrayList<>(completedPhaseIds(player, questId));
        String normalized = phaseId.trim();
        if (!completed.contains(normalized)) {
            completed.add(normalized);
        }
        CompoundTag state = quest(player, questId);
        ListTag tag = new ListTag();
        for (String id : completed) {
            tag.add(StringTag.valueOf(id));
        }
        state.put(COMPLETED_PHASES, tag);
    }


    public static List<String> activatedPhaseIds(Player player, String questId) {
        CompoundTag state = questIfPresent(player, questId);
        if (state == null) {
            return List.of();
        }
        ListTag activated = state.getList(ACTIVATED_PHASES, Tag.TAG_STRING);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < activated.size(); i++) {
            String phaseId = activated.getString(i);
            if (!phaseId.isBlank() && !result.contains(phaseId)) {
                result.add(phaseId);
            }
        }
        return List.copyOf(result);
    }

    public static boolean isPhaseActivated(Player player, String questId, String phaseId) {
        return phaseId != null && !phaseId.isBlank() && activatedPhaseIds(player, questId).contains(phaseId.trim());
    }

    public static boolean activatePhase(Player player, String questId, String phaseId) {
        if (questId == null || questId.isBlank() || phaseId == null || phaseId.isBlank()) {
            return false;
        }
        String normalized = phaseId.trim();
        List<String> activated = new ArrayList<>(activatedPhaseIds(player, questId));
        if (activated.contains(normalized)) {
            return false;
        }
        activated.add(normalized);
        ListTag tag = new ListTag();
        for (String id : activated) {
            tag.add(StringTag.valueOf(id));
        }
        quest(player, questId).put(ACTIVATED_PHASES, tag);
        return true;
    }

    public static void clearActivatedPhases(Player player, String questId) {
        CompoundTag state = questIfPresent(player, questId);
        if (state != null) {
            state.remove(ACTIVATED_PHASES);
        }
    }

    public static void clearCompletedPhases(Player player, String questId) {
        CompoundTag state = questIfPresent(player, questId);
        if (state != null) {
            state.remove(COMPLETED_PHASES);
        }
    }

    public static void clearCompletedObjectives(Player player, String questId) {
        if (questId == null || questId.isBlank()) {
            return;
        }
        CompoundTag state = questIfPresent(player, questId);
        if (state != null) {
            state.remove(COMPLETED_OBJECTIVES);
        }
    }

    private static int objectiveProgressByStorageKey(Player player, String questId, String storageKey) {
        if (questId == null || questId.isBlank() || storageKey == null || storageKey.isBlank()) {
            return 0;
        }
        CompoundTag progressTag = objectiveProgressTag(player, questId, false);
        return progressTag == null ? 0 : progressTag.getInt(storageKey.trim());
    }

    private static void setObjectiveProgressByStorageKey(Player player, String questId, String storageKey, int progress) {
        if (questId == null || questId.isBlank() || storageKey == null || storageKey.isBlank()) {
            return;
        }
        CompoundTag progressTag = objectiveProgressTag(player, questId, progress > 0);
        if (progressTag == null) {
            return;
        }
        if (progress <= 0) {
            progressTag.remove(storageKey.trim());
            if (progressTag.isEmpty()) {
                CompoundTag state = questIfPresent(player, questId);
                if (state != null) {
                    state.remove(OBJECTIVE_PROGRESS);
                }
            }
            return;
        }
        progressTag.putInt(storageKey.trim(), progress);
    }

    public static void clearObjectiveProgresses(Player player, String questId) {
        if (questId == null || questId.isBlank()) {
            return;
        }
        CompoundTag state = questIfPresent(player, questId);
        if (state != null) {
            state.remove(OBJECTIVE_PROGRESS);
        }
    }

    public static boolean hasAutoOpenedManualObjective(Player player, String questId, String phaseId, String objectiveId) {
        if (phaseId == null || phaseId.isBlank() || objectiveId == null || objectiveId.isBlank()) {
            return false;
        }
        CompoundTag state = questIfPresent(player, questId);
        if (state == null) {
            return false;
        }
        String key = objectiveStorageKey(phaseId, objectiveId);
        ListTag opened = state.getList(AUTO_OPENED_MANUAL_OBJECTIVES, Tag.TAG_STRING);
        for (int i = 0; i < opened.size(); i++) {
            if (key.equals(opened.getString(i))) {
                return true;
            }
        }
        return false;
    }

    public static void markAutoOpenedManualObjective(Player player, String questId, String phaseId, String objectiveId) {
        if (questId == null || questId.isBlank() || phaseId == null || phaseId.isBlank()
                || objectiveId == null || objectiveId.isBlank()) {
            return;
        }
        CompoundTag state = quest(player, questId);
        ListTag opened = state.getList(AUTO_OPENED_MANUAL_OBJECTIVES, Tag.TAG_STRING);
        String key = objectiveStorageKey(phaseId, objectiveId);
        for (int i = 0; i < opened.size(); i++) {
            if (key.equals(opened.getString(i))) {
                return;
            }
        }
        opened.add(StringTag.valueOf(key));
        state.put(AUTO_OPENED_MANUAL_OBJECTIVES, opened);
    }

    public static void clearAutoOpenedManualObjectives(Player player, String questId) {
        CompoundTag state = questIfPresent(player, questId);
        if (state != null) {
            state.remove(AUTO_OPENED_MANUAL_OBJECTIVES);
        }
    }

    public static boolean isManualLessonUnlocked(Player player, String manualId, String lessonId) {
        if (manualId == null || manualId.isBlank() || lessonId == null || lessonId.isBlank()) {
            return false;
        }
        CompoundTag root = rootIfPresent(player);
        if (root == null) return false;
        String key = manualLessonStorageKey(manualId, lessonId);
        ListTag unlocked = root.getList(UNLOCKED_MANUAL_LESSONS, Tag.TAG_STRING);
        for (int i = 0; i < unlocked.size(); i++) {
            if (key.equals(unlocked.getString(i))) return true;
        }
        return false;
    }

    public static boolean unlockManualLesson(Player player, String manualId, String lessonId) {
        if (manualId == null || manualId.isBlank() || lessonId == null || lessonId.isBlank()) {
            return false;
        }
        CompoundTag root = root(player);
        String key = manualLessonStorageKey(manualId, lessonId);
        ListTag unlocked = root.getList(UNLOCKED_MANUAL_LESSONS, Tag.TAG_STRING);
        for (int i = 0; i < unlocked.size(); i++) {
            if (key.equals(unlocked.getString(i))) return false;
        }
        unlocked.add(StringTag.valueOf(key));
        root.put(UNLOCKED_MANUAL_LESSONS, unlocked);
        return true;
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
            CompoundTag state = questIfPresent(player, questId);
            if (state != null) {
                state.remove(RUNTIME_MARKERS);
            }
        }
    }

    public static void clearRuntimeMarkers(Player player, String questId) {
        if (questId == null || questId.isBlank()) {
            return;
        }
        CompoundTag state = questIfPresent(player, questId);
        if (state != null) {
            state.remove(RUNTIME_MARKERS);
        }
    }

    public static List<String> activeQuestIds(Player player) {
        CompoundTag root = rootIfPresent(player);
        if (root == null || !root.contains(QUESTS, Tag.TAG_COMPOUND)) {
            return List.of();
        }
        CompoundTag quests = root.getCompound(QUESTS);
        List<String> activeQuestIds = new ArrayList<>();
        for (String questId : quests.getAllKeys()) {
            if ("active".equals(quests.getCompound(questId).getString("status"))) {
                activeQuestIds.add(questId);
            }
        }
        return activeQuestIds;
    }

    public static List<String> questIds(Player player) {
        CompoundTag root = rootIfPresent(player);
        if (root == null || !root.contains(QUESTS, Tag.TAG_COMPOUND)) {
            return List.of();
        }
        return new ArrayList<>(root.getCompound(QUESTS).getAllKeys());
    }

    public static void removeQuest(Player player, String questId) {
        if (questId == null || questId.isBlank()) {
            return;
        }
        CompoundTag root = rootIfPresent(player);
        if (root != null && root.contains(QUESTS, Tag.TAG_COMPOUND)) {
            root.getCompound(QUESTS).remove(questId.trim());
        }
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

    private static String objectiveStorageKey(String phaseId, String objectiveId) {
        return phaseId.trim() + "::" + objectiveId.trim();
    }

    private static String manualLessonStorageKey(String manualId, String lessonId) {
        return manualId.trim() + "::" + lessonId.trim();
    }

    @Nullable
    private static CompoundTag objectiveProgressTag(Player player, String questId, boolean create) {
        CompoundTag state = create ? quest(player, questId) : questIfPresent(player, questId);
        if (state == null) {
            return null;
        }
        if (!state.contains(OBJECTIVE_PROGRESS, Tag.TAG_COMPOUND)) {
            if (!create) {
                return null;
            }
            state.put(OBJECTIVE_PROGRESS, new CompoundTag());
        }
        return state.getCompound(OBJECTIVE_PROGRESS);
    }

    @Nullable
    private static CompoundTag runtimeMarkersTag(Player player, String questId, boolean create) {
        CompoundTag state = create ? quest(player, questId) : questIfPresent(player, questId);
        if (state == null) {
            return null;
        }
        if (!state.contains(RUNTIME_MARKERS, Tag.TAG_COMPOUND)) {
            if (!create) {
                return null;
            }
            state.put(RUNTIME_MARKERS, new CompoundTag());
        }
        return state.getCompound(RUNTIME_MARKERS);
    }

    public static void setTrackedQuestIds(Player player, List<String> questIds) {
        ListTag tracked = new ListTag();
        if (questIds != null) {
            for (String questId : questIds) {
                if (questId == null || questId.isBlank()) {
                    continue;
                }
                tracked.add(StringTag.valueOf(questId.trim()));
                break;
            }
        }
        root(player).put(TRACKED_QUESTS, tracked);
    }

    public static List<String> trackedQuestIds(Player player) {
        CompoundTag root = rootIfPresent(player);
        if (root == null) {
            return List.of();
        }
        ListTag tracked = root.getList(TRACKED_QUESTS, Tag.TAG_STRING);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < tracked.size(); i++) {
            String questId = tracked.getString(i);
            if (!questId.isBlank() && !result.contains(questId)) {
                result.add(questId);
            }
        }
        return result;
    }

    public static Optional<String> trackedQuestId(Player player) {
        List<String> tracked = trackedQuestIds(player);
        return tracked.isEmpty() ? Optional.empty() : Optional.of(tracked.get(0));
    }

    public static void setTrackedQuestId(Player player, @Nullable String questId) {
        if (questId == null || questId.isBlank()) {
            setTrackedQuestIds(player, List.of());
            return;
        }
        setTrackedQuestIds(player, List.of(questId.trim()));
    }

    public static void untrackQuest(Player player, String questId) {
        if (questId == null || questId.isBlank()) {
            return;
        }
        Optional<String> tracked = trackedQuestId(player);
        if (tracked.isPresent() && tracked.get().equals(questId.trim())) {
            setTrackedQuestId(player, null);
        }
    }

    public static void setRecommendedQuestIds(Player player, List<String> questIds) {
        ListTag recommended = new ListTag();
        for (String questId : questIds.stream().distinct().limit(64).toList()) {
            if (questId != null && !questId.isBlank()) {
                recommended.add(StringTag.valueOf(questId.trim()));
            }
        }
        root(player).put(RECOMMENDED_QUESTS, recommended);
    }

    public static List<String> recommendedQuestIds(Player player) {
        CompoundTag root = rootIfPresent(player);
        if (root == null) {
            return List.of();
        }
        ListTag recommended = root.getList(RECOMMENDED_QUESTS, Tag.TAG_STRING);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < recommended.size(); i++) {
            String questId = recommended.getString(i).trim();
            if (!questId.isBlank() && !result.contains(questId)) {
                result.add(questId);
            }
        }
        return result;
    }

    public static void recommendQuest(Player player, String questId) {
        if (questId == null || questId.isBlank()) {
            return;
        }
        List<String> recommended = new ArrayList<>(recommendedQuestIds(player));
        String normalized = questId.trim();
        if (!recommended.contains(normalized)) {
            recommended.add(normalized);
            setRecommendedQuestIds(player, recommended);
        }
    }

    public static void unrecommendQuest(Player player, String questId) {
        if (questId == null || questId.isBlank()) {
            return;
        }
        List<String> recommended = new ArrayList<>(recommendedQuestIds(player));
        if (recommended.remove(questId.trim())) {
            setRecommendedQuestIds(player, recommended);
        }
    }

    public static boolean isTrackerHudEnabled(Player player) {
        CompoundTag root = rootIfPresent(player);
        return root == null || !root.contains(TRACKER_HUD_ENABLED, Tag.TAG_BYTE) || root.getBoolean(TRACKER_HUD_ENABLED);
    }

    public static void setTrackerHudEnabled(Player player, boolean enabled) {
        root(player).putBoolean(TRACKER_HUD_ENABLED, enabled);
    }

    public static boolean hasCompletedNpcStructure(Player player, String completionId) {
        if (completionId == null || completionId.isBlank()) {
            return false;
        }
        CompoundTag root = rootIfPresent(player);
        if (root == null) {
            return false;
        }
        ListTag completed = root.getList(COMPLETED_NPC_STRUCTURES, Tag.TAG_COMPOUND);
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
        CompoundTag root = rootIfPresent(player);
        if (root == null) {
            return false;
        }
        ListTag completed = root.getList(COMPLETED_NPC_STRUCTURES, Tag.TAG_COMPOUND);
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

    public static void clearCompletedNpcStructures(Player player) {
        CompoundTag root = rootIfPresent(player);
        if (root != null) {
            root.remove(COMPLETED_NPC_STRUCTURES);
        }
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
