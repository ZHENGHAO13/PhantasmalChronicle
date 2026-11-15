package com.phantasm.briefing.event;

import com.phantasm.briefing.api.IQuestNPC;
import com.phantasm.briefing.capability.QuestNpcProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class QuestNpcCapabilityEvents {

    @SubscribeEvent
    public void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (!(event.getObject() instanceof LivingEntity livingEntity) || livingEntity instanceof IQuestNPC) {
            return;
        }

        QuestNpcProvider provider = new QuestNpcProvider();
        event.addCapability(QuestNpcProvider.ID, provider);
        event.addListener(provider::invalidate);
    }
}
