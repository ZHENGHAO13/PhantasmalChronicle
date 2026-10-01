package com.phantasm.briefing.network.packet;

import com.phantasm.briefing.client.ClientDialogueSession;
import com.phantasm.briefing.data.DialogueContentEntry;
import com.phantasm.briefing.data.DialogueContentType;
import com.phantasm.briefing.data.DialogueOption;
import com.phantasm.briefing.data.DialogueNode;
import com.phantasm.briefing.data.DialoguePresentationMode;
import com.phantasm.briefing.data.DialogueVoiceDataManager;
import com.phantasm.briefing.data.NpcTypewriterSoundSpec;
import com.phantasm.briefing.dialogue.sound.DialogueVoiceDefinition;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

public record OpenDialogueNodeS2CPacket(
        String nodeId,
        String title,
        List<DialogueContentEntry> contents,
        boolean triggerVFX,
        DialoguePresentationMode presentationMode,
        int speakerEntityId,
        boolean completionRequired,
        boolean typewriterSoundEnabled,
        String typewriterSoundEventId,
        float typewriterSoundVolume,
        List<DialogueOption> options
) {

    public static OpenDialogueNodeS2CPacket from(DialogueNode node, List<DialogueOption> options) {
        return from(node, options, -1, NpcTypewriterSoundSpec.defaults());
    }

    public static OpenDialogueNodeS2CPacket from(DialogueNode node, List<DialogueOption> options, int speakerEntityId) {
        return from(node, options, speakerEntityId, NpcTypewriterSoundSpec.defaults());
    }

    public static OpenDialogueNodeS2CPacket from(
            DialogueNode node,
            List<DialogueOption> options,
            int speakerEntityId,
            NpcTypewriterSoundSpec typewriterSound
    ) {
        ResolvedVoice sound = resolveVoice(typewriterSound);
        return new OpenDialogueNodeS2CPacket(
                node.nodeId(),
                node.title(),
                node.contents(),
                node.triggerVFX(),
                node.presentationMode(),
                speakerEntityId,
                true,
                sound.enabled(),
                sound.soundEventId(),
                sound.volume(),
                List.copyOf(options)
        );
    }

    public static OpenDialogueNodeS2CPacket notice(
            String noticeId,
            String title,
            List<String> lines,
            int speakerEntityId
    ) {
        return notice(noticeId, title, lines, speakerEntityId, NpcTypewriterSoundSpec.defaults());
    }

    public static OpenDialogueNodeS2CPacket notice(
            String noticeId,
            String title,
            List<String> lines,
            int speakerEntityId,
            NpcTypewriterSoundSpec typewriterSound
    ) {
        ResolvedVoice sound = resolveVoice(typewriterSound);
        return new OpenDialogueNodeS2CPacket(
                noticeId,
                title,
                lines.stream().map(DialogueContentEntry::text).toList(),
                false,
                DialoguePresentationMode.CINEMATIC,
                speakerEntityId,
                false,
                sound.enabled(),
                sound.soundEventId(),
                sound.volume(),
                List.of()
        );
    }

    private static ResolvedVoice resolveVoice(NpcTypewriterSoundSpec configuredSound) {
        NpcTypewriterSoundSpec sound = configuredSound == null
                ? NpcTypewriterSoundSpec.defaults()
                : configuredSound;
        DialogueVoiceDefinition definition = DialogueVoiceDataManager.getInstance().resolveOrDefault(sound.soundId());
        float effectiveVolume = sound.volume() * definition.defaultVolume();
        if (!Float.isFinite(effectiveVolume)) {
            effectiveVolume = 1.0F;
        }
        effectiveVolume = Math.max(0.0F, Math.min(2.0F, effectiveVolume));
        return new ResolvedVoice(sound.enabled(), definition.soundEventId(), effectiveVolume);
    }

    public static void encode(OpenDialogueNodeS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.nodeId());
        buffer.writeUtf(packet.title());
        buffer.writeCollection(packet.contents(), (buf, entry) -> {
            buf.writeEnum(entry.type());
            buf.writeUtf(entry.value());
        });
        buffer.writeBoolean(packet.triggerVFX());
        buffer.writeEnum(packet.presentationMode());
        buffer.writeInt(packet.speakerEntityId());
        buffer.writeBoolean(packet.completionRequired());
        buffer.writeBoolean(packet.typewriterSoundEnabled());
        buffer.writeUtf(packet.typewriterSoundEventId());
        buffer.writeFloat(packet.typewriterSoundVolume());
        buffer.writeCollection(packet.options(), (buf, option) -> {
            buf.writeUtf(option.optionId());
            buf.writeUtf(option.label());
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
                buffer.readList(buf -> new DialogueContentEntry(
                        buf.readEnum(DialogueContentType.class),
                        buf.readUtf()
                )),
                buffer.readBoolean(),
                buffer.readEnum(DialoguePresentationMode.class),
                buffer.readInt(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readUtf(),
                buffer.readFloat(),
                buffer.readList(buf -> new DialogueOption(
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

    private record ResolvedVoice(boolean enabled, String soundEventId, float volume) {
    }
}
