package com.phantasm.briefing.service;

import com.phantasm.briefing.data.ShopDataManager;
import com.phantasm.briefing.data.ShopItemSpec;
import com.phantasm.briefing.data.ShopOfferSpec;
import com.phantasm.briefing.data.ShopSpec;
import com.phantasm.briefing.data.TradeCategorySpec;
import com.phantasm.briefing.data.TradeDataManager;
import com.phantasm.briefing.data.TradeEntrySpec;
import com.phantasm.briefing.data.TradeSpec;
import com.phantasm.briefing.data.WalletDataManager;
import com.phantasm.briefing.data.WalletShopOfferEntry;
import com.phantasm.briefing.data.WalletSpec;
import com.phantasm.briefing.network.ModNetwork;
import com.phantasm.briefing.network.packet.OpenWalletScreenS2CPacket;
import com.phantasm.briefing.network.packet.OpenWalletShopS2CPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DialogueShopService {
    private static final ConcurrentHashMap<UUID, ActiveShopSession> ACTIVE_SHOP_TERMINALS = new ConcurrentHashMap<>();
    private static final String SESSION_KIND_SHOP = "shop";
    private static final String SESSION_KIND_TRADE = "trade";

    private DialogueShopService() {
    }

    public static boolean openShop(ServerPlayer player, String shopId) {
        return ShopDataManager.getInstance().getShop(shopId)
                .map(shop -> openShop(player, shop))
                .or(() -> TradeDataManager.getInstance().getTrade(shopId).map(trade -> openTrade(player, trade)))
                .orElse(false);
    }

    public static boolean openShop(ServerPlayer player, ShopSpec shop) {
        if (!shop.canOpen(player)) {
            return false;
        }
        return resendShopTerminal(player, shop);
    }

    public static boolean openTrade(ServerPlayer player, TradeSpec trade) {
        if (!trade.canOpen(player)) {
            return false;
        }
        return resendTradeTerminal(player, trade);
    }

    public static void openWalletOverview(ServerPlayer player) {
        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new OpenWalletScreenS2CPacket(WalletService.buildEntries(player))
        );
    }

    public static void closeWalletShop(ServerPlayer player, String shopId) {
        ACTIVE_SHOP_TERMINALS.computeIfPresent(player.getUUID(), (uuid, activeSession) ->
                activeSession.sessionId().equals(shopId) ? null : activeSession
        );
    }

    public static void clear(ServerPlayer player) {
        ACTIVE_SHOP_TERMINALS.remove(player.getUUID());
    }

    public static boolean purchaseWalletOffer(ServerPlayer player, String shopId, String entryId) {
        ActiveShopSession activeSession = ACTIVE_SHOP_TERMINALS.get(player.getUUID());
        if (activeSession == null || !activeSession.sessionId().equals(shopId)) {
            player.sendSystemMessage(Component.literal("[PhantasmBriefing] 当前没有打开该交易终端。"));
            return false;
        }
        if (SESSION_KIND_TRADE.equals(activeSession.sessionKind())) {
            return purchaseTradeOffer(player, shopId, entryId);
        }
        return purchaseShopOffer(player, shopId, entryId);
    }

    private static boolean purchaseShopOffer(ServerPlayer player, String shopId, String entryId) {
        ShopSpec shop = ShopDataManager.getInstance().getShop(shopId).orElse(null);
        if (shop == null) {
            player.sendSystemMessage(Component.literal("[PhantasmBriefing] 商店不存在：" + shopId));
            return false;
        }

        ShopOfferSpec offer = findShopOffer(shop, entryId);
        if (offer == null) {
            player.sendSystemMessage(Component.literal("[PhantasmBriefing] 商店条目不存在：" + entryId));
            resendShopTerminal(player, shop);
            return false;
        }
        if (!shop.isOfferUnlocked(player, offer)) {
            player.sendSystemMessage(Component.literal("[PhantasmBriefing] 该商品尚未解锁。"));
            resendShopTerminal(player, shop);
            return false;
        }

        ItemStack reward = offer.result().toItemStack();
        if (reward.isEmpty()) {
            player.sendSystemMessage(Component.literal("[PhantasmBriefing] 该商品奖励为空，无法购买。"));
            resendShopTerminal(player, shop);
            return false;
        }

        if (shop.isWalletShop()) {
            WalletSpec wallet = WalletDataManager.getInstance().getWallet(shop.walletId()).orElse(null);
            if (wallet == null) {
                player.sendSystemMessage(Component.literal("[PhantasmBriefing] 钱包货币不存在：" + shop.walletId()));
                return false;
            }
            if (!WalletService.trySpend(player, shop.walletId(), offer.walletCost())) {
                player.sendSystemMessage(Component.literal("[PhantasmBriefing] " + wallet.currencyName() + "不足。"));
                resendShopTerminal(player, shop);
                return false;
            }
        } else {
            List<ShopItemSpec> costs = new ArrayList<>();
            if (offer.costA() != null) {
                costs.add(offer.costA());
            }
            if (offer.costB() != null) {
                costs.add(offer.costB());
            }
            if (!tryConsumeItems(player, costs)) {
                player.sendSystemMessage(Component.literal("[PhantasmBriefing] 材料不足，无法完成交易。"));
                resendShopTerminal(player, shop);
                return false;
            }
        }

        grantStack(player, reward);
        player.sendSystemMessage(Component.literal(
                "[PhantasmBriefing] 已购买 " + reward.getHoverName().getString() + " x" + reward.getCount()
        ));
        resendShopTerminal(player, shop);
        return true;
    }

    private static boolean purchaseTradeOffer(ServerPlayer player, String tradeId, String entryId) {
        TradeSpec trade = TradeDataManager.getInstance().getTrade(tradeId).orElse(null);
        if (trade == null) {
            player.sendSystemMessage(Component.literal("[PhantasmBriefing] 交易不存在：" + tradeId));
            return false;
        }
        TradeEntrySpec entry = findTradeEntry(trade, entryId);
        if (entry == null) {
            player.sendSystemMessage(Component.literal("[PhantasmBriefing] 交易条目不存在：" + entryId));
            resendTradeTerminal(player, trade);
            return false;
        }
        if (!entry.isUnlocked(player)) {
            player.sendSystemMessage(Component.literal("[PhantasmBriefing] 该交易条目尚未解锁。"));
            resendTradeTerminal(player, trade);
            return false;
        }
        if (!tryConsumeItems(player, entry.costs())) {
            player.sendSystemMessage(Component.literal("[PhantasmBriefing] 材料不足，无法完成交易。"));
            resendTradeTerminal(player, trade);
            return false;
        }
        ItemStack reward = entry.rewards().get(0).toItemStack();
        if (reward.isEmpty()) {
            player.sendSystemMessage(Component.literal("[PhantasmBriefing] 该交易奖励为空，无法发放。"));
            resendTradeTerminal(player, trade);
            return false;
        }
        grantStack(player, reward);
        player.sendSystemMessage(Component.literal(
                "[PhantasmBriefing] 已完成交易，获得 " + reward.getHoverName().getString() + " x" + reward.getCount()
        ));
        resendTradeTerminal(player, trade);
        return true;
    }

    private static boolean resendShopTerminal(ServerPlayer player, ShopSpec shop) {
        WalletSpec wallet = shop.isWalletShop()
                ? WalletDataManager.getInstance().getWallet(shop.walletId()).orElse(null)
                : null;
        if (shop.isWalletShop() && wallet == null) {
            return false;
        }

        List<WalletShopOfferEntry> offers = buildShopEntries(player, shop, wallet);
        if (offers.isEmpty()) {
            return false;
        }

        String subtitle = shop.isWalletShop() ? "钱包商店终端" : "物资交易终端";
        String balanceLabel = shop.isWalletShop()
                ? wallet.currencyName() + " : " + WalletService.getBalance(player, wallet.walletId())
                : "支付方式：物品交换";
        ACTIVE_SHOP_TERMINALS.put(player.getUUID(), new ActiveShopSession(shop.shopId(), SESSION_KIND_SHOP));
        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new OpenWalletShopS2CPacket(
                        shop.shopId(),
                        shop.title(),
                        subtitle,
                        balanceLabel,
                        wallet == null ? "" : wallet.iconItemId(),
                        offers
                )
        );
        return true;
    }

    private static boolean resendTradeTerminal(ServerPlayer player, TradeSpec trade) {
        List<WalletShopOfferEntry> offers = buildTradeEntries(player, trade);
        if (offers.isEmpty()) {
            return false;
        }

        String subtitle = trade.description().isBlank() ? "独立交易终端" : trade.description();
        ACTIVE_SHOP_TERMINALS.put(player.getUUID(), new ActiveShopSession(trade.tradeId(), SESSION_KIND_TRADE));
        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new OpenWalletShopS2CPacket(
                        trade.tradeId(),
                        trade.title(),
                        subtitle,
                        "支付方式：以物易物",
                        "",
                        offers
                )
        );
        return true;
    }

    private static List<WalletShopOfferEntry> buildShopEntries(ServerPlayer player, ShopSpec shop, WalletSpec wallet) {
        Map<String, String> categoryTitles = toCategoryTitleMap(shop.categories());
        int walletBalance = wallet == null ? 0 : WalletService.getBalance(player, wallet.walletId());
        List<WalletShopOfferEntry> entries = new ArrayList<>();
        for (ShopOfferSpec offer : shop.sortedOffers()) {
            boolean unlocked = shop.isOfferUnlocked(player, offer);
            if (!unlocked && offer.hideUntilUnlocked()) {
                continue;
            }

            ItemStack result = offer.result().toItemStack();
            String title = offer.title().isBlank()
                    ? (result.isEmpty() ? offer.result().itemId() : result.getHoverName().getString())
                    : offer.title();
            boolean affordable = wallet != null
                    ? walletBalance >= offer.walletCost()
                    : hasRequiredItems(player, collectShopCosts(offer));
            entries.add(new WalletShopOfferEntry(
                    offer.offerId(),
                    offer.categoryId(),
                    categoryTitles.getOrDefault(offer.categoryId(), ""),
                    title,
                    offer.description(),
                    offer.result().itemId(),
                    offer.result().count(),
                    wallet != null
                            ? offer.walletCost() + " " + wallet.currencyName()
                            : formatItemList(collectShopCosts(offer)),
                    describeShopReward(offer, result, title),
                    !unlocked ? "需要前置任务或条件" : affordable ? "可购买" : wallet != null ? "余额不足" : "材料不足",
                    wallet != null ? wallet.iconItemId() : firstCostItemId(offer),
                    wallet != null ? offer.walletCost() : firstCostCount(offer),
                    wallet != null ? "" : secondCostItemId(offer),
                    wallet != null ? 0 : secondCostCount(offer),
                    !unlocked ? WalletShopOfferEntry.STATUS_LOCKED : affordable ? WalletShopOfferEntry.STATUS_READY : WalletShopOfferEntry.STATUS_SHORT,
                    unlocked,
                    unlocked && affordable,
                    wallet != null
            ));
        }
        return List.copyOf(entries);
    }

    private static List<WalletShopOfferEntry> buildTradeEntries(ServerPlayer player, TradeSpec trade) {
        Map<String, String> categoryTitles = toCategoryTitleMap(trade.categories());
        List<WalletShopOfferEntry> entries = new ArrayList<>();
        for (TradeEntrySpec entry : sortedTradeEntries(trade)) {
            boolean unlocked = entry.isUnlocked(player);
            if (!unlocked && entry.hideUntilUnlocked()) {
                continue;
            }

            ItemStack reward = entry.rewards().get(0).toItemStack();
            String rewardTitle = reward.isEmpty() ? entry.rewards().get(0).itemId() : reward.getHoverName().getString();
            boolean affordable = hasRequiredItems(player, entry.costs());
            entries.add(new WalletShopOfferEntry(
                    entry.entryId(),
                    entry.categoryId(),
                    categoryTitles.getOrDefault(entry.categoryId(), ""),
                    entry.title().isBlank() ? rewardTitle : entry.title(),
                    "",
                    entry.rewards().get(0).itemId(),
                    entry.rewards().get(0).count(),
                    formatItemList(entry.costs()),
                    rewardTitle + " x" + entry.rewards().get(0).count(),
                    !unlocked ? "需要前置任务或条件" : affordable ? "可交换" : "材料不足",
                    entry.costs().isEmpty() ? "" : entry.costs().get(0).itemId(),
                    entry.costs().isEmpty() ? 0 : entry.costs().get(0).count(),
                    entry.costs().size() > 1 ? entry.costs().get(1).itemId() : "",
                    entry.costs().size() > 1 ? entry.costs().get(1).count() : 0,
                    !unlocked ? WalletShopOfferEntry.STATUS_LOCKED : affordable ? WalletShopOfferEntry.STATUS_READY : WalletShopOfferEntry.STATUS_SHORT,
                    unlocked,
                    unlocked && affordable,
                    false
            ));
        }
        return List.copyOf(entries);
    }

    private static ShopOfferSpec findShopOffer(ShopSpec shop, String entryId) {
        return shop.offers().stream()
                .filter(offer -> offer.offerId().equals(entryId))
                .findFirst()
                .orElse(null);
    }

    private static TradeEntrySpec findTradeEntry(TradeSpec trade, String entryId) {
        return sortedTradeEntries(trade).stream()
                .filter(entry -> entry.entryId().equals(entryId))
                .findFirst()
                .orElse(null);
    }

    private static List<TradeEntrySpec> sortedTradeEntries(TradeSpec trade) {
        Map<String, Integer> categoryOrder = new LinkedHashMap<>();
        for (TradeCategorySpec category : trade.categories()) {
            categoryOrder.put(category.categoryId(), category.sortOrder());
        }
        return trade.entries().stream()
                .sorted(Comparator
                        .comparingInt((TradeEntrySpec entry) -> categoryOrder.getOrDefault(entry.categoryId(), Integer.MAX_VALUE))
                        .thenComparingInt(TradeEntrySpec::sortOrder)
                        .thenComparing(TradeEntrySpec::entryId, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static Map<String, String> toCategoryTitleMap(List<TradeCategorySpec> categories) {
        Map<String, String> titles = new LinkedHashMap<>();
        for (TradeCategorySpec category : categories) {
            titles.put(category.categoryId(), category.title());
        }
        return titles;
    }

    private static List<ShopItemSpec> collectShopCosts(ShopOfferSpec offer) {
        List<ShopItemSpec> costs = new ArrayList<>();
        if (offer.costA() != null) {
            costs.add(offer.costA());
        }
        if (offer.costB() != null) {
            costs.add(offer.costB());
        }
        return costs;
    }

    private static boolean hasRequiredItems(ServerPlayer player, List<ShopItemSpec> costs) {
        Map<String, Integer> required = mergeRequiredItems(costs);
        if (required.isEmpty()) {
            return true;
        }

        Map<String, Integer> available = new LinkedHashMap<>();
        for (ItemStack stack : player.getInventory().items) {
            addAvailableCount(available, stack);
        }
        for (ItemStack stack : player.getInventory().offhand) {
            addAvailableCount(available, stack);
        }
        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            if (available.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private static boolean tryConsumeItems(ServerPlayer player, List<ShopItemSpec> costs) {
        Map<String, Integer> required = mergeRequiredItems(costs);
        if (required.isEmpty()) {
            return true;
        }
        if (!hasRequiredItems(player, costs)) {
            return false;
        }

        consumeFromStacks(player.getInventory().items, required);
        consumeFromStacks(player.getInventory().offhand, required);
        player.inventoryMenu.broadcastChanges();
        return true;
    }

    private static void consumeFromStacks(List<ItemStack> stacks, Map<String, Integer> required) {
        for (ItemStack stack : stacks) {
            if (required.isEmpty()) {
                return;
            }
            String itemId = getItemId(stack);
            if (itemId.isBlank()) {
                continue;
            }
            int remaining = required.getOrDefault(itemId, 0);
            if (remaining <= 0) {
                continue;
            }
            int consumed = Math.min(remaining, stack.getCount());
            stack.shrink(consumed);
            if (remaining == consumed) {
                required.remove(itemId);
            } else {
                required.put(itemId, remaining - consumed);
            }
        }
    }

    private static Map<String, Integer> mergeRequiredItems(List<ShopItemSpec> costs) {
        Map<String, Integer> required = new LinkedHashMap<>();
        for (ShopItemSpec item : costs) {
            required.merge(item.itemId(), item.count(), Integer::sum);
        }
        return required;
    }

    private static void addAvailableCount(Map<String, Integer> available, ItemStack stack) {
        String itemId = getItemId(stack);
        if (!itemId.isBlank()) {
            available.merge(itemId, stack.getCount(), Integer::sum);
        }
    }

    private static String getItemId(ItemStack stack) {
        if (stack.isEmpty()) {
            return "";
        }
        ResourceLocation itemKey = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return itemKey == null ? "" : itemKey.toString();
    }

    private static String formatItemList(List<ShopItemSpec> items) {
        if (items.isEmpty()) {
            return "无花费";
        }
        return items.stream()
                .map(DialogueShopService::describeItem)
                .reduce((left, right) -> left + " + " + right)
                .orElse("无花费");
    }

    private static String describeShopReward(ShopOfferSpec offer, ItemStack result, String resolvedTitle) {
        if (offer.description().isBlank()) {
            return resolvedTitle + " x" + offer.result().count();
        }
        return resolvedTitle + " x" + offer.result().count();
    }

    private static String describeItem(ShopItemSpec item) {
        ItemStack stack = item.toItemStack();
        String title = stack.isEmpty() ? item.itemId() : stack.getHoverName().getString();
        return title + " x" + item.count();
    }

    private static String firstCostItemId(ShopOfferSpec offer) {
        return offer.costA() == null ? "" : offer.costA().itemId();
    }

    private static int firstCostCount(ShopOfferSpec offer) {
        return offer.costA() == null ? 0 : offer.costA().count();
    }

    private static String secondCostItemId(ShopOfferSpec offer) {
        return offer.costB() == null ? "" : offer.costB().itemId();
    }

    private static int secondCostCount(ShopOfferSpec offer) {
        return offer.costB() == null ? 0 : offer.costB().count();
    }

    private static void grantStack(ServerPlayer player, ItemStack reward) {
        ItemStack remaining = reward.copy();
        player.getInventory().add(remaining);
        if (!remaining.isEmpty()) {
            player.drop(remaining, false);
        }
        player.inventoryMenu.broadcastChanges();
    }

    private record ActiveShopSession(String sessionId, String sessionKind) {
    }
}
