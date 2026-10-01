package com.phantasm.briefing.network.packet;

import com.phantasm.briefing.service.DialogueFlowCoordinator;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record DialogueNodeFinishedC2SPacket(String nodeId) {

    public static void encode(DialogueNodeFinishedC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.nodeId());
    }

    public static DialogueNodeFinishedC2SPacket decode(FriendlyByteBuf buffer) {
        return new DialogueNodeFinishedC2SPacket(buffer.readUtf());
    }

    public static void handle(DialogueNodeFinishedC2SPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                DialogueFlowCoordinator.handleNodeFinished(player, packet.nodeId());
            }
        });
        context.setPacketHandled(true);
    }
}
