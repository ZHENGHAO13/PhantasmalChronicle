package com.phantasm.briefing.event;

import com.phantasm.briefing.command.PBriefingCommands;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class QuestNpcCommandEvents {

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        PBriefingCommands.register(event.getDispatcher());
    }
}
