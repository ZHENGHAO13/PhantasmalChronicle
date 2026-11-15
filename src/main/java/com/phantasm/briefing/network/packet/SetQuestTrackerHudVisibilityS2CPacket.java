package com.phantasm.briefing.network.packet;

import com.phantasm.briefing.client.QuestTrackerClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SetQuestTrackerHudVisibilityS2CPacket(
        boolean enabled
) {
    public static void encode(SetQuestTrackerHudVisibilityS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.enabled());
    }

    public static SetQuestTrackerHudVisibilityS2CPacket decode(FriendlyByteBuf buffer) {
        return new SetQuestTrackerHudVisibilityS2CPacket(buffer.readBoolean());
    }

    public static void handle(SetQuestTrackerHudVisibilityS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> QuestTrackerClientState.setTrackerHudEnabled(packet.enabled()));
        context.setPacketHandled(true);
    }
}
