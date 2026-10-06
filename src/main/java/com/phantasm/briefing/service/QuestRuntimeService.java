package com.phantasm.briefing.service;

import com.mojang.logging.LogUtils;
import com.phantasm.briefing.data.DialogueAction;
import com.phantasm.briefing.data.QuestDataManager;
import com.phantasm.briefing.data.QuestObjectiveSpec;
import com.phantasm.briefing.data.QuestObjectiveMode;
import com.phantasm.briefing.data.QuestObjectiveType;
import com.phantasm.briefing.data.QuestPhaseMode;
import com.phantasm.briefing.data.QuestPhaseSpec;
import com.phantasm.briefing.data.QuestRuntimeStatus;
import com.phantasm.briefing.data.QuestSpec;
import com.phantasm.briefing.integration.FTBIntegrationHelper;
import com.phantasm.briefing.event.NpcStructureSpawnEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class QuestRuntimeService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private QuestRuntimeService() {
    }

    public static boolean canAccept(ServerPlayer player, QuestSpec quest) {
        if (quest == null) return false;
        if (BriefingPlayerData.isQuestActive(player, quest.questId())) return false;
        if (!quest.repeatable() && BriefingPlayerData.isQuestCompleted(player, quest.questId())) return false;
        return QuestPrerequisiteService.areSatisfied(player, quest);
    }

    public static boolean accept(ServerPlayer player, String questId) {
        QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
        if (!canAccept(player, quest)) {
            return false;
        }

        activateQuest(player, quest);
        return true;
    }

    public static boolean give(ServerPlayer player, String questId) {
        QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
        if (quest == null) {
            return false;
        }

        activateQuest(player, quest);
        return true;
    }

    public static int ensureAutoStartedQuests(ServerPlayer player) {
        if (player == null) {
            return 0;
        }

        int started = 0;
        boolean changed;
        int safety = 64;
        do {
            changed = false;
            for (QuestSpec quest : List.copyOf(QuestDataManager.getInstance().getAllQuests())) {
                if (!quest.autoStart() || !canAccept(player, quest)) {
                    continue;
                }
                if (accept(player, quest.questId())) {
                    started++;
                    changed = true;
                }
            }
        } while (changed && --safety > 0);
        return started;
    }

    public static boolean complete(ServerPlayer player, String questId) {
        QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
        if (quest == null) {
            return false;
        }
        if (BriefingPlayerData.isQuestCompleted(player, questId)) {
            return true;
        }

        String currentPhaseId = BriefingPlayerData.phase(player, questId);
        if (!currentPhaseId.isBlank()) {
            BriefingPlayerData.completePhase(player, questId, currentPhaseId);
        }
        var state = BriefingPlayerData.quest(player, questId);
        state.putString("status", "completed");
        if (!quest.ftbQuestId().isBlank()) {
            FTBIntegrationHelper.completeQuestSilently(player, quest.ftbQuestId());
        }
        BriefingPlayerData.untrackQuest(player, questId);
        invalidateAutomaticObjectives(player);
        StructureSearchCompatService.clearQuestRuntimeData(player, questId);
        NpcStructureSpawnEvents.invalidatePlayer(player);
        executeQuestActions(player, quest.questId(), quest.completeActions(), "quest_complete", quest.questId(), false);
        playQuestCompletedSound(player);
        ensureAutoStartedQuests(player);
        applyNextQuestRecommendations(player, quest);
        QuestTrackerService.normalizeTracking(player);
        QuestTrackerService.syncToClient(player);
        QuestEntityHintService.requestSync(player);
        return true;
    }

    public static boolean reset(ServerPlayer player, String questId) {
        QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
        if (quest == null) {
            return false;
        }

        StructureSearchCompatService.clearQuestRuntimeData(player, questId);
        BriefingPlayerData.removeQuest(player, questId);
        BriefingPlayerData.untrackQuest(player, questId);
        BriefingPlayerData.unrecommendQuest(player, questId);
        invalidateAutomaticObjectives(player);
        NpcStructureSpawnEvents.invalidatePlayer(player);
        ensureAutoStartedQuests(player);
        QuestTrackerService.normalizeTracking(player);
        QuestTrackerService.syncToClient(player);
        QuestEntityHintService.requestSync(player);
        return true;
    }

    public static int resetAll(ServerPlayer player) {
        List<String> questIds = BriefingPlayerData.questIds(player);
        if (questIds.isEmpty()) {
            BriefingPlayerData.setTrackedQuestId(player, null);
            BriefingPlayerData.setRecommendedQuestIds(player, List.of());
            invalidateAutomaticObjectives(player);
            NpcStructureSpawnEvents.invalidatePlayer(player);
            ensureAutoStartedQuests(player);
            QuestTrackerService.normalizeTracking(player);
            QuestTrackerService.syncToClient(player);
            QuestEntityHintService.requestSync(player);
            return 0;
        }

        for (String questId : questIds) {
            StructureSearchCompatService.clearQuestRuntimeData(player, questId);
            BriefingPlayerData.removeQuest(player, questId);
        }
        BriefingPlayerData.setTrackedQuestId(player, null);
        BriefingPlayerData.setRecommendedQuestIds(player, List.of());
        invalidateAutomaticObjectives(player);
        NpcStructureSpawnEvents.invalidatePlayer(player);
        ensureAutoStartedQuests(player);
        QuestTrackerService.normalizeTracking(player);
        QuestTrackerService.syncToClient(player);
        QuestEntityHintService.requestSync(player);
        return questIds.size();
    }

    private static void activateQuest(ServerPlayer player, QuestSpec quest) {
        String questId = quest.questId();
        var state = BriefingPlayerData.quest(player, questId);
        state.putString("status", "active");
        // Establish a focus immediately so accept-actions that start more quests cannot steal the first focus.
        QuestTrackerService.normalizeTracking(player);
        invalidateAutomaticObjectives(player);
        BriefingPlayerData.clearCompletedObjectives(player, questId);
        BriefingPlayerData.clearCompletedPhases(player, questId);
        BriefingPlayerData.clearActivatedPhases(player, questId);
        BriefingPlayerData.clearObjectiveProgresses(player, questId);
        BriefingPlayerData.clearAutoOpenedManualObjectives(player, questId);
        StructureSearchCompatService.clearQuestRuntimeData(player, questId);

        BriefingPlayerData.unrecommendQuest(player, questId);
        executeQuestActions(player, quest.questId(), quest.acceptActions(), "quest_accept", quest.questId(), true);

        QuestPhaseSpec initialPhase = quest.phase(quest.initialPhaseId());
        if (quest.phaseMode() == QuestPhaseMode.FREE) {
            activateFreePhases(player, quest, "");
        } else if (initialPhase != null) {
            applyPhaseChange(player, quest, initialPhase);
        } else {
            BriefingPlayerData.setPhase(player, questId, quest.initialPhaseId());
            invalidateAutomaticObjectives(player);
            NpcStructureSpawnEvents.invalidatePlayer(player);
            QuestTrackerService.syncToClient(player);
            QuestEntityHintService.requestSync(player);
        }
        evaluateAutomaticObjectives(player);
    }

    public static boolean isQuestActive(ServerPlayer player, String questId) {
        return BriefingPlayerData.isQuestActive(player, questId);
    }

    public static QuestRuntimeStatus status(ServerPlayer player, String questId) {
        QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
        return status(player, quest);
    }

    public static QuestRuntimeStatus status(ServerPlayer player, QuestSpec quest) {
        if (quest == null) {
            return QuestRuntimeStatus.LOCKED;
        }
        if (isQuestCompleted(player, quest.questId())) {
            return QuestRuntimeStatus.COMPLETED;
        }
        if (isQuestReadyToTurnIn(player, quest)) {
            return QuestRuntimeStatus.READY_TO_TURN_IN;
        }
        if (isQuestActive(player, quest.questId())) {
            return QuestRuntimeStatus.ACTIVE;
        }
        if (canAccept(player, quest)) {
            return QuestRuntimeStatus.AVAILABLE;
        }
        return QuestRuntimeStatus.LOCKED;
    }

    public static boolean isQuestAvailable(ServerPlayer player, String questId) {
        QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
        return canAccept(player, quest);
    }

    public static boolean isQuestReadyToTurnIn(ServerPlayer player, String questId) {
        QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
        return isQuestReadyToTurnIn(player, quest);
    }

    public static boolean isQuestReadyToTurnIn(ServerPlayer player, QuestSpec quest) {
        if (quest == null || !isQuestActive(player, quest.questId())) {
            return false;
        }

        for (QuestObjectiveSpec objective : activeObjectives(player, quest)) {
            if (objective.hintType() == com.phantasm.briefing.data.QuestHintType.COMPLETE) {
                return true;
            }
        }
        for (QuestPhaseSpec phase : activePhases(player, quest)) {
            if (phase.hintType() == com.phantasm.briefing.data.QuestHintType.COMPLETE) {
                return true;
            }
        }
        return false;
    }

    public static boolean isQuestCompleted(ServerPlayer player, String questId) {
        QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
        return quest != null && BriefingPlayerData.isQuestCompleted(player, questId);
    }

    public static String phase(ServerPlayer player, String questId) {
        return BriefingPlayerData.phase(player, questId);
    }

    public static List<QuestPhaseSpec> activePhases(ServerPlayer player, QuestSpec quest) {
        if (quest == null || !isQuestActive(player, quest.questId())) {
            return List.of();
        }
        if (quest.phaseMode() != QuestPhaseMode.FREE) {
            QuestPhaseSpec current = quest.phase(phase(player, quest.questId()));
            return current == null ? List.of() : List.of(current);
        }

        List<QuestPhaseSpec> active = new ArrayList<>();
        for (QuestPhaseSpec phase : quest.phases()) {
            if (!BriefingPlayerData.isPhaseCompleted(player, quest.questId(), phase.phaseId())) {
                active.add(phase);
            }
        }
        return List.copyOf(active);
    }

    public static boolean isPhaseActive(ServerPlayer player, String questId, String phaseId) {
        QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
        return isPhaseActive(player, quest, phaseId);
    }

    public static boolean isPhaseActive(ServerPlayer player, QuestSpec quest, String phaseId) {
        if (quest == null || phaseId == null || phaseId.isBlank()) {
            return false;
        }
        return activePhases(player, quest).stream().anyMatch(phase -> phase.phaseId().equals(phaseId.trim()));
    }

    public static Optional<QuestPhaseSpec> currentPhase(ServerPlayer player, QuestSpec quest) {
        if (quest == null || !isQuestActive(player, quest.questId())) {
            return Optional.empty();
        }
        String preferred = phase(player, quest.questId());
        QuestPhaseSpec preferredPhase = quest.phase(preferred);
        if (preferredPhase != null && isPhaseActive(player, quest, preferredPhase.phaseId())) {
            return Optional.of(preferredPhase);
        }
        return activePhases(player, quest).stream().findFirst();
    }

    public static Optional<QuestPhaseSpec> activePhaseForObjective(
            ServerPlayer player, QuestSpec quest, String objectiveId) {
        if (quest == null || objectiveId == null || objectiveId.isBlank()) {
            return Optional.empty();
        }
        for (QuestPhaseSpec phase : activePhases(player, quest)) {
            if (phase.objective(objectiveId) != null) {
                return Optional.of(phase);
            }
        }
        return Optional.empty();
    }

    public static List<String> currentObjectiveLines(ServerPlayer player, QuestSpec quest) {
        Optional<QuestObjectiveSpec> currentObjective = currentObjective(player, quest);
        if (currentObjective.isPresent()) {
            List<String> objectiveLines = buildObjectiveLines(player, quest, currentObjective.get());
            if (!objectiveLines.isEmpty()) {
                return objectiveLines;
            }
        }
        return currentPhase(player, quest)
                .map(QuestPhaseSpec::objectiveLines)
                .filter(lines -> !lines.isEmpty())
                .orElse(quest.objectiveLines());
    }


    public static List<String> currentPhaseObjectiveLines(ServerPlayer player, QuestSpec quest) {
        List<QuestPhaseSpec> phases = activePhases(player, quest);
        if (phases.isEmpty()) {
            return currentObjectiveLines(player, quest);
        }

        List<String> pending = new ArrayList<>();
        List<String> completedLines = new ArrayList<>();
        for (QuestPhaseSpec phase : phases) {
            if (phase.objectives().isEmpty()) {
                if (!phase.objectiveLines().isEmpty()) {
                    pending.addAll(phase.objectiveLines());
                }
                continue;
            }
            for (QuestObjectiveSpec objective : phase.objectives()) {
                if (!BriefingConditionService.all(player, null, objective.conditions())) {
                    continue;
                }
                boolean completed = BriefingPlayerData.isObjectiveCompleted(
                        player, quest.questId(), phase.phaseId(), objective.objectiveId());
                int progress = completed
                        ? objective.requiredCount()
                        : Math.min(objective.requiredCount(), Math.max(0,
                        BriefingPlayerData.objectiveProgress(
                                player, quest.questId(), phase.phaseId(), objective.objectiveId())));
                List<String> displayLines = objective.objectiveLines().isEmpty()
                        ? List.of(objectiveDisplayName(objective))
                        : objective.objectiveLines();
                String progressSuffix = objective.isEventDriven()
                        ? " (" + progress + "/" + objective.requiredCount() + ")"
                        : "";
                List<String> target = completed ? completedLines : pending;
                for (int lineIndex = 0; lineIndex < displayLines.size(); lineIndex++) {
                    String text = displayLines.get(lineIndex);
                    if (lineIndex == 0) {
                        target.add((completed ? "✓ " : "○ ") + text + progressSuffix);
                    } else {
                        target.add("  " + text);
                    }
                }
            }
        }
        if (!pending.isEmpty() || !completedLines.isEmpty()) {
            pending.addAll(completedLines);
            return List.copyOf(pending);
        }
        return currentObjectiveLines(player, quest);
    }


    /**
     * Removes persisted runtime state only for quests that were confirmed to have existed
     * before an editor hot reload and disappeared after that reload. This is deliberately
     * event-driven; no periodic scan is introduced.
     */
    public static int purgeRemovedQuestData(ServerPlayer player, Set<String> removedQuestIds) {
        if (player == null || removedQuestIds == null || removedQuestIds.isEmpty()) {
            return 0;
        }

        Set<String> storedQuestIds = Set.copyOf(BriefingPlayerData.questIds(player));
        int removedStates = 0;
        for (String rawQuestId : removedQuestIds) {
            if (rawQuestId == null || rawQuestId.isBlank()) {
                continue;
            }
            String questId = rawQuestId.trim();
            StructureSearchCompatService.clearQuestRuntimeData(player, questId);
            if (storedQuestIds.contains(questId)) {
                BriefingPlayerData.removeQuest(player, questId);
                removedStates++;
            }
            BriefingPlayerData.untrackQuest(player, questId);
            BriefingPlayerData.unrecommendQuest(player, questId);
        }
        return removedStates;
    }

    /**
     * Removes orphan quest state that is no longer present in the authoritative content set.
     * The editor manifest and freshly loaded registry are treated as the allow-list, so a
     * manifest entry that failed server-side parsing is preserved instead of being destroyed.
     */
    public static int purgeOrphanedQuestData(ServerPlayer player, Set<String> editorQuestIds) {
        if (player == null) {
            return 0;
        }

        Set<String> allowedQuestIds = new java.util.LinkedHashSet<>();
        if (editorQuestIds != null) {
            editorQuestIds.stream()
                    .filter(id -> id != null && !id.isBlank())
                    .map(String::trim)
                    .forEach(allowedQuestIds::add);
        }
        QuestDataManager.getInstance().getAllQuests().stream()
                .map(QuestSpec::questId)
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .forEach(allowedQuestIds::add);

        Set<String> persistedIds = new java.util.LinkedHashSet<>();
        persistedIds.addAll(BriefingPlayerData.questIds(player));
        persistedIds.addAll(BriefingPlayerData.trackedQuestIds(player));
        persistedIds.addAll(BriefingPlayerData.recommendedQuestIds(player));
        persistedIds.removeIf(id -> id == null || id.isBlank() || allowedQuestIds.contains(id.trim()));
        return purgeRemovedQuestData(player, persistedIds);
    }

    public static boolean reconcileActiveQuestFlow(ServerPlayer player) {
        if (player == null) {
            return false;
        }

        boolean changedAny = false;
        int safety = 64;
        boolean changed;
        do {
            changed = false;
            for (String questId : List.copyOf(BriefingPlayerData.activeQuestIds(player))) {
                QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
                if (quest == null) {
                    continue;
                }
                if (quest.phaseMode() == QuestPhaseMode.FREE) {
                    if (activateFreePhases(player, quest, "")) {
                        changed = true;
                        changedAny = true;
                    }
                    for (QuestPhaseSpec phase : List.copyOf(activePhases(player, quest))) {
                        if (phase.objectives().isEmpty()) {
                            continue;
                        }
                        if (handlePostObjectiveCompletion(player, quest, phase)) {
                            changed = true;
                            changedAny = true;
                            evaluateAutomaticObjectives(player);
                            break;
                        }
                    }
                    if (changed) {
                        break;
                    }
                    if (!quest.phases().isEmpty() && activePhases(player, quest).isEmpty()) {
                        if (complete(player, quest.questId())) {
                            changed = true;
                            changedAny = true;
                            break;
                        }
                    }
                    continue;
                }

                QuestPhaseSpec phase = currentPhase(player, quest).orElse(null);
                if (phase == null || phase.objectives().isEmpty()) {
                    continue;
                }
                if (handlePostObjectiveCompletion(player, quest, phase)) {
                    changed = true;
                    changedAny = true;
                    evaluateAutomaticObjectives(player);
                    break;
                }
            }
        } while (changed && --safety > 0);
        return changedAny;
    }

    public static boolean setPhase(ServerPlayer player, String questId, String phaseId) {
        QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
        if (quest == null || !isQuestActive(player, questId)) {
            return false;
        }
        QuestPhaseSpec phase = quest.phase(phaseId);
        if (phase == null) {
            return false;
        }

        if (quest.phaseMode() == QuestPhaseMode.FREE) {
            if (BriefingPlayerData.isPhaseCompleted(player, questId, phase.phaseId())) {
                return false;
            }
            BriefingPlayerData.setPhase(player, questId, phase.phaseId());
            boolean newlyActivated = BriefingPlayerData.activatePhase(player, questId, phase.phaseId());
            if (newlyActivated) {
                executeQuestActions(player, quest.questId(), phase.actions(), "phase", phase.phaseId(), true);
            }
            invalidateAutomaticObjectives(player);
            NpcStructureSpawnEvents.invalidatePlayer(player);
            evaluateFtbObjectivesOnce(player, "");
            QuestTrackerService.syncToClient(player);
            QuestEntityHintService.requestSync(player);
            return true;
        }

        String previousPhaseId = BriefingPlayerData.phase(player, questId);
        if (!previousPhaseId.isBlank() && !previousPhaseId.equals(phase.phaseId())) {
            BriefingPlayerData.completePhase(player, questId, previousPhaseId);
            playPhaseCompletedSound(player);
            QuestPhaseSpec previousPhase = quest.phase(previousPhaseId);
            if (previousPhase != null) {
                executeQuestActions(player, quest.questId(), previousPhase.completionActions(), "phase_complete", previousPhase.phaseId(), true);
            }
        }

        applyPhaseChange(player, quest, phase);
        return true;
    }

    public static List<QuestObjectiveSpec> activeObjectives(ServerPlayer player, QuestSpec quest) {
        if (quest == null) {
            return List.of();
        }

        List<QuestObjectiveSpec> active = new ArrayList<>();
        for (QuestPhaseSpec phase : activePhases(player, quest)) {
            if (phase.objectives().isEmpty()) {
                continue;
            }
            for (QuestObjectiveSpec objective : phase.objectives()) {
                if (BriefingPlayerData.isObjectiveCompleted(player, quest.questId(), phase.phaseId(), objective.objectiveId())
                        || !BriefingConditionService.all(player, null, objective.conditions())) {
                    continue;
                }
                active.add(objective);
                if (phase.objectiveMode() != QuestObjectiveMode.FREE) {
                    break;
                }
            }
        }
        return List.copyOf(active);
    }

    public static Optional<QuestObjectiveSpec> currentObjective(ServerPlayer player, QuestSpec quest) {
        return activeObjectives(player, quest).stream().findFirst();
    }

    public static boolean isObjectiveActive(ServerPlayer player, QuestSpec quest, QuestObjectiveSpec objective) {
        if (objective == null) {
            return false;
        }
        return activeObjectives(player, quest).stream()
                .anyMatch(candidate -> candidate.objectiveId().equals(objective.objectiveId()));
    }

    public static boolean isObjectiveActive(
            ServerPlayer player, String questId, String phaseId, String objectiveId) {
        QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
        if (quest == null || phaseId == null || phaseId.isBlank()
                || objectiveId == null || objectiveId.isBlank()) {
            return false;
        }
        QuestPhaseSpec phase = quest.phase(phaseId);
        QuestObjectiveSpec objective = phase == null ? null : phase.objective(objectiveId);
        if (objective == null || !isPhaseActive(player, quest, phase.phaseId())) {
            return false;
        }
        for (QuestObjectiveSpec candidate : phase.objectives()) {
            if (BriefingPlayerData.isObjectiveCompleted(
                    player, quest.questId(), phase.phaseId(), candidate.objectiveId())
                    || !BriefingConditionService.all(player, null, candidate.conditions())) {
                continue;
            }
            if (candidate.objectiveId().equals(objective.objectiveId())) {
                return true;
            }
            if (phase.objectiveMode() != QuestObjectiveMode.FREE) {
                return false;
            }
        }
        return false;
    }

    public static boolean isObjectiveCompleted(ServerPlayer player, String questId, String objectiveId) {
        QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
        if (quest == null || objectiveId == null || objectiveId.isBlank()) {
            return false;
        }
        QuestPhaseSpec current = currentPhase(player, quest).orElse(null);
        if (current != null && current.objective(objectiveId) != null) {
            return BriefingPlayerData.isObjectiveCompleted(player, questId, current.phaseId(), objectiveId);
        }
        for (QuestPhaseSpec phase : quest.phases()) {
            if (phase.objective(objectiveId) != null
                    && BriefingPlayerData.isObjectiveCompleted(player, questId, phase.phaseId(), objectiveId)) {
                return true;
            }
        }
        return false;
    }

    public static boolean completeObjective(ServerPlayer player, String questId, String objectiveId) {
        return completeObjective(player, questId, objectiveId, "");
    }

    public static boolean completeManualReadObjective(
            ServerPlayer player,
            String questId,
            String phaseId,
            String objectiveId,
            String manualId,
            String lessonId,
            String pageId
    ) {
        ManualReadContext context = resolveManualRead(player, questId, phaseId, objectiveId);
        if (context == null || !isManualReadAccessible(player, context.quest(), context.phase(), context.objective())) {
            return false;
        }
        boolean questActive = BriefingPlayerData.isQuestActive(player, questId);
        boolean phaseCurrent = isPhaseActive(player, context.quest(), phaseId);
        boolean objectiveCurrent = isObjectiveActive(player, context.quest(), context.objective());
        boolean objectiveCompleted = BriefingPlayerData.isObjectiveCompleted(player, questId, phaseId, objectiveId);
        if (!ManualReadAccessPolicy.canComplete(questActive, phaseCurrent, objectiveCurrent, objectiveCompleted)) {
            return false;
        }
        if (!matchesManualCompletionPage(player, context.objective(), manualId, lessonId, pageId)) {
            return false;
        }
        return completeObjective(player, questId, objectiveId);
    }

    static boolean isManualReadAccessible(
            ServerPlayer player,
            QuestSpec quest,
            QuestPhaseSpec phase,
            QuestObjectiveSpec objective
    ) {
        if (player == null || quest == null || phase == null || objective == null
                || objective.objectiveType() != QuestObjectiveType.READ_MANUAL
                || objective.manualRefs().isEmpty()) {
            return false;
        }
        boolean questCompleted = BriefingPlayerData.isQuestCompleted(player, quest.questId());
        boolean phaseCompleted = BriefingPlayerData.isPhaseCompleted(player, quest.questId(), phase.phaseId());
        boolean phaseCurrent = isPhaseActive(player, quest, phase.phaseId());
        boolean objectiveCompleted = BriefingPlayerData.isObjectiveCompleted(
                player, quest.questId(), phase.phaseId(), objective.objectiveId());
        boolean objectiveCurrent = phaseCurrent && isObjectiveActive(player, quest, objective);
        return ManualReadAccessPolicy.canView(
                questCompleted, phaseCompleted, phaseCurrent, objectiveCurrent, objectiveCompleted);
    }

    private static ManualReadContext resolveManualRead(
            ServerPlayer player,
            String questId,
            String phaseId,
            String objectiveId
    ) {
        if (player == null || questId == null || questId.isBlank() || phaseId == null || phaseId.isBlank()
                || objectiveId == null || objectiveId.isBlank()) {
            return null;
        }
        QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
        QuestPhaseSpec phase = quest == null ? null : quest.phase(phaseId);
        QuestObjectiveSpec objective = phase == null ? null : phase.objective(objectiveId);
        if (quest == null || phase == null || objective == null
                || objective.objectiveType() != QuestObjectiveType.READ_MANUAL
                || objective.manualRefs().isEmpty()) {
            return null;
        }
        return new ManualReadContext(quest, phase, objective);
    }

    private static boolean matchesManualCompletionPage(
            ServerPlayer player,
            QuestObjectiveSpec objective,
            String manualId,
            String lessonId,
            String pageId
    ) {
        if (manualId == null || manualId.isBlank() || lessonId == null || lessonId.isBlank()
                || pageId == null || pageId.isBlank()) {
            return false;
        }
        com.phantasm.briefing.data.ManualSpec manual =
                com.phantasm.briefing.data.ManualDataManager.getInstance().getManual(manualId).orElse(null);
        if (manual == null) return false;
        for (com.phantasm.briefing.data.ManualReferenceSpec ref : objective.manualRefs()) {
            if (!ref.manualId().equals(manual.manualId())) continue;
            com.phantasm.briefing.data.ManualNodeSpec lesson = manual.findLesson(ref.lessonId(), ref.pageId());
            if (lesson == null || !lesson.nodeId().equals(lessonId) || lesson.pages().isEmpty()
                    || !ManualVisibilityService.isLessonVisible(player, manual.manualId(), lesson)) {
                continue;
            }
            for (int index = 0; index < lesson.pages().size(); index++) {
                if (!lesson.pages().get(index).pageId().equals(pageId)) continue;
                return ManualReadCompletionPolicy.matches(
                        ref.manualId(), lesson.nodeId(), ref.pageId(),
                        manualId, lessonId, pageId, index == lesson.pages().size() - 1);
            }
        }
        return false;
    }

    private static boolean completeObjective(ServerPlayer player, String questId, String objectiveId, String excludedFtbQuestId) {
        QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
        QuestPhaseSpec phase = activePhaseForObjective(player, quest, objectiveId).orElse(null);
        QuestObjectiveSpec objective = phase == null ? null : phase.objective(objectiveId);
        if (quest == null || phase == null || objective == null) {
            return false;
        }
        if (BriefingPlayerData.isObjectiveCompleted(player, questId, phase.phaseId(), objectiveId)) {
            return true;
        }
        // Sequential phases must not be bypassed by dialogue/manual actions that target
        // a later objective directly. FREE mode still exposes every eligible objective.
        if (!isObjectiveActive(player, quest, objective)) {
            return false;
        }
        if (!BriefingConditionService.all(player, null, objective.conditions())) {
            return false;
        }

        BriefingPlayerData.completeObjective(player, questId, phase.phaseId(), objectiveId);
        invalidateAutomaticObjectives(player);
        BriefingPlayerData.setObjectiveProgress(player, questId, phase.phaseId(), objectiveId, objective.requiredCount());
        NpcStructureSpawnEvents.invalidatePlayer(player);
        executeObjectiveActions(player, questId, objective);
        if (!handlePostObjectiveCompletion(player, quest, phase, excludedFtbQuestId)) {
            QuestTrackerService.syncToClient(player);
            QuestEntityHintService.requestSync(player);
        }
        return true;
    }

    public static void onEntityKilled(ServerPlayer player, String entityTypeId,
                                      KillTargetRule.Disposition disposition) {
        QuestObjectiveEventService.onEntityKilled(player, entityTypeId, disposition);
    }

    public static void onItemCrafted(ServerPlayer player, String itemId, int craftedCount) {
        QuestObjectiveEventService.onItemCrafted(player, itemId, craftedCount);
    }

    public static void onEntityInteracted(ServerPlayer player, String entityTypeId, String questNodeId, String npcIdentity) {
        QuestObjectiveEventService.onEntityInteracted(player, entityTypeId, questNodeId, npcIdentity);
    }

    /** Called after a quest/phase/objective transition; schedule an immediate next-tick check. */
    public static void invalidateAutomaticObjectives(ServerPlayer player) {
        QuestAutomaticObjectiveService.invalidate(player);
    }

    public static void clearAutomaticObjectiveState(ServerPlayer player) {
        QuestAutomaticObjectiveService.clear(player);
    }

    public static void clearAllAutomaticObjectiveStates() {
        QuestAutomaticObjectiveService.clearAll();
    }

    /** Pickup/crafting/menu events are handled on the next tick, after inventory mutation. */

    public static void markInventoryChanged(ServerPlayer player) {
        QuestAutomaticObjectiveService.markInventoryChanged(player);
    }

    /** Cheap per-player tick gate; the old full active-quest scan is no longer unconditional. */

    public static void tickAutomaticObjectives(ServerPlayer player) {
        QuestAutomaticObjectiveService.tick(player);
    }


    public static void evaluateAutomaticObjectives(ServerPlayer player) {
        QuestAutomaticObjectiveService.evaluate(player);
    }

    public static void refreshFtbCompletionCache(ServerPlayer player) {
        QuestFtbObjectiveService.refreshCompletionCache(player);
    }

    public static void evaluateFtbObjectivesOnce(ServerPlayer player) {
        QuestFtbObjectiveService.evaluateOnce(player, "");
    }

    private static void evaluateFtbObjectivesOnce(ServerPlayer player, String excludedFtbQuestId) {
        QuestFtbObjectiveService.evaluateOnce(player, excludedFtbQuestId);
    }

    public static void onFtbQuestCompleted(ServerPlayer player, String ftbQuestId) {
        QuestFtbObjectiveService.onQuestCompleted(player, ftbQuestId);
    }

    static boolean completeObjectiveFromFtb(ServerPlayer player, String questId, String objectiveId, String excludedFtbQuestId) {
        return completeObjective(player, questId, objectiveId, excludedFtbQuestId);
    }

    private static List<String> buildObjectiveLines(ServerPlayer player, QuestSpec quest, QuestObjectiveSpec objective) {
        List<String> lines = objective.objectiveLines().isEmpty()
                ? List.of(objectiveDisplayName(objective))
                : objective.objectiveLines();
        if (objective.usesStructureSearch() && !StructureSearchCompatService.hasResolvedMarker(player, quest, objective)) {
            List<String> result = new ArrayList<>(lines);
            String waitLine = "等待定位结构: " + objective.resolvedStructureLabel();
            if (result.isEmpty()) {
                result.add(waitLine);
            } else if (!result.contains(waitLine)) {
                result.add(waitLine);
            }
            return List.copyOf(result);
        }
        if (!objective.isEventDriven()) {
            return lines;
        }

        QuestPhaseSpec objectivePhase = activePhaseForObjective(player, quest, objective.objectiveId())
                .orElseGet(() -> quest.phases().stream()
                        .filter(phase -> phase.objective(objective.objectiveId()) != null)
                        .findFirst().orElse(null));
        int progress = objectivePhase == null ? 0 : Math.min(
                objective.requiredCount(),
                Math.max(0, BriefingPlayerData.objectiveProgress(
                        player, quest.questId(), objectivePhase.phaseId(), objective.objectiveId()))
        );
        String suffix = " (" + progress + "/" + objective.requiredCount() + ")";
        if (lines.isEmpty()) {
            return List.of(objectiveDisplayName(objective) + suffix);
        }

        List<String> result = new ArrayList<>(lines);
        result.set(0, result.get(0) + suffix);
        return List.copyOf(result);
    }

    private static String objectiveDisplayName(QuestObjectiveSpec objective) {
        if (objective == null) {
            return "";
        }
        if (!objective.title().isBlank()) {
            return objective.title();
        }
        if (objective.objectiveType() == QuestObjectiveType.FTB_QUEST && !objective.targetId().isBlank()) {
            return objective.targetId();
        }
        return objective.objectiveType().name();
    }

    private static boolean handlePostObjectiveCompletion(ServerPlayer player, QuestSpec quest, QuestPhaseSpec phase) {
        return handlePostObjectiveCompletion(player, quest, phase, "");
    }

    private static boolean handlePostObjectiveCompletion(
            ServerPlayer player,
            QuestSpec quest,
            QuestPhaseSpec phase,
            String excludedFtbQuestId
    ) {
        if (phase.objectives().isEmpty()
                || BriefingPlayerData.isPhaseCompleted(player, quest.questId(), phase.phaseId())) {
            return false;
        }

        boolean allCompleted = phase.objectives().stream()
                .allMatch(objective -> BriefingPlayerData.isObjectiveCompleted(
                        player, quest.questId(), phase.phaseId(), objective.objectiveId()));
        if (!allCompleted) {
            return false;
        }

        BriefingPlayerData.completePhase(player, quest.questId(), phase.phaseId());
        playPhaseCompletedSound(player);
        executeQuestActions(player, quest.questId(), phase.completionActions(),
                "phase_complete", phase.phaseId(), true);

        if (quest.phaseMode() == QuestPhaseMode.FREE) {
            List<QuestPhaseSpec> remaining = activePhases(player, quest);
            if (remaining.isEmpty()) {
                return complete(player, quest.questId());
            }
            String preferred = BriefingPlayerData.phase(player, quest.questId());
            if (preferred.equals(phase.phaseId()) || !isPhaseActive(player, quest, preferred)) {
                BriefingPlayerData.setPhase(player, quest.questId(), remaining.get(0).phaseId());
            }
            invalidateAutomaticObjectives(player);
            NpcStructureSpawnEvents.invalidatePlayer(player);
            evaluateFtbObjectivesOnce(player, excludedFtbQuestId);
            QuestTrackerService.syncToClient(player);
            QuestEntityHintService.requestSync(player);
            return true;
        }

        if (phase.completeQuestOnObjectivesCompleted()) {
            return complete(player, quest.questId());
        }

        QuestPhaseSpec nextPhase = resolveNextPhaseAfterCompletion(quest, phase);
        if (nextPhase != null) {
            applyPhaseChange(player, quest, nextPhase, excludedFtbQuestId);
            return true;
        }

        return complete(player, quest.questId());
    }

    private static QuestPhaseSpec resolveNextPhaseAfterCompletion(QuestSpec quest, QuestPhaseSpec phase) {
        if (!phase.nextPhaseId().isBlank()) {
            QuestPhaseSpec configuredNext = quest.phase(phase.nextPhaseId());
            if (configuredNext != null) {
                return configuredNext;
            }
        }

        int currentIndex = quest.phases().indexOf(phase);
        if (currentIndex < 0 || currentIndex + 1 >= quest.phases().size()) {
            return null;
        }
        return quest.phases().get(currentIndex + 1);
    }

    private static boolean activateFreePhases(
            ServerPlayer player, QuestSpec quest, String excludedFtbQuestId) {
        if (quest == null || quest.phaseMode() != QuestPhaseMode.FREE
                || !BriefingPlayerData.isQuestActive(player, quest.questId())) {
            return false;
        }

        boolean changed = false;
        for (QuestPhaseSpec phase : quest.phases()) {
            if (BriefingPlayerData.isPhaseCompleted(player, quest.questId(), phase.phaseId())) {
                continue;
            }
            if (BriefingPlayerData.activatePhase(player, quest.questId(), phase.phaseId())) {
                changed = true;
                executeQuestActions(player, quest.questId(), phase.actions(),
                        "phase", phase.phaseId(), true);
            }
        }

        List<QuestPhaseSpec> active = activePhases(player, quest);
        String preferred = BriefingPlayerData.phase(player, quest.questId());
        if (!active.isEmpty() && !isPhaseActive(player, quest, preferred)) {
            BriefingPlayerData.setPhase(player, quest.questId(), active.get(0).phaseId());
            changed = true;
        }

        if (changed) {
            invalidateAutomaticObjectives(player);
            NpcStructureSpawnEvents.invalidatePlayer(player);
            evaluateFtbObjectivesOnce(player, excludedFtbQuestId);
            QuestTrackerService.syncToClient(player);
            QuestEntityHintService.requestSync(player);
        }
        return changed;
    }

    private static void applyPhaseChange(ServerPlayer player, QuestSpec quest, QuestPhaseSpec phase) {
        applyPhaseChange(player, quest, phase, "");
    }

    private static void applyPhaseChange(
            ServerPlayer player,
            QuestSpec quest,
            QuestPhaseSpec phase,
            String excludedFtbQuestId
    ) {
        BriefingPlayerData.setPhase(player, quest.questId(), phase.phaseId());
        invalidateAutomaticObjectives(player);
        NpcStructureSpawnEvents.invalidatePlayer(player);
        executeQuestActions(player, quest.questId(), phase.actions(), "phase", phase.phaseId(), true);
        evaluateFtbObjectivesOnce(player, excludedFtbQuestId);
        QuestTrackerService.syncToClient(player);
        QuestEntityHintService.requestSync(player);
    }

    private static void playPhaseCompletedSound(ServerPlayer player) {
        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 1.15F);
    }

    private static void playQuestCompletedSound(ServerPlayer player) {
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.9F, 1.0F);
    }

    private static void executeObjectiveActions(ServerPlayer player, String questId, QuestObjectiveSpec objective) {
        executeQuestActions(player, questId, objective.completionActions(), "objective", objective.objectiveId(), true);
    }

    private static void applyNextQuestRecommendations(ServerPlayer player, QuestSpec completedQuest) {
        if (completedQuest.nextQuestIds().isEmpty()) {
            QuestTrackerService.normalizeTracking(player);
            return;
        }

        Optional<String> trackedQuestId = QuestTrackerService.normalizeTracking(player);
        boolean hasTrackedQuest = trackedQuestId.isPresent();
        for (String nextQuestId : completedQuest.nextQuestIds()) {
            QuestSpec nextQuest = QuestDataManager.getInstance().getQuest(nextQuestId).orElse(null);
            if (nextQuest == null) {
                BriefingPlayerData.unrecommendQuest(player, nextQuestId);
                LOGGER.warn("[PhantasmBriefing] Quest '{}' references missing next quest '{}'", completedQuest.questId(), nextQuestId);
                continue;
            }

            QuestRuntimeStatus status = status(player, nextQuest);
            if (status == QuestRuntimeStatus.AVAILABLE) {
                BriefingPlayerData.recommendQuest(player, nextQuestId);
                continue;
            }

            BriefingPlayerData.unrecommendQuest(player, nextQuestId);
            if (!hasTrackedQuest && (status == QuestRuntimeStatus.ACTIVE || status == QuestRuntimeStatus.READY_TO_TURN_IN)) {
                BriefingPlayerData.setTrackedQuestId(player, nextQuestId);
                hasTrackedQuest = true;
            }
        }
    }
    private static void executeQuestActions(ServerPlayer player, String questId, List<DialogueAction> actions,
                                            String ownerType, String ownerId, boolean allowSelfCompleteQuestAction) {
        QuestActionExecutor.execute(player, questId, actions, ownerType, ownerId, allowSelfCompleteQuestAction);
    }

    private record ManualReadContext(QuestSpec quest, QuestPhaseSpec phase, QuestObjectiveSpec objective) {
    }
}
