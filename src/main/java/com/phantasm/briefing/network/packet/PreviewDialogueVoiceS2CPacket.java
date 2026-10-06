package com.phantasm.briefing.network.packet;

import com.phantasm.briefing.network.ClientPacketBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Plays one editor preview sound on the target client without requiring a registered SoundEvent. */
public record PreviewDialogueVoiceS2CPacket(String soundEventId, float volume) {
    public static void encode(PreviewDialogueVoiceS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.soundEventId());
        buffer.writeFloat(packet.volume());
    }

    public static PreviewDialogueVoiceS2CPacket decode(FriendlyByteBuf buffer) {
        return new PreviewDialogueVoiceS2CPacket(buffer.readUtf(), buffer.readFloat());
    }

    public static void handle(PreviewDialogueVoiceS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientPacketBridge.handle(packet));
        context.setPacketHandled(true);
    }
}
