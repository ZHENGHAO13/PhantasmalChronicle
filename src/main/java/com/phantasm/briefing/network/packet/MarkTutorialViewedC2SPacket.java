package com.phantasm.briefing.network.packet;

import com.phantasm.briefing.service.QuestRuntimeService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record MarkTutorialViewedC2SPacket(
        String questId,
        String phaseId,
        String objectiveId,
        String manualId,
        String lessonId,
        String pageId
) {
    public MarkTutorialViewedC2SPacket(String questId, String phaseId, String objectiveId) {
        this(questId, phaseId, objectiveId, "", "", "");
    }

    public static void encode(MarkTutorialViewedC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.questId());
        buffer.writeUtf(packet.phaseId());
        buffer.writeUtf(packet.objectiveId());
        buffer.writeUtf(packet.manualId());
        buffer.writeUtf(packet.lessonId());
        buffer.writeUtf(packet.pageId());
    }

    public static MarkTutorialViewedC2SPacket decode(FriendlyByteBuf buffer) {
        return new MarkTutorialViewedC2SPacket(buffer.readUtf(), buffer.readUtf(), buffer.readUtf(),
                buffer.readUtf(), buffer.readUtf(), buffer.readUtf());
    }

    public static void handle(MarkTutorialViewedC2SPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player != null) {
                QuestRuntimeService.markTutorialViewed(
                        player,
                        packet.questId(),
                        packet.phaseId(),
                        packet.objectiveId(),
                        packet.manualId(),
                        packet.lessonId(),
                        packet.pageId()
                );
            }
        });
        context.setPacketHandled(true);
    }
}
