package com.phantasm.briefing.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.resources.ResourceLocation;
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
