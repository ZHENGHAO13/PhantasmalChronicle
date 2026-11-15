package com.phantasm.briefing.event;

import com.phantasm.briefing.service.BriefingPlayerData;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class PlayerDataEvents {
    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        BriefingPlayerData.copyOnClone(event.getOriginal(), event.getEntity());
    }
}
