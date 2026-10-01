package com.phantasm.briefing.network.packet;

import com.phantasm.briefing.service.DialogueFlowCoordinator;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record DialogueOptionSelectedC2SPacket(
        String nodeId,
        String optionId
) {

    public static void encode(DialogueOptionSelectedC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.nodeId());
        buffer.writeUtf(packet.optionId());
    }

    public static DialogueOptionSelectedC2SPacket decode(FriendlyByteBuf buffer) {
        return new DialogueOptionSelectedC2SPacket(buffer.readUtf(), buffer.readUtf());
    }

    public static void handle(DialogueOptionSelectedC2SPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                DialogueFlowCoordinator.handleOptionSelected(player, packet.nodeId(), packet.optionId());
            }
        });
        context.setPacketHandled(true);
    }
}
