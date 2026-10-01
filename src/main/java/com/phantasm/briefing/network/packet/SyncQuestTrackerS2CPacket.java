package com.phantasm.briefing.network.packet;

import com.phantasm.briefing.client.QuestTrackerClientState;
import com.phantasm.briefing.data.QuestMarkerSpec;
import com.phantasm.briefing.data.QuestTrackerEntry;
import com.phantasm.briefing.data.ManualReadPromptState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

public record SyncQuestTrackerS2CPacket(
        QuestTrackerEntry entry,
        ManualReadPromptState manualReadPrompt
) {

    public static void encode(SyncQuestTrackerS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.entry() != null);
        if (packet.entry() != null) {
            buffer.writeUtf(packet.entry().questId());
            buffer.writeUtf(packet.entry().title());
            buffer.writeCollection(packet.entry().objectiveLines(), FriendlyByteBuf::writeUtf);
            buffer.writeBoolean(packet.entry().marker() != null);
            if (packet.entry().marker() != null) {
                QuestMarkerSpec marker = packet.entry().marker();
                buffer.writeUtf(marker.label());
                buffer.writeUtf(marker.dimension());
                buffer.writeDouble(marker.x());
                buffer.writeDouble(marker.y());
                buffer.writeDouble(marker.z());
            }
        }
        buffer.writeBoolean(packet.manualReadPrompt() != null);
        if (packet.manualReadPrompt() != null) {
            buffer.writeUtf(packet.manualReadPrompt().questId());
            buffer.writeUtf(packet.manualReadPrompt().phaseId());
            buffer.writeUtf(packet.manualReadPrompt().objectiveId());
        }
    }

    public static SyncQuestTrackerS2CPacket decode(FriendlyByteBuf buffer) {
        QuestTrackerEntry entry = null;
        if (buffer.readBoolean()) {
            String questId = buffer.readUtf();
            String title = buffer.readUtf();
            List<String> objectiveLines = buffer.readList(FriendlyByteBuf::readUtf);
            QuestMarkerSpec marker = null;
            if (buffer.readBoolean()) {
                marker = new QuestMarkerSpec(
                        buffer.readUtf(),
                        buffer.readUtf(),
                        buffer.readDouble(),
                        buffer.readDouble(),
                        buffer.readDouble()
                );
            }
            entry = new QuestTrackerEntry(questId, title, objectiveLines, marker);
        }
        ManualReadPromptState manualReadPrompt = null;
        if (buffer.readBoolean()) {
            manualReadPrompt = new ManualReadPromptState(buffer.readUtf(), buffer.readUtf(), buffer.readUtf());
        }
        return new SyncQuestTrackerS2CPacket(entry, manualReadPrompt);
    }

    public static void handle(SyncQuestTrackerS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            QuestTrackerClientState.setTrackedEntry(packet.entry());
            QuestTrackerClientState.setManualReadPrompt(packet.manualReadPrompt());
        });
        context.setPacketHandled(true);
    }
}
