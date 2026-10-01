package com.phantasm.briefing.service;

import com.phantasm.briefing.data.QuestDataManager;
import com.phantasm.briefing.data.QuestJournalEntry;
import com.phantasm.briefing.data.QuestJournalObjectiveEntry;
import com.phantasm.briefing.data.QuestJournalPhaseEntry;
import com.phantasm.briefing.data.QuestObjectiveSpec;
import com.phantasm.briefing.data.QuestObjectiveType;
import com.phantasm.briefing.data.QuestPhaseMode;
import com.phantasm.briefing.data.QuestPhaseSpec;
import com.phantasm.briefing.data.QuestRuntimeStatus;
import com.phantasm.briefing.data.QuestSpec;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class QuestJournalService {
    private QuestJournalService() {
    }

    public static List<QuestJournalEntry> buildEntries(ServerPlayer player) {
        List<QuestJournalEntry> entries = new ArrayList<>();
        String trackedQuestId = QuestTrackerService.normalizeTracking(player).orElse("");
        Set<String> recommendedQuestIds = new HashSet<>(BriefingPlayerData.recommendedQuestIds(player));
        for (QuestSpec quest : QuestDataManager.getInstance().getAllQuests()) {
            QuestRuntimeStatus status = QuestRuntimeService.status(player, quest);
            if (status != QuestRuntimeStatus.AVAILABLE
                    && status != QuestRuntimeStatus.ACTIVE
                    && status != QuestRuntimeStatus.READY_TO_TURN_IN
                    && status != QuestRuntimeStatus.COMPLETED) {
                continue;
            }
            entries.add(new QuestJournalEntry(
                    quest.questId(),
                    quest.title(),
                    quest.description(),
                    buildPhases(player, quest, status),
                    status,
                    quest.questId().equals(trackedQuestId),
                    recommendedQuestIds.contains(quest.questId()),
                    ManualVisibilityService.visibleRefs(player, quest.manualRefs())
            ));
        }

        entries.sort(Comparator
                .comparingInt((QuestJournalEntry entry) -> entry.status() == QuestRuntimeStatus.COMPLETED ? 1 : 0)
                .thenComparing(QuestJournalEntry::title, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(entries);
    }

    public static List<QuestSpec> getVisibleQuests(ServerPlayer player) {
        List<QuestSpec> visibleQuests = new ArrayList<>();
        for (QuestSpec quest : QuestDataManager.getInstance().getAllQuests()) {
            if (QuestRuntimeService.status(player, quest) == QuestRuntimeStatus.LOCKED) {
                continue;
            }
            visibleQuests.add(quest);
        }
        return List.copyOf(visibleQuests);
    }

    private static List<QuestJournalPhaseEntry> buildPhases(ServerPlayer player, QuestSpec quest, QuestRuntimeStatus status) {
        if (quest.phases().isEmpty()) {
            return List.of();
        }
        String currentPhaseId = (status == QuestRuntimeStatus.ACTIVE || status == QuestRuntimeStatus.READY_TO_TURN_IN)
                ? QuestRuntimeService.phase(player, quest.questId())
                : "";
        Set<String> completedPhaseIds = new HashSet<>(BriefingPlayerData.completedPhaseIds(player, quest.questId()));

        // 兼容旧存档：仅线性任务可以根据当前阶段推断它之前的阶段已完成。
        if (quest.phaseMode() != QuestPhaseMode.FREE
                && status != QuestRuntimeStatus.COMPLETED && completedPhaseIds.isEmpty() && !currentPhaseId.isBlank()) {
            for (QuestPhaseSpec phase : quest.phases()) {
                if (phase.phaseId().equals(currentPhaseId)) {
                    break;
                }
                completedPhaseIds.add(phase.phaseId());
            }
        }

        List<QuestJournalPhaseEntry> result = new ArrayList<>();
        for (QuestPhaseSpec phase : quest.phases()) {
            boolean completed = status == QuestRuntimeStatus.COMPLETED || completedPhaseIds.contains(phase.phaseId());
            boolean current = status != QuestRuntimeStatus.COMPLETED
                    && (quest.phaseMode() == QuestPhaseMode.FREE
                    ? QuestRuntimeService.isPhaseActive(player, quest, phase.phaseId())
                    : phase.phaseId().equals(currentPhaseId));
            result.add(new QuestJournalPhaseEntry(
                    phase.phaseId(),
                    phase.title().isBlank() ? phase.phaseId() : phase.title(),
                    buildObjectives(player, quest, phase, completed, current),
                    completed,
                    current
            ));
        }
        return List.copyOf(result);
    }

    private static List<QuestJournalObjectiveEntry> buildObjectives(
            ServerPlayer player,
            QuestSpec quest,
            QuestPhaseSpec phase,
            boolean phaseCompleted,
            boolean phaseCurrent
    ) {
        if (phase.objectives().isEmpty()) {
            return List.of();
        }
        List<QuestJournalObjectiveEntry> result = new ArrayList<>();
        for (QuestObjectiveSpec objective : phase.objectives()) {
            boolean completed = phaseCompleted || BriefingPlayerData.isObjectiveCompleted(
                    player, quest.questId(), phase.phaseId(), objective.objectiveId());
            boolean manualReadable = objective.objectiveType() == QuestObjectiveType.READ_MANUAL
                    && (phaseCurrent || phaseCompleted)
                    && QuestRuntimeService.isManualReadAccessible(player, quest, phase, objective);
            int progress = completed
                    ? objective.requiredCount()
                    : Math.min(objective.requiredCount(), Math.max(0, BriefingPlayerData.objectiveProgress(
                    player, quest.questId(), phase.phaseId(), objective.objectiveId())));
            result.add(new QuestJournalObjectiveEntry(
                    objective.objectiveId(),
                    objective.title().isBlank() ? objective.objectiveId() : objective.title(),
                    objective.objectiveType(),
                    progress,
                    objective.requiredCount(),
                    completed,
                    manualReadable,
                    ManualVisibilityService.visibleRefs(player, objective.manualRefs())
            ));
        }
        return List.copyOf(result);
    }
}
