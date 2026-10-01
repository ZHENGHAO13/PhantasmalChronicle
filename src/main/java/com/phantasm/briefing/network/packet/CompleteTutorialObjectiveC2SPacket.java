package com.phantasm.briefing.network.packet;

import com.phantasm.briefing.network.ModNetwork;
import com.phantasm.briefing.service.QuestRuntimeService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public record CompleteTutorialObjectiveC2SPacket(
        String questId,
        String phaseId,
        String objectiveId
) {
    public static void encode(CompleteTutorialObjectiveC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.questId());
        buffer.writeUtf(packet.phaseId());
        buffer.writeUtf(packet.objectiveId());
    }

    public static CompleteTutorialObjectiveC2SPacket decode(FriendlyByteBuf buffer) {
        return new CompleteTutorialObjectiveC2SPacket(buffer.readUtf(), buffer.readUtf(), buffer.readUtf());
    }

    public static void handle(CompleteTutorialObjectiveC2SPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player == null) {
                return;
            }
            QuestRuntimeService.completeTutorialObjective(
                    player,
                    packet.questId(),
                    packet.phaseId(),
                    packet.objectiveId()
            );
            ModNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    OpenQuestJournalS2CPacket.forPlayer(player)
            );
        });
        context.setPacketHandled(true);
    }
}
