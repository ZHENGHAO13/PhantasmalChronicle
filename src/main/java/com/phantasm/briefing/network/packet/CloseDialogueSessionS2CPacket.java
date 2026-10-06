package com.phantasm.briefing.network.packet;

import com.phantasm.briefing.network.ClientPacketBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record CloseDialogueSessionS2CPacket() {
    public static void encode(CloseDialogueSessionS2CPacket packet, FriendlyByteBuf buffer) {
    }

    public static CloseDialogueSessionS2CPacket decode(FriendlyByteBuf buffer) {
        return new CloseDialogueSessionS2CPacket();
    }

    public static void handle(CloseDialogueSessionS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientPacketBridge.handle(packet));
        context.setPacketHandled(true);
    }
}
