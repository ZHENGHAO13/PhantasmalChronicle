package com.phantasm.briefing.network.packet;

import com.phantasm.briefing.network.ClientPacketBridge;
import com.phantasm.briefing.data.ManualReadPromptState;
import com.phantasm.briefing.data.QuestMarkerSpec;
import com.phantasm.briefing.data.QuestTrackerEntry;
import com.phantasm.briefing.data.QuestTrackerObjectiveEntry;
import com.phantasm.briefing.data.QuestTrackerPhaseEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
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
            buffer.writeVarInt(packet.entry().phases().size());
            for (QuestTrackerPhaseEntry phase : packet.entry().phases()) {
                buffer.writeUtf(phase.title());
                buffer.writeVarInt(phase.objectives().size());
                for (QuestTrackerObjectiveEntry objective : phase.objectives()) {
                    buffer.writeUtf(objective.title());
                    buffer.writeCollection(objective.trackingLines(), FriendlyByteBuf::writeUtf);
                    buffer.writeUtf(objective.waitingStructureLabel());
                    buffer.writeBoolean(objective.completed());
                    buffer.writeBoolean(objective.active());
                }
            }
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
            int phaseCount = buffer.readVarInt();
            List<QuestTrackerPhaseEntry> phases = new ArrayList<>(phaseCount);
            for (int phaseIndex = 0; phaseIndex < phaseCount; phaseIndex++) {
                String phaseTitle = buffer.readUtf();
                int objectiveCount = buffer.readVarInt();
                List<QuestTrackerObjectiveEntry> objectives = new ArrayList<>(objectiveCount);
                for (int objectiveIndex = 0; objectiveIndex < objectiveCount; objectiveIndex++) {
                    objectives.add(new QuestTrackerObjectiveEntry(
                            buffer.readUtf(),
                            buffer.readList(FriendlyByteBuf::readUtf),
                            buffer.readUtf(),
                            buffer.readBoolean(),
                            buffer.readBoolean()
                    ));
                }
                phases.add(new QuestTrackerPhaseEntry(
                        phaseTitle,
                        List.copyOf(objectives)
                ));
            }
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
            entry = new QuestTrackerEntry(
                    questId,
                    title,
                    List.copyOf(phases),
                    marker
            );
        }
        ManualReadPromptState manualReadPrompt = null;
        if (buffer.readBoolean()) {
            manualReadPrompt = new ManualReadPromptState(buffer.readUtf(), buffer.readUtf(), buffer.readUtf());
        }
        return new SyncQuestTrackerS2CPacket(entry, manualReadPrompt);
    }

    public static void handle(SyncQuestTrackerS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientPacketBridge.handle(packet));
        context.setPacketHandled(true);
    }
}
