package com.phantasm.briefing.network.packet;

import com.phantasm.briefing.client.ClientDialogueSession;
import com.phantasm.briefing.data.DialogueOption;
import com.phantasm.briefing.data.DialogueNode;
import com.phantasm.briefing.data.DialoguePresentationMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

public record OpenDialogueNodeS2CPacket(
        String nodeId,
        String title,
        List<String> lines,
        boolean triggerVFX,
        DialoguePresentationMode presentationMode,
        int speakerEntityId,
        boolean completionRequired,
        List<DialogueOption> options
) {

    public static OpenDialogueNodeS2CPacket from(DialogueNode node, List<DialogueOption> options) {
        return from(node, options, -1);
    }

    public static OpenDialogueNodeS2CPacket from(DialogueNode node, List<DialogueOption> options, int speakerEntityId) {
        return new OpenDialogueNodeS2CPacket(
                node.nodeId(),
                node.title(),
                node.lines(),
                node.triggerVFX(),
                node.presentationMode(),
                speakerEntityId,
                true,
                List.copyOf(options)
        );
    }

    public static OpenDialogueNodeS2CPacket notice(
            String noticeId,
            String title,
            List<String> lines,
            int speakerEntityId
    ) {
        return new OpenDialogueNodeS2CPacket(
                noticeId,
                title,
                List.copyOf(lines),
                false,
                DialoguePresentationMode.CINEMATIC,
                speakerEntityId,
                false,
                List.of()
        );
    }

    public static void encode(OpenDialogueNodeS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.nodeId());
        buffer.writeUtf(packet.title());
        buffer.writeCollection(packet.lines(), FriendlyByteBuf::writeUtf);
        buffer.writeBoolean(packet.triggerVFX());
        buffer.writeEnum(packet.presentationMode());
        buffer.writeInt(packet.speakerEntityId());
        buffer.writeBoolean(packet.completionRequired());
        buffer.writeCollection(packet.options(), (buf, option) -> {
            buf.writeUtf(option.optionId());
            buf.writeUtf(option.label());
            buf.writeUtf(option.requiredQuestId());
            buf.writeCollection(option.actions(), (actionBuf, action) -> {
                actionBuf.writeEnum(action.type());
                actionBuf.writeUtf(action.targetId());
            });
        });
    }

    public static OpenDialogueNodeS2CPacket decode(FriendlyByteBuf buffer) {
        return new OpenDialogueNodeS2CPacket(
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readList(FriendlyByteBuf::readUtf),
                buffer.readBoolean(),
                buffer.readEnum(DialoguePresentationMode.class),
                buffer.readInt(),
                buffer.readBoolean(),
                buffer.readList(buf -> new DialogueOption(
                        buf.readUtf(),
                        buf.readUtf(),
                        buf.readUtf(),
                        buf.readList(actionBuf -> new com.phantasm.briefing.data.DialogueAction(
                                actionBuf.readEnum(com.phantasm.briefing.data.DialogueActionType.class),
                                actionBuf.readUtf()
                        )),
                        List.of()
                ))
        );
    }

    public static void handle(OpenDialogueNodeS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientDialogueSession.getInstance().openNode(packet));
        context.setPacketHandled(true);
    }
}
