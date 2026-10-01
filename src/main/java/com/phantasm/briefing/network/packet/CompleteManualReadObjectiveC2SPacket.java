package com.phantasm.briefing.network.packet;

import com.phantasm.briefing.network.ModNetwork;
import com.phantasm.briefing.service.QuestRuntimeService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public record CompleteManualReadObjectiveC2SPacket(
        String questId,
        String phaseId,
        String objectiveId,
        String manualId,
        String lessonId,
        String pageId
) {
    public static void encode(CompleteManualReadObjectiveC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.questId());
        buffer.writeUtf(packet.phaseId());
        buffer.writeUtf(packet.objectiveId());
        buffer.writeUtf(packet.manualId());
        buffer.writeUtf(packet.lessonId());
        buffer.writeUtf(packet.pageId());
    }

    public static CompleteManualReadObjectiveC2SPacket decode(FriendlyByteBuf buffer) {
        return new CompleteManualReadObjectiveC2SPacket(
                buffer.readUtf(), buffer.readUtf(), buffer.readUtf(),
                buffer.readUtf(), buffer.readUtf(), buffer.readUtf());
    }

    public static void handle(CompleteManualReadObjectiveC2SPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player == null) return;
            if (!QuestRuntimeService.completeManualReadObjective(
                    player,
                    packet.questId(),
                    packet.phaseId(),
                    packet.objectiveId(),
                    packet.manualId(),
                    packet.lessonId(),
                    packet.pageId())) {
                return;
            }
            ModNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    OpenQuestJournalS2CPacket.forPlayer(player)
            );
        });
        context.setPacketHandled(true);
    }
}
