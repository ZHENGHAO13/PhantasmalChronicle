package com.phantasm.briefing.integration;

import com.phantasm.briefing.PhantasmBriefing;
import com.phantasm.briefing.data.WalletSpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;

public final class LightmansCurrencyWalletBridge {
    private static final String MOD_ID = "lightmanscurrency";
    private static volatile Access access;
    private static volatile boolean resolved;

    private LightmansCurrencyWalletBridge() {
    }

    public static boolean supports(WalletSpec wallet) {
        if (!ModList.get().isLoaded(MOD_ID) || wallet == null) {
            return false;
        }
        Access api = access();
        return api != null && api.isCoin(resolveIconItem(wallet));
    }

    public static int getBalance(ServerPlayer player, WalletSpec wallet) {
        Access api = access();
        Item coin = resolveIconItem(wallet);
        if (api == null || coin == null || !api.isCoin(coin)) {
            return 0;
        }
        try {
            Object unitValue = api.fromItemOrValue.invoke(null, coin, 1L);
            long unit = ((Number) api.getCoreValue.invoke(unitValue)).longValue();
            Object funds = api.getPlayersAvailableFunds.invoke(null, player);
            Object available = api.valueOf.invoke(funds, api.getUniqueName.invoke(unitValue));
            long total = ((Number) api.getCoreValue.invoke(available)).longValue();
            return unit <= 0L ? 0 : (int) Math.min(Integer.MAX_VALUE, total / unit);
        } catch (ReflectiveOperationException exception) {
            PhantasmBriefing.LOGGER.warn("[PhantasmBriefing] Failed to read Lightman's Currency wallet: {}", exception.getMessage());
            return 0;
        }
    }

    public static boolean trySpend(ServerPlayer player, WalletSpec wallet, int amount) {
        if (amount <= 0) {
            return true;
        }
        Access api = access();
        Item coin = resolveIconItem(wallet);
        if (api == null || coin == null || !api.isCoin(coin)) {
            return false;
        }
        try {
            Object cost = api.fromItemOrValue.invoke(null, coin, (long) amount);
            boolean affordable = (boolean) api.canPlayerAfford.invoke(null, player, cost);
            if (!affordable) {
                return false;
            }
            return (boolean) api.takeMoneyFromPlayer.invoke(null, player, cost);
        } catch (ReflectiveOperationException exception) {
            PhantasmBriefing.LOGGER.warn("[PhantasmBriefing] Failed to spend Lightman's Currency wallet funds: {}", exception.getMessage());
            return false;
        }
    }

    private static Item resolveIconItem(WalletSpec wallet) {
        ResourceLocation id = ResourceLocation.tryParse(wallet.iconItemId());
        return id == null ? null : ForgeRegistries.ITEMS.getValue(id);
    }

    private static Access access() {
        if (!ModList.get().isLoaded(MOD_ID)) {
            return null;
        }
        if (resolved) {
            return access;
        }
        synchronized (LightmansCurrencyWalletBridge.class) {
            if (resolved) {
                return access;
            }
            try {
                access = new Access();
            } catch (ReflectiveOperationException exception) {
                PhantasmBriefing.LOGGER.warn("[PhantasmBriefing] Lightman's Currency wallet bridge unavailable: {}", exception.getMessage());
            } finally {
                resolved = true;
            }
            return access;
        }
    }

    private static final class Access {
        private final Method getPlayersAvailableFunds;
        private final Method canPlayerAfford;
        private final Method takeMoneyFromPlayer;
        private final Method fromItemOrValue;
        private final Method getCoreValue;
        private final Method getUniqueName;
        private final Method valueOf;
        private final Method chainDataOfCoin;

        private Access() throws ReflectiveOperationException {
            Class<?> moneyApi = Class.forName("io.github.lightman314.lightmanscurrency.api.money.MoneyAPI");
            Class<?> moneyValue = Class.forName("io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue");
            Class<?> moneyView = Class.forName("io.github.lightman314.lightmanscurrency.api.money.value.MoneyView");
            Class<?> coinValue = Class.forName("io.github.lightman314.lightmanscurrency.api.money.value.builtin.CoinValue");
            Class<?> coinApi = Class.forName("io.github.lightman314.lightmanscurrency.api.money.coins.CoinAPI");
            this.getPlayersAvailableFunds = moneyApi.getMethod("getPlayersAvailableFunds", Player.class);
            this.canPlayerAfford = moneyApi.getMethod("canPlayerAfford", Player.class, moneyValue);
            this.takeMoneyFromPlayer = moneyApi.getMethod("takeMoneyFromPlayer", Player.class, moneyValue);
            this.fromItemOrValue = coinValue.getMethod("fromItemOrValue", Item.class, long.class);
            this.getCoreValue = moneyValue.getMethod("getCoreValue");
            this.getUniqueName = moneyValue.getMethod("getUniqueName");
            this.valueOf = moneyView.getMethod("valueOf", String.class);
            this.chainDataOfCoin = coinApi.getMethod("ChainDataOfCoin", Item.class);
        }

        private boolean isCoin(Item item) {
            if (item == null) {
                return false;
            }
            try {
                return this.chainDataOfCoin.invoke(null, item) != null;
            } catch (ReflectiveOperationException ignored) {
                return false;
            }
        }
    }
}
