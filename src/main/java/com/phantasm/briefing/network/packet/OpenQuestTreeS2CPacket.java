package com.phantasm.briefing.network.packet;

import com.phantasm.briefing.network.ClientPacketBridge;
import com.phantasm.briefing.data.QuestTreeEdgeEntry;
import com.phantasm.briefing.data.QuestTreeEdgeType;
import com.phantasm.briefing.data.QuestTreeNodeEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

public record OpenQuestTreeS2CPacket(
        List<QuestTreeNodeEntry> nodes,
        List<QuestTreeEdgeEntry> edges
) {
    public static void encode(OpenQuestTreeS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeCollection(packet.nodes(), (buf, node) -> {
            buf.writeUtf(node.questId());
            buf.writeUtf(node.title());
            buf.writeUtf(node.description());
            buf.writeEnum(node.status());
            buf.writeInt(node.x());
            buf.writeInt(node.y());
            buf.writeCollection(node.objectiveLines(), FriendlyByteBuf::writeUtf);
            buf.writeCollection(node.parentQuestIds(), FriendlyByteBuf::writeUtf);
            buf.writeCollection(node.nextQuestIds(), FriendlyByteBuf::writeUtf);
        });
        buffer.writeCollection(packet.edges(), (buf, edge) -> {
            buf.writeUtf(edge.fromQuestId());
            buf.writeUtf(edge.toQuestId());
            buf.writeEnum(edge.edgeType());
        });
    }

    public static OpenQuestTreeS2CPacket decode(FriendlyByteBuf buffer) {
        List<QuestTreeNodeEntry> nodes = buffer.readList(buf -> new QuestTreeNodeEntry(
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readEnum(com.phantasm.briefing.data.QuestRuntimeStatus.class),
                buf.readInt(),
                buf.readInt(),
                buf.readList(FriendlyByteBuf::readUtf),
                buf.readList(FriendlyByteBuf::readUtf),
                buf.readList(FriendlyByteBuf::readUtf)
        ));
        List<QuestTreeEdgeEntry> edges = buffer.readList(buf -> new QuestTreeEdgeEntry(
                buf.readUtf(),
                buf.readUtf(),
                buf.readEnum(QuestTreeEdgeType.class)
        ));
        return new OpenQuestTreeS2CPacket(nodes, edges);
    }

    public static void handle(OpenQuestTreeS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientPacketBridge.handle(packet));
        context.setPacketHandled(true);
    }
}
