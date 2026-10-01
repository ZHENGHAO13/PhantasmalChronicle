package com.phantasm.briefing.data;

import com.mojang.logging.LogUtils;

import java.util.Collection;
import java.util.Optional;

public final class WalletDataManager extends AbstractJsonDataManager<WalletSpec> {
    private static final WalletDataManager INSTANCE = new WalletDataManager();

    private WalletDataManager() {
        super("phantasm_wallet", "wallet", WalletSpec::fromJson, WalletSpec::walletId, LogUtils.getLogger());
    }

    public static WalletDataManager getInstance() {
        return INSTANCE;
    }

    public Optional<WalletSpec> getWallet(String walletId) {
        return find(walletId);
    }

    public Collection<WalletSpec> getAllWallets() {
        return values();
    }
}
