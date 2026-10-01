package com.phantasm.briefing.network.packet;

import com.phantasm.briefing.service.DialogueShopService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record PurchaseWalletShopOfferC2SPacket(
        String shopId,
        String entryId
) {

    public static void encode(PurchaseWalletShopOfferC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.shopId());
        buffer.writeUtf(packet.entryId());
    }

    public static PurchaseWalletShopOfferC2SPacket decode(FriendlyByteBuf buffer) {
        return new PurchaseWalletShopOfferC2SPacket(buffer.readUtf(), buffer.readUtf());
    }

    public static void handle(PurchaseWalletShopOfferC2SPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                DialogueShopService.purchaseWalletOffer(player, packet.shopId(), packet.entryId());
            }
        });
        context.setPacketHandled(true);
    }
}
