package com.phantasm.briefing.client;

import com.phantasm.briefing.PhantasmBriefing;
import com.phantasm.briefing.network.ModNetwork;
import com.phantasm.briefing.network.packet.OpenQuestJournalC2SPacket;
import com.phantasm.briefing.network.packet.StartNpcDialogueC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = PhantasmBriefing.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DialogueInteractionClientEvents {
    private DialogueInteractionClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientDialogueSession.getInstance().clearIfSpeakerMissing(minecraft);
        while (ClientKeyMappings.START_NPC_DIALOGUE.consumeClick()) {
            if (minecraft.player == null
                    || minecraft.level == null
                    || minecraft.screen != null
                    || minecraft.isPaused()
                    || ClientDialogueSession.getInstance().isActive()) {
                continue;
            }

            Entity target = DialogueFocusClient.findFocusedLivingEntity(minecraft);
            if (target != null) {
                ModNetwork.CHANNEL.sendToServer(new StartNpcDialogueC2SPacket(target.getId()));
            }
        }

        while (ClientKeyMappings.OPEN_QUEST_JOURNAL.consumeClick()) {
            if (minecraft.player == null
                    || minecraft.level == null
                    || minecraft.screen != null
                    || minecraft.isPaused()
                    || ClientDialogueSession.getInstance().isActive()) {
                continue;
            }
            ModNetwork.CHANNEL.sendToServer(new OpenQuestJournalC2SPacket());
        }
    }
}
