package com.phantasm.briefing.data;

import com.mojang.logging.LogUtils;

import java.util.Optional;

public final class TradeDataManager extends AbstractJsonDataManager<TradeSpec> {
    private static final TradeDataManager INSTANCE = new TradeDataManager();

    private TradeDataManager() {
        super("phantasm_trades", "trade", TradeSpec::fromJson, TradeSpec::tradeId, LogUtils.getLogger());
    }

    public static TradeDataManager getInstance() {
        return INSTANCE;
    }

    public Optional<TradeSpec> getTrade(String tradeId) {
        return find(tradeId);
    }
}
