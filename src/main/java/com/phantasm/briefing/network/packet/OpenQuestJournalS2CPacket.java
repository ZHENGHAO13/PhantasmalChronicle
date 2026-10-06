package com.phantasm.briefing.network.packet;

import com.phantasm.briefing.network.ClientPacketBridge;
import com.phantasm.briefing.data.QuestJournalEntry;
import com.phantasm.briefing.data.ManualSpec;
import com.phantasm.briefing.data.ManualAutoOpenTarget;
import com.phantasm.briefing.service.ManualVisibilityService;
import com.phantasm.briefing.service.QuestJournalService;
import net.minecraft.server.level.ServerPlayer;
import com.phantasm.briefing.data.QuestJournalObjectiveEntry;
import com.phantasm.briefing.data.QuestJournalPhaseEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

public record OpenQuestJournalS2CPacket(
        List<QuestJournalEntry> entries,
        List<ManualSpec> manuals,
        ManualAutoOpenTarget autoOpenTarget
) {

    public static OpenQuestJournalS2CPacket forPlayer(ServerPlayer player) {
        return forPlayer(player, null);
    }

    public static OpenQuestJournalS2CPacket forPlayer(ServerPlayer player, ManualAutoOpenTarget autoOpenTarget) {
        return new OpenQuestJournalS2CPacket(QuestJournalService.buildEntries(player),
                ManualVisibilityService.visibleManuals(player), autoOpenTarget);
    }

    public static void encode(OpenQuestJournalS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeCollection(packet.entries(), (buf, entry) -> {
            buf.writeUtf(entry.questId());
            buf.writeUtf(entry.title());
            buf.writeUtf(entry.description());
            buf.writeCollection(entry.phases(), (phaseBuf, phase) -> {
                phaseBuf.writeUtf(phase.phaseId());
                phaseBuf.writeUtf(phase.title());
                phaseBuf.writeBoolean(phase.completed());
                phaseBuf.writeBoolean(phase.current());
                phaseBuf.writeCollection(phase.objectives(), OpenQuestJournalS2CPacket::writeObjective);
            });
            buf.writeEnum(entry.status());
            buf.writeBoolean(entry.tracked());
            buf.writeBoolean(entry.recommended());
            ManualPacketCodec.writeRefs(buf, entry.manualRefs());
        });
        ManualPacketCodec.writeManuals(buffer, packet.manuals());
        buffer.writeBoolean(packet.autoOpenTarget() != null);
        if (packet.autoOpenTarget() != null) {
            ManualAutoOpenTarget target = packet.autoOpenTarget();
            buffer.writeUtf(target.questId());
            buffer.writeUtf(target.phaseId());
            buffer.writeUtf(target.objectiveId());
            buffer.writeUtf(target.manualId());
            buffer.writeUtf(target.lessonId());
            buffer.writeUtf(target.pageId());
        }
    }

    public static OpenQuestJournalS2CPacket decode(FriendlyByteBuf buffer) {
        List<QuestJournalEntry> entries = buffer.readList(buf -> new QuestJournalEntry(
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readList(phaseBuf -> {
                    String phaseId = phaseBuf.readUtf();
                    String title = phaseBuf.readUtf();
                    boolean completed = phaseBuf.readBoolean();
                    boolean current = phaseBuf.readBoolean();
                    List<QuestJournalObjectiveEntry> objectives = phaseBuf.readList(OpenQuestJournalS2CPacket::readObjective);
                    return new QuestJournalPhaseEntry(phaseId, title, objectives, completed, current);
                }),
                buf.readEnum(com.phantasm.briefing.data.QuestRuntimeStatus.class),
                buf.readBoolean(),
                buf.readBoolean(),
                ManualPacketCodec.readRefs(buf)
        ));
        List<ManualSpec> manuals = ManualPacketCodec.readManuals(buffer);
        ManualAutoOpenTarget autoOpenTarget = null;
        if (buffer.readBoolean()) {
            autoOpenTarget = new ManualAutoOpenTarget(
                    buffer.readUtf(), buffer.readUtf(), buffer.readUtf(),
                    buffer.readUtf(), buffer.readUtf(), buffer.readUtf());
        }
        return new OpenQuestJournalS2CPacket(entries, manuals, autoOpenTarget);
    }

    private static void writeObjective(FriendlyByteBuf buffer, QuestJournalObjectiveEntry objective) {
        buffer.writeUtf(objective.objectiveId());
        buffer.writeUtf(objective.title());
        buffer.writeEnum(objective.objectiveType());
        buffer.writeVarInt(objective.progress());
        buffer.writeVarInt(objective.requiredCount());
        buffer.writeBoolean(objective.completed());
        buffer.writeBoolean(objective.active());
        buffer.writeBoolean(objective.manualReadable());
        ManualPacketCodec.writeRefs(buffer, objective.manualRefs());
    }

    private static QuestJournalObjectiveEntry readObjective(FriendlyByteBuf buffer) {
        return new QuestJournalObjectiveEntry(
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readEnum(com.phantasm.briefing.data.QuestObjectiveType.class),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                ManualPacketCodec.readRefs(buffer)
        );
    }

    public static void handle(OpenQuestJournalS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientPacketBridge.handle(packet));
        context.setPacketHandled(true);
    }
}
