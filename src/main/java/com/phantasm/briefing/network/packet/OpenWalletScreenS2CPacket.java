package com.phantasm.briefing.network.packet;

import com.phantasm.briefing.client.screen.WalletScreen;
import com.phantasm.briefing.data.WalletBalanceEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

public record OpenWalletScreenS2CPacket(
        List<WalletBalanceEntry> entries
) {

    public static void encode(OpenWalletScreenS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeCollection(packet.entries(), (buf, entry) -> {
            buf.writeUtf(entry.walletId());
            buf.writeUtf(entry.title());
            buf.writeUtf(entry.currencyName());
            buf.writeInt(entry.balance());
        });
    }

    public static OpenWalletScreenS2CPacket decode(FriendlyByteBuf buffer) {
        return new OpenWalletScreenS2CPacket(buffer.readList(buf -> new WalletBalanceEntry(
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readInt()
        )));
    }

    public static void handle(OpenWalletScreenS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new WalletScreen(packet.entries())));
        context.setPacketHandled(true);
    }
}
