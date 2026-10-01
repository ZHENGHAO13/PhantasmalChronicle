package com.phantasm.briefing.service;

import com.phantasm.briefing.data.ManualAutoOpenTarget;
import com.phantasm.briefing.data.ManualReferenceSpec;
import com.phantasm.briefing.data.QuestDataManager;
import com.phantasm.briefing.data.QuestObjectiveSpec;
import com.phantasm.briefing.data.QuestObjectiveType;
import com.phantasm.briefing.data.QuestPhaseSpec;
import com.phantasm.briefing.data.QuestSpec;
import com.phantasm.briefing.network.ModNetwork;
import com.phantasm.briefing.network.packet.OpenQuestJournalS2CPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.LinkedHashSet;
import java.util.List;

/** Opens at most one newly-active handbook objective and records all simultaneous candidates as handled. */
public final class ManualAutoOpenService {
    private ManualAutoOpenService() {
    }

    public static void openPending(ServerPlayer player) {
        if (player == null) {
            return;
        }

        ManualAutoOpenTarget first = null;
        LinkedHashSet<String> questIds = new LinkedHashSet<>(BriefingPlayerData.trackedQuestIds(player));
        questIds.addAll(BriefingPlayerData.activeQuestIds(player));

        for (String questId : questIds) {
            QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
            if (quest == null) {
                continue;
            }
            for (QuestObjectiveSpec objective : QuestRuntimeService.activeObjectives(player, quest)) {
                QuestPhaseSpec phase = QuestRuntimeService.activePhaseForObjective(
                        player, quest, objective.objectiveId()).orElse(null);
                if (phase == null
                        || objective.objectiveType() != QuestObjectiveType.READ_MANUAL
                        || !objective.autoOpenManual()
                        || BriefingPlayerData.isObjectiveCompleted(player, questId, phase.phaseId(), objective.objectiveId())
                        || BriefingPlayerData.hasAutoOpenedManualObjective(
                                player, questId, phase.phaseId(), objective.objectiveId())) {
                    continue;
                }

                List<ManualReferenceSpec> refs = ManualVisibilityService.visibleRefs(player, objective.manualRefs());
                if (refs.isEmpty()) {
                    continue;
                }

                BriefingPlayerData.markAutoOpenedManualObjective(
                        player, questId, phase.phaseId(), objective.objectiveId());
                if (first == null) {
                    ManualReferenceSpec ref = refs.get(0);
                    first = new ManualAutoOpenTarget(
                            questId, phase.phaseId(), objective.objectiveId(),
                            ref.manualId(), ref.lessonId(), ref.pageId());
                }
            }
        }

        if (first != null) {
            ModNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    OpenQuestJournalS2CPacket.forPlayer(player, first)
            );
        }
    }
}
