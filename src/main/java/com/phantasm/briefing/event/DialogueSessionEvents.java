package com.phantasm.briefing.event;

import com.phantasm.briefing.network.ModNetwork;
import com.phantasm.briefing.network.packet.SetQuestTrackerHudVisibilityS2CPacket;
import com.phantasm.briefing.service.BriefingPlayerData;
import com.phantasm.briefing.service.DialogueFlowCoordinator;
import com.phantasm.briefing.service.QuestEntityHintService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

public final class DialogueSessionEvents {
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            ModNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new SetQuestTrackerHudVisibilityS2CPacket(BriefingPlayerData.isTrackerHudEnabled(serverPlayer))
            );
            QuestEntityHintService.requestSync(serverPlayer);
        }
    }


    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer serverPlayer) {
            DialogueFlowCoordinator.validateActiveSession(serverPlayer);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            QuestEntityHintService.clear(serverPlayer);
        }
        DialogueFlowCoordinator.clearPlayerSession(event.getEntity().getUUID());
    }
}
