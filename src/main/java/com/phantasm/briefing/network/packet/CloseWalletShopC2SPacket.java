package com.phantasm.briefing.network.packet;

import com.phantasm.briefing.service.DialogueShopService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record CloseWalletShopC2SPacket(
        String shopId
) {

    public static void encode(CloseWalletShopC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.shopId());
    }

    public static CloseWalletShopC2SPacket decode(FriendlyByteBuf buffer) {
        return new CloseWalletShopC2SPacket(buffer.readUtf());
    }

    public static void handle(CloseWalletShopC2SPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                DialogueShopService.closeWalletShop(player, packet.shopId());
            }
        });
        context.setPacketHandled(true);
    }
}
