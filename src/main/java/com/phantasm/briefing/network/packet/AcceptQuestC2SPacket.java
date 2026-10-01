package com.phantasm.briefing.network.packet;

import com.phantasm.briefing.network.ModNetwork;
import com.phantasm.briefing.service.QuestEntityHintService;
import com.phantasm.briefing.service.QuestRuntimeService;
import com.phantasm.briefing.service.QuestTrackerService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public record AcceptQuestC2SPacket(String questId) {
    public static void encode(AcceptQuestC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.questId());
    }

    public static AcceptQuestC2SPacket decode(FriendlyByteBuf buffer) {
        return new AcceptQuestC2SPacket(buffer.readUtf());
    }

    public static void handle(AcceptQuestC2SPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player == null) {
                return;
            }
            QuestRuntimeService.accept(player, packet.questId());
            QuestTrackerService.normalizeTracking(player);
            QuestTrackerService.syncToClient(player);
            QuestEntityHintService.requestSync(player);
            ModNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    OpenQuestJournalS2CPacket.forPlayer(player)
            );
        });
        context.setPacketHandled(true);
    }
}
