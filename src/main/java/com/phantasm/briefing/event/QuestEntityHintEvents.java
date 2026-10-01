package com.phantasm.briefing.event;

import com.phantasm.briefing.service.QuestEntityHintService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class QuestEntityHintEvents {
    @SubscribeEvent
    public void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            QuestEntityHintService.onStartTracking(player, event.getTarget());
        }
    }

    @SubscribeEvent
    public void onStopTracking(PlayerEvent.StopTracking event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            QuestEntityHintService.onStopTracking(player, event.getTarget());
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        QuestEntityHintService.clearAll();
    }

    /**
     * Lightweight scheduler only. The service returns immediately unless a hint-affecting
     * event, meaningful player movement, or one tracked NPC movement probe marks state dirty.
     */
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
