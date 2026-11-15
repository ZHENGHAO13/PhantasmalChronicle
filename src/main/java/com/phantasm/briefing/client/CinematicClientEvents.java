package com.phantasm.briefing.client;

import com.phantasm.briefing.PhantasmBriefing;
import com.phantasm.briefing.client.cinematic.CinematicStateManager;
import com.phantasm.briefing.client.vfx.VFXManager;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = PhantasmBriefing.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CinematicClientEvents {
    private CinematicClientEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKeyInput(InputEvent.Key event) {
        CinematicStateManager manager = CinematicStateManager.getInstance();
        if (!manager.isCinematicModeActive()) {
            return;
        }

        if (event.getAction() == GLFW.GLFW_PRESS || event.getAction() == GLFW.GLFW_REPEAT) {
            if (event.getKey() == GLFW.GLFW_KEY_ESCAPE) {
                manager.handleEscapeExit();
                if (event.isCancelable()) {
                    event.setCanceled(true);
                }
                return;
            }
            if (manager.isAwaitingOptionSelection()) {
                int optionIndex = mapKeyToOptionIndex(event.getKey());
                if (optionIndex >= 0) {
                    manager.selectOptionByIndex(optionIndex);
                }
                if (event.isCancelable()) {
                    event.setCanceled(true);
                }
                return;
            }
            if (event.getKey() == GLFW.GLFW_KEY_SPACE || event.getKey() == GLFW.GLFW_KEY_ENTER) {
                manager.requestAdvance();
            }
        }
        if (event.isCancelable()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseButton(InputEvent.MouseButton event) {
        CinematicStateManager manager = CinematicStateManager.getInstance();
        if (!manager.isCinematicModeActive()) {
            return;
        }

        if (event.getAction() == GLFW.GLFW_PRESS && event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            manager.requestAdvance();
        }
        if (event.isCancelable()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        CinematicStateManager manager = CinematicStateManager.getInstance();
        if (!manager.isCinematicModeActive()) {
            return;
        }

        event.setYaw(manager.getLockedYaw());
        event.setPitch(manager.getLockedPitch());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isPaused()) {
            return;
        }

        CinematicStateManager.getInstance().tick();
        VFXManager.getInstance().tick();
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        VFXManager.getInstance().render(event.getPoseStack(), event.getCamera(), event.getPartialTick());
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        CinematicStateManager.getInstance().endCinematicMode();
        VFXManager.getInstance().clear();
    }

    private static int mapKeyToOptionIndex(int keyCode) {
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            return keyCode - GLFW.GLFW_KEY_1;
        }
        if (keyCode >= GLFW.GLFW_KEY_KP_1 && keyCode <= GLFW.GLFW_KEY_KP_9) {
            return keyCode - GLFW.GLFW_KEY_KP_1;
        }
        return -1;
    }
}
