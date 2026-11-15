package com.phantasm.briefing.network.packet;

import com.phantasm.briefing.client.QuestTrackerClientState;
import com.phantasm.briefing.data.QuestHintType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SyncQuestHintTargetS2CPacket(
        int entityId,
        QuestHintType type
) {
    public static void encode(SyncQuestHintTargetS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entityId());
        buffer.writeEnum(packet.type());
    }

    public static SyncQuestHintTargetS2CPacket decode(FriendlyByteBuf buffer) {
        return new SyncQuestHintTargetS2CPacket(buffer.readVarInt(), buffer.readEnum(QuestHintType.class));
    }

    public static void handle(SyncQuestHintTargetS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> QuestTrackerClientState.setHint(packet.entityId(), packet.type()));
        context.setPacketHandled(true);
    }
}
