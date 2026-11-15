package com.phantasm.briefing.event;

import com.phantasm.briefing.service.QuestEntityHintService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class QuestEntityHintEvents {
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (event.player instanceof ServerPlayer player) {
            QuestEntityHintService.tick(player);
        }
    }
}
