package com.phantasm.briefing.network.packet;

import com.phantasm.briefing.data.QuestDataManager;
import com.phantasm.briefing.data.QuestRuntimeStatus;
import com.phantasm.briefing.data.QuestSpec;
import com.phantasm.briefing.network.ModNetwork;
import com.phantasm.briefing.service.BriefingPlayerData;
import com.phantasm.briefing.service.QuestRuntimeService;
import com.phantasm.briefing.service.QuestTrackerService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.Optional;
import java.util.function.Supplier;

public record SetTrackedQuestC2SPacket(String questId, boolean tracked) {
    public static void encode(SetTrackedQuestC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.questId());
        buffer.writeBoolean(packet.tracked());
    }

    public static SetTrackedQuestC2SPacket decode(FriendlyByteBuf buffer) {
        return new SetTrackedQuestC2SPacket(buffer.readUtf(), buffer.readBoolean());
    }

    public static void handle(SetTrackedQuestC2SPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player == null) {
                return;
            }

            String questId = packet.questId() == null ? "" : packet.questId().trim();
            if (!packet.tracked()) {
                Optional<String> current = BriefingPlayerData.trackedQuestId(player);
                if (questId.isBlank() || current.filter(questId::equals).isPresent()) {
                    BriefingPlayerData.setTrackedQuestId(player, null);
                }
            } else if (!questId.isBlank()) {
                QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
                QuestRuntimeStatus status = QuestRuntimeService.status(player, quest);
                if (status == QuestRuntimeStatus.ACTIVE || status == QuestRuntimeStatus.READY_TO_TURN_IN) {
                    BriefingPlayerData.setTrackedQuestId(player, questId);
                }
            }

            QuestTrackerService.normalizeTracking(player);
            QuestTrackerService.syncToClient(player);
            ModNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    OpenQuestJournalS2CPacket.forPlayer(player)
            );
        });
        context.setPacketHandled(true);
    }
}
