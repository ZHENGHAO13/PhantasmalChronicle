package com.phantasm.briefing.network.packet;

import com.phantasm.briefing.service.DialogueFlowCoordinator;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record DialogueSessionAbortedC2SPacket(String nodeId) {

    public static void encode(DialogueSessionAbortedC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.nodeId());
    }

    public static DialogueSessionAbortedC2SPacket decode(FriendlyByteBuf buffer) {
        return new DialogueSessionAbortedC2SPacket(buffer.readUtf());
    }

    public static void handle(DialogueSessionAbortedC2SPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                DialogueFlowCoordinator.clearPlayerSession(player.getUUID());
            }
        });
        context.setPacketHandled(true);
    }
}
