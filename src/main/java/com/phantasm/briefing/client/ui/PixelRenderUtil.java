package com.phantasm.briefing.client.ui;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.opengl.GL11C;

public final class PixelRenderUtil {
    private PixelRenderUtil() {
    }

    public static int snap(double value) {
        return (int) Math.round(value);
    }

    public static void prepareNearestTexture(ResourceLocation texture) {
        RenderSystem.setShaderTexture(0, texture);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
    }

    public static void prepareLinearTexture(ResourceLocation texture) {
        RenderSystem.setShaderTexture(0, texture);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
    }

    /**
     * Draws a tinted transparent texture using additive blending. This is intended for low-opacity
     * decorative overlays such as runes, seals and magical watermarks. Callers own clipping through
     * {@link #withClip(GuiGraphics, int, int, int, int, Runnable)} so the same helper works in any panel.
     */
    public static void drawAdditiveOverlay(GuiGraphics graphics, ResourceLocation texture,
                                           int x, int y, int width, int height,
                                           int textureWidth, int textureHeight, int tintColor) {
        if (width <= 0 || height <= 0 || textureWidth <= 0 || textureHeight <= 0) return;
        prepareLinearTexture(texture);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        graphics.setColor(red(tintColor), green(tintColor), blue(tintColor), alpha(tintColor));
        try {
            graphics.blit(texture, x, y, width, height, 0.0F, 0.0F,
                    textureWidth, textureHeight, textureWidth, textureHeight);
        } finally {
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.defaultBlendFunc();
        }
    }

    /** Balanced clipping shared by screens; nested clips are restored even when rendering throws. */
    public static void withClip(GuiGraphics graphics, int x1, int y1, int x2, int y2, Runnable draw) {
        if (x2 <= x1 || y2 <= y1) return;
        graphics.enableScissor(x1, y1, x2, y2);
        try {
            draw.run();
        } finally {
            graphics.disableScissor();
        }
    }

    public static float red(int color) {
        return ((color >> 16) & 0xFF) / 255.0F;
    }

    public static float green(int color) {
        return ((color >> 8) & 0xFF) / 255.0F;
    }

    public static float blue(int color) {
        return (color & 0xFF) / 255.0F;
    }

    public static float alpha(int color) {
        return ((color >> 24) & 0xFF) / 255.0F;
    }
}
