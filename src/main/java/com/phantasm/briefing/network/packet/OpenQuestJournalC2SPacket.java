package com.phantasm.briefing.network.packet;

import com.phantasm.briefing.network.ModNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public final class OpenQuestJournalC2SPacket {
    public static void encode(OpenQuestJournalC2SPacket packet, FriendlyByteBuf buffer) {
    }

    public static OpenQuestJournalC2SPacket decode(FriendlyByteBuf buffer) {
        return new OpenQuestJournalC2SPacket();
    }

    public static void handle(OpenQuestJournalC2SPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player == null) {
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
