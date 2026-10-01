package com.phantasm.briefing.client.widgets;

import com.phantasm.briefing.client.ui.PixelRenderUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;
import javax.annotation.Nonnull;

public final class PixelImageWidget extends AbstractPixelWidget {
    private final ResourceLocation texture;
    private final float u;
    private final float v;
    private final int regionWidth;
    private final int regionHeight;
    private final int textureWidth;
    private final int textureHeight;
    private final int tintColor;

    public PixelImageWidget(
            int x,
            int y,
            int width,
            int height,
            @Nonnull ResourceLocation texture,
            float u,
            float v,
            int regionWidth,
            int regionHeight,
            int textureWidth,
            int textureHeight,
            int tintColor
    ) {
        super(x, y, width, height);
        this.texture = Objects.requireNonNull(texture);
        this.u = u;
        this.v = v;
        this.regionWidth = regionWidth;
        this.regionHeight = regionHeight;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.tintColor = tintColor;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible || this.width <= 0 || this.height <= 0) {
            return;
        }

        ResourceLocation texture = Objects.requireNonNull(this.texture);
        PixelRenderUtil.prepareNearestTexture(texture);
        guiGraphics.setColor(
                PixelRenderUtil.red(this.tintColor),
                PixelRenderUtil.green(this.tintColor),
                PixelRenderUtil.blue(this.tintColor),
                PixelRenderUtil.alpha(this.tintColor)
        );
        guiGraphics.blit(
                texture,
                this.x,
                this.y,
                this.width,
                this.height,
                this.u,
                this.v,
                this.regionWidth,
                this.regionHeight,
                this.textureWidth,
                this.textureHeight
        );
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
