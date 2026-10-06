package com.phantasm.briefing.network.packet;

import com.phantasm.briefing.network.ClientPacketBridge;
import com.phantasm.briefing.data.QuestHintTarget;
import com.phantasm.briefing.data.QuestHintType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record SyncQuestHintTargetS2CPacket(
        List<QuestHintTarget> targets
) {
    public SyncQuestHintTargetS2CPacket {
        targets = targets == null ? List.of() : List.copyOf(targets);
    }

    public static void encode(SyncQuestHintTargetS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.targets().size());
        for (QuestHintTarget target : packet.targets()) {
            buffer.writeVarInt(target.entityId());
            buffer.writeEnum(target.type());
        }
    }

    public static SyncQuestHintTargetS2CPacket decode(FriendlyByteBuf buffer) {
        int size = Math.max(0, Math.min(64, buffer.readVarInt()));
        List<QuestHintTarget> targets = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            targets.add(new QuestHintTarget(buffer.readVarInt(), buffer.readEnum(QuestHintType.class)));
        }
        return new SyncQuestHintTargetS2CPacket(targets);
    }

    public static void handle(SyncQuestHintTargetS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientPacketBridge.handle(packet));
        context.setPacketHandled(true);
    }
}
