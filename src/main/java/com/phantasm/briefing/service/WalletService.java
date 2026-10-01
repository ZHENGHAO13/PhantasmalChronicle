package com.phantasm.briefing.service;

import com.phantasm.briefing.data.WalletBalanceEntry;
import com.phantasm.briefing.data.WalletDataManager;
import com.phantasm.briefing.data.WalletSpec;
import com.phantasm.briefing.integration.LightmansCurrencyWalletBridge;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class WalletService {
    private WalletService() {
    }

    public static int getBalance(ServerPlayer player, String walletId) {
        WalletSpec wallet = WalletDataManager.getInstance().getWallet(walletId).orElse(null);
        if (wallet == null) {
            return 0;
        }

        if (LightmansCurrencyWalletBridge.supports(wallet)) {
            return LightmansCurrencyWalletBridge.getBalance(player, wallet);
        }

        int total = 0;
        Inventory inventory = player.getInventory();
        for (ItemStack stack : inventory.items) {
            if (matchesWallet(wallet, stack)) {
                total += stack.getCount();
            }
        }
        for (ItemStack stack : inventory.offhand) {
            if (matchesWallet(wallet, stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public static boolean trySpend(ServerPlayer player, String walletId, int amount) {
        if (amount <= 0) {
            return true;
        }

        WalletSpec wallet = WalletDataManager.getInstance().getWallet(walletId).orElse(null);
        if (wallet == null || getBalance(player, walletId) < amount) {
            return false;
        }

        if (LightmansCurrencyWalletBridge.supports(wallet)) {
            return LightmansCurrencyWalletBridge.trySpend(player, wallet, amount);
        }

        int remaining = amount;
        remaining = consumeFromList(player.getInventory().items, wallet, remaining);
        remaining = consumeFromList(player.getInventory().offhand, wallet, remaining);
        player.inventoryMenu.broadcastChanges();
        return remaining <= 0;
    }

    public static List<WalletBalanceEntry> buildEntries(ServerPlayer player) {
        List<WalletSpec> wallets = new ArrayList<>(WalletDataManager.getInstance().getAllWallets());
        wallets.sort(Comparator
                .comparingInt(WalletSpec::sortOrder)
                .thenComparing(WalletSpec::title, String.CASE_INSENSITIVE_ORDER));

        List<WalletBalanceEntry> entries = new ArrayList<>();
        for (WalletSpec wallet : wallets) {
            entries.add(new WalletBalanceEntry(
                    wallet.walletId(),
                    wallet.title(),
                    wallet.currencyName(),
                    getBalance(player, wallet.walletId())
            ));
        }
        return List.copyOf(entries);
    }

    private static int consumeFromList(List<ItemStack> stacks, WalletSpec wallet, int remaining) {
        for (ItemStack stack : stacks) {
            if (remaining <= 0) {
                break;
            }
            if (!matchesWallet(wallet, stack)) {
                continue;
            }

            int consumed = Math.min(remaining, stack.getCount());
            stack.shrink(consumed);
            remaining -= consumed;
        }
        return remaining;
    }

    private static boolean matchesWallet(WalletSpec wallet, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        ResourceLocation itemKey = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemKey == null) {
            return false;
        }
        return wallet.itemIds().contains(itemKey.toString());
    }
}
