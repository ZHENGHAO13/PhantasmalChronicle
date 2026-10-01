package com.phantasm.briefing.capability;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;

public final class ModCapabilities {
    public static final Capability<QuestNpcData> QUEST_NPC = CapabilityManager.get(new CapabilityToken<>() {
    });

    private ModCapabilities() {
    }

    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.register(QuestNpcData.class);
    }
}
