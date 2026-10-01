package com.phantasm.briefing.service;

import com.mojang.logging.LogUtils;
import com.phantasm.briefing.data.QuestDataManager;
import com.phantasm.briefing.data.QuestObjectiveSpec;
import com.phantasm.briefing.data.QuestPhaseMode;
import com.phantasm.briefing.data.QuestPhaseSpec;
import com.phantasm.briefing.data.QuestSpec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One-way player-data migration into the current phase-qualified objective model.
 * This class is deliberately not part of quest runtime logic: old save shapes are
 * consumed once on login, normalized, and then removed or rewritten.
 */
public final class PlayerDataMigrationService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SCHEMA_VERSION = "schemaVersion";
    private static final int CURRENT_SCHEMA_VERSION = 3;
    private static final String QUESTS = "quests";
    private static final String COMPLETED_OBJECTIVES = "completedObjectives";
    private static final String COMPLETED_PHASES = "completedPhases";
    private static final String ACTIVATED_PHASES = "activatedPhases";
    private static final String OBJECTIVE_PROGRESS = "objectiveProgress";
    private static final String VIEWED_TUTORIALS = "viewedTutorials";

    private PlayerDataMigrationService() {
    }

    public static void migrate(ServerPlayer player) {
        CompoundTag root = BriefingPlayerData.root(player);
        int version = root.getInt(SCHEMA_VERSION);
        if (version >= CURRENT_SCHEMA_VERSION) {
            return;
        }

        int rewrittenObjectives = 0;
        int rewrittenProgress = 0;
        boolean pendingObjectiveMigration = false;
        if (root.contains(QUESTS, Tag.TAG_COMPOUND)) {
            CompoundTag quests = root.getCompound(QUESTS);
            for (String questId : new ArrayList<>(quests.getAllKeys())) {
                if (!quests.contains(questId, Tag.TAG_COMPOUND)) {
                    continue;
                }
                CompoundTag state = quests.getCompound(questId);
                QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
                if (quest == null) {
                    // Do not declare the whole player save migrated while a quest state still contains
                    // phase-unqualified objective data. The content may simply be temporarily unavailable
                    // because its file failed validation during this startup.
                    if (requiresObjectiveKeyMigration(state)) {
                        pendingObjectiveMigration = true;
                    }
                    state.remove(VIEWED_TUTORIALS);
                    continue;
                }
                migratePhaseState(quest, state);
                rewrittenObjectives += migrateCompletedObjectives(quest, state);
                rewrittenProgress += migrateObjectiveProgress(quest, state);
                // The old two-step image-tutorial acknowledgement is not a runtime concept anymore.
                // Completed objectives were migrated above; merely viewing a page never grants completion.
                state.remove(VIEWED_TUTORIALS);
                if (requiresObjectiveKeyMigration(state)) {
                    pendingObjectiveMigration = true;
                }
            }
        }

        if (!pendingObjectiveMigration) {
            root.putInt(SCHEMA_VERSION, CURRENT_SCHEMA_VERSION);
        } else {
            LOGGER.warn(
                    "[PhantasmBriefing] Player {} still has quest progress awaiting content migration; schema version was not advanced",
                    player.getGameProfile().getName()
            );
        }
        if (rewrittenObjectives > 0 || rewrittenProgress > 0) {
            LOGGER.info(
                    "[PhantasmBriefing] Migrated player {} quest progress to current objective keys (completed={}, progress={})",
                    player.getGameProfile().getName(), rewrittenObjectives, rewrittenProgress
            );
        }
    }


    private static void migratePhaseState(QuestSpec quest, CompoundTag state) {
        String currentPhaseId = state.getString("phase").trim();
        if (quest.phaseMode() == QuestPhaseMode.SEQUENTIAL
                && !currentPhaseId.isBlank()
                && !state.contains(COMPLETED_PHASES, Tag.TAG_LIST)) {
            ListTag completed = new ListTag();
            for (QuestPhaseSpec phase : quest.phases()) {
                if (phase.phaseId().equals(currentPhaseId)) break;
                completed.add(StringTag.valueOf(phase.phaseId()));
            }
            if (!completed.isEmpty()) state.put(COMPLETED_PHASES, completed);
        }
        if (quest.phaseMode() == QuestPhaseMode.FREE
                && !state.contains(ACTIVATED_PHASES, Tag.TAG_LIST)) {
            LinkedHashSet<String> activatedIds = new LinkedHashSet<>();
            if (state.contains(COMPLETED_PHASES, Tag.TAG_LIST)) {
                ListTag completed = state.getList(COMPLETED_PHASES, Tag.TAG_STRING);
                for (int i = 0; i < completed.size(); i++) {
                    String phaseId = completed.getString(i).trim();
                    if (!phaseId.isBlank() && quest.phase(phaseId) != null) activatedIds.add(phaseId);
                }
            }
            if (!currentPhaseId.isBlank() && quest.phase(currentPhaseId) != null) activatedIds.add(currentPhaseId);
            if (!activatedIds.isEmpty()) {
                ListTag activated = new ListTag();
                activatedIds.forEach(id -> activated.add(StringTag.valueOf(id)));
                state.put(ACTIVATED_PHASES, activated);
            }
        }
    }

    private static int migrateCompletedObjectives(QuestSpec quest, CompoundTag state) {
        if (!state.contains(COMPLETED_OBJECTIVES, Tag.TAG_LIST)) {
            return 0;
        }
        ListTag source = state.getList(COMPLETED_OBJECTIVES, Tag.TAG_STRING);
        Set<String> normalized = new LinkedHashSet<>();
        int rewritten = 0;
        for (int i = 0; i < source.size(); i++) {
            String key = source.getString(i).trim();
            if (key.isBlank()) {
                continue;
            }
            if (key.contains("::")) {
                normalized.add(key);
                continue;
            }
            List<String> migrated = qualifyObjectiveKeys(quest, key);
            if (!migrated.isEmpty()) {
                normalized.addAll(migrated);
                rewritten++;
            } else {
                // Keep the raw value instead of discarding data. The schema version remains eligible for
                // a later retry if matching content is restored.
                normalized.add(key);
            }
        }
        ListTag target = new ListTag();
        normalized.forEach(key -> target.add(StringTag.valueOf(key)));
        state.put(COMPLETED_OBJECTIVES, target);
        return rewritten;
    }

    private static int migrateObjectiveProgress(QuestSpec quest, CompoundTag state) {
        if (!state.contains(OBJECTIVE_PROGRESS, Tag.TAG_COMPOUND)) {
            return 0;
        }
        CompoundTag source = state.getCompound(OBJECTIVE_PROGRESS);
        CompoundTag target = new CompoundTag();
        int rewritten = 0;
        for (String key : new ArrayList<>(source.getAllKeys())) {
            int value = source.getInt(key);
            if (key.contains("::")) {
                target.putInt(key, value);
                continue;
            }
            List<String> migrated = qualifyObjectiveKeys(quest, key);
            if (!migrated.isEmpty()) {
                for (String migratedKey : migrated) {
                    target.putInt(migratedKey, Math.max(value, target.getInt(migratedKey)));
                }
                rewritten++;
            } else {
                target.putInt(key, value);
            }
        }
        state.put(OBJECTIVE_PROGRESS, target);
        return rewritten;
    }

    private static List<String> qualifyObjectiveKeys(QuestSpec quest, String objectiveId) {
        List<String> result = new ArrayList<>();
        for (QuestPhaseSpec phase : quest.phases()) {
            QuestObjectiveSpec objective = phase.objective(objectiveId);
            if (objective != null) {
                result.add(phase.phaseId() + "::" + objectiveId);
            }
        }
        return List.copyOf(result);
    }

    private static boolean requiresObjectiveKeyMigration(CompoundTag state) {
        if (state.contains(COMPLETED_OBJECTIVES, Tag.TAG_LIST)) {
            ListTag completed = state.getList(COMPLETED_OBJECTIVES, Tag.TAG_STRING);
            for (int i = 0; i < completed.size(); i++) {
                String key = completed.getString(i).trim();
                if (!key.isBlank() && !key.contains("::")) {
                    return true;
                }
            }
        }
        if (state.contains(OBJECTIVE_PROGRESS, Tag.TAG_COMPOUND)) {
            for (String key : state.getCompound(OBJECTIVE_PROGRESS).getAllKeys()) {
                if (!key.isBlank() && !key.contains("::")) {
                    return true;
                }
            }
        }
        return false;
    }
}
