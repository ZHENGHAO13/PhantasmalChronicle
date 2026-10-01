package com.phantasm.briefing.data;

import com.mojang.logging.LogUtils;

import java.util.Optional;

public final class ShopDataManager extends AbstractJsonDataManager<ShopSpec> {
    private static final ShopDataManager INSTANCE = new ShopDataManager();

    private ShopDataManager() {
        super("phantasm_shops", "shop", ShopSpec::fromJson, ShopSpec::shopId, LogUtils.getLogger());
    }

    public static ShopDataManager getInstance() {
        return INSTANCE;
    }

    public Optional<ShopSpec> getShop(String shopId) {
        return find(shopId);
    }
}
