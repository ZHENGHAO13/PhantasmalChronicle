package com.phantasm.briefing.network.packet;

import com.phantasm.briefing.network.ClientPacketBridge;
import com.phantasm.briefing.data.WalletShopOfferEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

public record OpenWalletShopS2CPacket(
        String shopId,
        String shopTitle,
        String shopSubtitle,
        String balanceLabel,
        String balanceIconItemId,
        List<WalletShopOfferEntry> offers
) {

    public static void encode(OpenWalletShopS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.shopId());
        buffer.writeUtf(packet.shopTitle());
        buffer.writeUtf(packet.shopSubtitle());
        buffer.writeUtf(packet.balanceLabel());
        buffer.writeUtf(packet.balanceIconItemId());
        buffer.writeCollection(packet.offers(), (buf, entry) -> {
            buf.writeUtf(entry.entryId());
            buf.writeUtf(entry.categoryId());
            buf.writeUtf(entry.categoryTitle());
            buf.writeUtf(entry.title());
            buf.writeUtf(entry.description());
            buf.writeUtf(entry.resultItemId());
            buf.writeInt(entry.resultCount());
            buf.writeUtf(entry.costSummary());
            buf.writeUtf(entry.rewardSummary());
            buf.writeUtf(entry.stateText());
            buf.writeUtf(entry.primaryCostItemId());
            buf.writeInt(entry.primaryCostCount());
            buf.writeUtf(entry.secondaryCostItemId());
            buf.writeInt(entry.secondaryCostCount());
            buf.writeUtf(entry.statusBadge());
            buf.writeBoolean(entry.unlocked());
            buf.writeBoolean(entry.affordable());
            buf.writeBoolean(entry.walletPayment());
        });
    }

    public static OpenWalletShopS2CPacket decode(FriendlyByteBuf buffer) {
        return new OpenWalletShopS2CPacket(
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readList(buf -> new WalletShopOfferEntry(
                        buf.readUtf(),
                        buf.readUtf(),
                        buf.readUtf(),
                        buf.readUtf(),
                        buf.readUtf(),
                        buf.readUtf(),
                        buf.readInt(),
                        buf.readUtf(),
                        buf.readUtf(),
                        buf.readUtf(),
                        buf.readUtf(),
                        buf.readInt(),
                        buf.readUtf(),
                        buf.readInt(),
                        buf.readUtf(),
                        buf.readBoolean(),
                        buf.readBoolean(),
                        buf.readBoolean()
                ))
        );
    }

    public static void handle(OpenWalletShopS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientPacketBridge.handle(packet));
        context.setPacketHandled(true);
    }
}
