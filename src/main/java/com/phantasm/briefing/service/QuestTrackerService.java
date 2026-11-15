package com.phantasm.briefing.service;

import com.phantasm.briefing.data.QuestSpec;
import com.phantasm.briefing.data.QuestTrackerEntry;
import com.phantasm.briefing.network.ModNetwork;
import com.phantasm.briefing.network.packet.SyncQuestTrackerS2CPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class QuestTrackerService {
    private QuestTrackerService() {
    }

    public static Optional<QuestTrackerEntry> resolveTrackedQuest(ServerPlayer player) {
        List<QuestSpec> visibleQuests = QuestJournalService.getVisibleQuests(player);
        List<String> trackedQuestIds = BriefingPlayerData.trackedQuestIds(player);
        return visibleQuests.stream()
                .filter(quest -> trackedQuestIds.contains(quest.questId()))
                .filter(quest -> QuestRuntimeService.status(player, quest).priority() > 0)
                .sorted(Comparator
                        .comparingInt((QuestSpec quest) -> -QuestRuntimeService.status(player, quest).priority())
                        .thenComparing(QuestSpec::title, String.CASE_INSENSITIVE_ORDER))
                .findFirst()
                .or(() -> visibleQuests.stream()
                        .filter(quest -> {
                            int priority = QuestRuntimeService.status(player, quest).priority();
                            return priority > 1;
                        })
                        .sorted(Comparator
                                .comparingInt((QuestSpec quest) -> -QuestRuntimeService.status(player, quest).priority())
                                .thenComparing(QuestSpec::title, String.CASE_INSENSITIVE_ORDER))
                        .findFirst())
                .or(() -> visibleQuests.stream()
                        .filter(quest -> QuestRuntimeService.status(player, quest).priority() > 0)
                        .sorted(Comparator
                                .comparingInt((QuestSpec quest) -> -QuestRuntimeService.status(player, quest).priority())
                                .thenComparing(QuestSpec::title, String.CASE_INSENSITIVE_ORDER))
                        .findFirst())
                .or(() -> visibleQuests.stream()
                        .sorted(Comparator
                                .comparingInt((QuestSpec quest) -> -QuestRuntimeService.status(player, quest).priority())
                                .thenComparing(QuestSpec::title, String.CASE_INSENSITIVE_ORDER))
                        .findFirst())
                .map(quest -> new QuestTrackerEntry(
                        quest.questId(),
                        quest.trackerTitle().isBlank() ? quest.title() : quest.trackerTitle(),
                        QuestRuntimeService.currentObjectiveLines(player, quest),
                        resolveTrackedMarker(player, quest)
                ));
    }

    public static void syncToClient(ServerPlayer player) {
        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SyncQuestTrackerS2CPacket(resolveTrackedQuest(player).orElse(null))
        );
    }

    private static com.phantasm.briefing.data.QuestMarkerSpec resolveTrackedMarker(ServerPlayer player, QuestSpec quest) {
        return QuestRuntimeService.currentObjective(player, quest)
                .map(objective -> StructureSearchCompatService.resolveMarker(player, quest, objective))
                .orElse(quest.marker());
    }
}
