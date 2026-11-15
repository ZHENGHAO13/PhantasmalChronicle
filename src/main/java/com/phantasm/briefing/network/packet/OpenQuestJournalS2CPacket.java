package com.phantasm.briefing.network.packet;

import com.phantasm.briefing.client.screen.QuestJournalScreen;
import com.phantasm.briefing.data.QuestJournalEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

public record OpenQuestJournalS2CPacket(
        List<QuestJournalEntry> entries
) {

    public static void encode(OpenQuestJournalS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeCollection(packet.entries(), (buf, entry) -> {
            buf.writeUtf(entry.questId());
            buf.writeUtf(entry.title());
            buf.writeUtf(entry.description());
            buf.writeCollection(entry.objectiveLines(), FriendlyByteBuf::writeUtf);
            buf.writeEnum(entry.status());
        });
    }

    public static OpenQuestJournalS2CPacket decode(FriendlyByteBuf buffer) {
        return new OpenQuestJournalS2CPacket(buffer.readList(buf -> new QuestJournalEntry(
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readList(FriendlyByteBuf::readUtf),
                buf.readEnum(com.phantasm.briefing.data.QuestRuntimeStatus.class)
        )));
    }

    public static void handle(OpenQuestJournalS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new QuestJournalScreen(packet.entries())));
        context.setPacketHandled(true);
    }
}
