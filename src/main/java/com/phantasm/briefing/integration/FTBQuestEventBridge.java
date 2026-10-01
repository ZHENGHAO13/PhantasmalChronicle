package com.phantasm.briefing.integration;

import dev.architectury.event.EventResult;
import dev.ftb.mods.ftbquests.events.ObjectCompletedEvent;
import dev.ftb.mods.ftbquests.events.ObjectStartedEvent;
import net.minecraft.server.level.ServerPlayer;

public final class FTBQuestEventBridge {
    private static boolean registered;

    private FTBQuestEventBridge() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }

        ObjectCompletedEvent.QUEST.register(event -> {
            String questId = event.getQuest().getCodeString();
            for (ServerPlayer player : event.getOnlineMembers()) {
                FTBIntegrationHelper.onQuestCompleted(player, questId);
            }
            return EventResult.pass();
        });

        ObjectStartedEvent.QUEST.register(event -> {
            String questId = event.getQuest().getCodeString();
            for (ServerPlayer player : event.getOnlineMembers()) {
                FTBIntegrationHelper.onQuestStarted(player, questId);
            }
            return EventResult.pass();
        });

        registered = true;
    }
}
