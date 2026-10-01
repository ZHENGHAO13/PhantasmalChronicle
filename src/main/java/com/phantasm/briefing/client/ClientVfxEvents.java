package com.phantasm.briefing.client;

import com.phantasm.briefing.PhantasmBriefing;
import com.phantasm.briefing.client.vfx.VFXManager;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-only lifecycle for lightweight Phantasm visual effects.
 *
 * <p>Dialogue flow no longer owns a second cinematic state machine; VFX stays
 * independent so dialogue presentation can evolve without duplicating input,
 * camera, or typewriter logic.</p>
 */
@Mod.EventBusSubscriber(modid = PhantasmBriefing.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientVfxEvents {
    private ClientVfxEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !Minecraft.getInstance().isPaused()) {
            VFXManager.getInstance().tick();
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            VFXManager.getInstance().render(event.getPoseStack(), event.getCamera(), event.getPartialTick());
        }
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        VFXManager.getInstance().clear();
    }
}
