package com.phantasm.briefing.service;

import com.phantasm.briefing.data.QuestDataManager;
import com.phantasm.briefing.data.QuestObjectiveSpec;
import com.phantasm.briefing.data.QuestObjectiveType;
import com.phantasm.briefing.data.QuestPhaseSpec;
import com.phantasm.briefing.data.QuestSpec;
import com.phantasm.briefing.data.QuestRuntimeStatus;
import com.phantasm.briefing.data.QuestTrackerEntry;
import com.phantasm.briefing.data.QuestTrackerObjectiveEntry;
import com.phantasm.briefing.data.QuestTrackerPhaseEntry;
import com.phantasm.briefing.data.ManualReadPromptState;
import com.phantasm.briefing.network.ModNetwork;
import com.phantasm.briefing.network.packet.SyncQuestTrackerS2CPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class QuestTrackerService {
    private QuestTrackerService() {
    }

    public static Optional<String> normalizeTracking(ServerPlayer player) {
        List<String> oldTracked = BriefingPlayerData.trackedQuestIds(player);
        String validTracked = "";
        for (String questId : oldTracked) {
            QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
            QuestRuntimeStatus status = QuestRuntimeService.status(player, quest);
            if (status == QuestRuntimeStatus.ACTIVE || status == QuestRuntimeStatus.READY_TO_TURN_IN) {
                validTracked = questId;
                break;
            }
        }
        if (validTracked.isBlank()) {
            for (QuestSpec quest : QuestDataManager.getInstance().getAllQuests()) {
                QuestRuntimeStatus status = QuestRuntimeService.status(player, quest);
                if (status == QuestRuntimeStatus.ACTIVE || status == QuestRuntimeStatus.READY_TO_TURN_IN) {
                    validTracked = quest.questId();
                    break;
                }
            }
        }

        List<String> normalizedTracked = validTracked.isBlank() ? List.of() : List.of(validTracked);
        if (!oldTracked.equals(normalizedTracked)) {
            BriefingPlayerData.setTrackedQuestIds(player, normalizedTracked);
        }

        List<String> oldRecommended = BriefingPlayerData.recommendedQuestIds(player);
        List<String> validRecommended = new ArrayList<>();
        for (String questId : oldRecommended) {
            QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
            if (QuestRuntimeService.status(player, quest) == QuestRuntimeStatus.AVAILABLE) {
                validRecommended.add(questId);
            }
        }
        if (!oldRecommended.equals(validRecommended)) {
            BriefingPlayerData.setRecommendedQuestIds(player, validRecommended);
        }

        return validTracked.isBlank() ? Optional.empty() : Optional.of(validTracked);
    }

    public static Optional<QuestTrackerEntry> resolveTrackedQuest(ServerPlayer player) {
        return normalizeTracking(player)
                .flatMap(questId -> QuestDataManager.getInstance().getQuest(questId))
                .filter(quest -> {
                    QuestRuntimeStatus status = QuestRuntimeService.status(player, quest);
                    return status == QuestRuntimeStatus.ACTIVE || status == QuestRuntimeStatus.READY_TO_TURN_IN;
                })
                .map(quest -> new QuestTrackerEntry(
                        quest.questId(),
                        quest.trackerTitle().isBlank() ? quest.title() : quest.trackerTitle(),
                        buildTrackedPhases(player, quest),
                        resolveTrackedMarker(player, quest)
                ));
    }

    private static List<QuestTrackerPhaseEntry> buildTrackedPhases(ServerPlayer player, QuestSpec quest) {
        List<QuestTrackerPhaseEntry> result = new ArrayList<>();
        for (QuestPhaseSpec phase : QuestRuntimeService.activePhases(player, quest)) {
            List<QuestTrackerObjectiveEntry> objectives = new ArrayList<>();
            for (QuestObjectiveSpec objective : phase.objectives()) {
                if (!BriefingConditionService.all(player, null, objective.conditions())) {
                    continue;
                }
                boolean completed = BriefingPlayerData.isObjectiveCompleted(
                        player, quest.questId(), phase.phaseId(), objective.objectiveId());
                boolean active = !completed && QuestRuntimeService.isObjectiveActive(player, quest, objective);
                objectives.add(new QuestTrackerObjectiveEntry(
                        objectiveTitle(player, quest, phase, objective, completed),
                        List.copyOf(objective.objectiveLines()),
                        objective.usesStructureSearch() && !StructureSearchCompatService.hasResolvedMarker(player, quest, objective)
                                ? objective.resolvedStructureLabel()
                                : "",
                        completed,
                        active
                ));
            }
            String phaseTitle = phase.title().isBlank() ? phase.phaseId() : phase.title();
            result.add(new QuestTrackerPhaseEntry(
                    phaseTitle,
                    List.copyOf(objectives)
            ));
        }
        return List.copyOf(result);
    }

    private static String objectiveTitle(
            ServerPlayer player,
            QuestSpec quest,
            QuestPhaseSpec phase,
            QuestObjectiveSpec objective,
            boolean completed
    ) {
        String title = objective.title().isBlank() ? objective.objectiveId() : objective.title();
        if (!objective.isEventDriven()) {
            return title;
        }
        int progress = completed
                ? objective.requiredCount()
                : Math.min(objective.requiredCount(), Math.max(0,
                BriefingPlayerData.objectiveProgress(
                        player, quest.questId(), phase.phaseId(), objective.objectiveId())));
        return title + " (" + progress + "/" + objective.requiredCount() + ")";
    }


    public static void syncToClient(ServerPlayer player) {
        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SyncQuestTrackerS2CPacket(
                        resolveTrackedQuest(player).orElse(null),
                        resolveManualReadPrompt(player).orElse(null)
                )
        );
        ManualAutoOpenService.openPending(player);
    }

    public static Optional<ManualReadPromptState> resolveManualReadPrompt(ServerPlayer player) {
        LinkedHashSet<String> orderedQuestIds = new LinkedHashSet<>(BriefingPlayerData.trackedQuestIds(player));
        orderedQuestIds.addAll(BriefingPlayerData.activeQuestIds(player));
        for (String questId : orderedQuestIds) {
            QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
            if (quest == null) {
                continue;
            }
            for (QuestObjectiveSpec objective : QuestRuntimeService.activeObjectives(player, quest)) {
                QuestPhaseSpec phase = QuestRuntimeService.activePhaseForObjective(
                        player, quest, objective.objectiveId()).orElse(null);
                if (phase == null
                        || objective.objectiveType() != QuestObjectiveType.READ_MANUAL
                        || objective.manualRefs().isEmpty()
                        || !QuestRuntimeService.isManualReadAccessible(
                                player,
                                quest,
                                phase,
                                objective
                        )) {
                    continue;
                }
                return Optional.of(new ManualReadPromptState(
                        quest.questId(),
                        phase.phaseId(),
                        objective.objectiveId()
                ));
            }
        }
        return Optional.empty();
    }

    private static com.phantasm.briefing.data.QuestMarkerSpec resolveTrackedMarker(ServerPlayer player, QuestSpec quest) {
        return QuestRuntimeService.currentObjective(player, quest)
                .map(objective -> StructureSearchCompatService.resolveMarker(player, quest, objective))
                .orElse(quest.marker());
    }
}
