package com.phantasm.briefing.data;

import com.mojang.logging.LogUtils;

import java.util.Collection;
import java.util.Optional;

public final class ManualDataManager extends AbstractJsonDataManager<ManualSpec> {
    private static final ManualDataManager INSTANCE = new ManualDataManager();

    private ManualDataManager() {
        super("phantasm_manuals", "manual", ManualSpec::fromJson, ManualSpec::manualId, LogUtils.getLogger());
    }

    public static ManualDataManager getInstance() {
        return INSTANCE;
    }

    public Optional<ManualSpec> getManual(String manualId) {
        return find(manualId);
    }

    public Collection<ManualSpec> getAllManuals() {
        return values();
    }
}
