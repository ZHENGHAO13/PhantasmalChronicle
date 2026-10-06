package com.phantasm.briefing.network.packet;

import com.phantasm.briefing.network.ClientPacketBridge;
import com.phantasm.briefing.data.QuestTreeSnapshot;
import com.phantasm.briefing.service.QuestTreeService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Refreshes quest/manual UI only when the player already has a quest screen open.
 * It never opens a screen on its own and is sent after relevant editor data changes.
 */
public record RefreshQuestContentS2CPacket(
        OpenQuestJournalS2CPacket journal,
        OpenQuestTreeS2CPacket tree
) {
    public static RefreshQuestContentS2CPacket forPlayer(ServerPlayer player) {
        QuestTreeSnapshot snapshot = QuestTreeService.buildSnapshot(player);
        return new RefreshQuestContentS2CPacket(
                OpenQuestJournalS2CPacket.forPlayer(player),
                new OpenQuestTreeS2CPacket(snapshot.nodes(), snapshot.edges())
        );
    }

    public static void encode(RefreshQuestContentS2CPacket packet, FriendlyByteBuf buffer) {
        OpenQuestJournalS2CPacket.encode(packet.journal(), buffer);
        OpenQuestTreeS2CPacket.encode(packet.tree(), buffer);
    }

    public static RefreshQuestContentS2CPacket decode(FriendlyByteBuf buffer) {
        return new RefreshQuestContentS2CPacket(
                OpenQuestJournalS2CPacket.decode(buffer),
                OpenQuestTreeS2CPacket.decode(buffer)
        );
    }

    public static void handle(RefreshQuestContentS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientPacketBridge.handle(packet));
        context.setPacketHandled(true);
    }
}
