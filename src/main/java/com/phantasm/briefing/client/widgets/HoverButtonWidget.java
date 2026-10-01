package com.phantasm.briefing.client.widgets;

import com.phantasm.briefing.client.ui.PixelRenderUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import java.util.Objects;
import javax.annotation.Nonnull;

public final class HoverButtonWidget extends AbstractPixelWidget {
    private final Font font;
    private final Component label;
    private final int normalColor;
    private final int hoverColor;
    private final int borderColor;
    private final int normalTextColor;
    private final int hoverTextColor;
    private final PressAction onPress;
    @Nullable
    private final ResourceLocation normalTexture;
    @Nullable
    private final ResourceLocation hoverTexture;

    public HoverButtonWidget(
            @Nonnull Font font,
            int x,
            int y,
            int width,
            int height,
            @Nonnull Component label,
            int normalColor,
            int hoverColor,
            int borderColor,
            int normalTextColor,
            int hoverTextColor,
            @Nonnull PressAction onPress
    ) {
        this(font, x, y, width, height, label, normalColor, hoverColor, borderColor, normalTextColor, hoverTextColor, onPress, null, null);
    }

    public HoverButtonWidget(
            @Nonnull Font font,
            int x,
            int y,
            int width,
            int height,
            @Nonnull Component label,
            int normalColor,
            int hoverColor,
            int borderColor,
            int normalTextColor,
            int hoverTextColor,
            @Nonnull PressAction onPress,
            @Nullable ResourceLocation normalTexture,
            @Nullable ResourceLocation hoverTexture
    ) {
        super(x, y, width, height);
        this.font = Objects.requireNonNull(font);
        this.label = Objects.requireNonNull(label);
        this.normalColor = normalColor;
        this.hoverColor = hoverColor;
        this.borderColor = borderColor;
        this.normalTextColor = normalTextColor;
        this.hoverTextColor = hoverTextColor;
        this.onPress = Objects.requireNonNull(onPress);
        this.normalTexture = normalTexture;
        this.hoverTexture = hoverTexture;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) {
            return;
        }

        boolean hovered = this.isMouseOver(mouseX, mouseY);
        int backgroundColor = hovered ? this.hoverColor : this.normalColor;

        guiGraphics.fill(this.x, this.y, this.x + this.width, this.y + this.height, this.borderColor);

        ResourceLocation texture = hovered ? this.hoverTexture : this.normalTexture;
        if (texture != null) {
            PixelRenderUtil.prepareNearestTexture(texture);
            guiGraphics.setColor(
                    PixelRenderUtil.red(backgroundColor),
                    PixelRenderUtil.green(backgroundColor),
                    PixelRenderUtil.blue(backgroundColor),
                    PixelRenderUtil.alpha(backgroundColor)
            );
            guiGraphics.blit(texture, this.x + 1, this.y + 1, 0, 0.0F, 0.0F, this.width - 2, this.height - 2, 1, 1);
            guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        } else {
            guiGraphics.fill(this.x + 1, this.y + 1, this.x + this.width - 1, this.y + this.height - 1, backgroundColor);
        }

        int textColor = hovered ? this.hoverTextColor : this.normalTextColor;
        Font font = Objects.requireNonNull(this.font);
        Component label = Objects.requireNonNull(this.label);
        int textX = this.x + ((this.width - font.width(label)) / 2);
        int textY = this.y + ((this.height - font.lineHeight) / 2);
        guiGraphics.drawString(font, label, textX, textY, textColor, true);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.visible || !this.active || button != 0 || !this.isMouseOver(mouseX, mouseY)) {
            return false;
        }

        this.onPress.onPress(this);
        return true;
    }

    @FunctionalInterface
    public interface PressAction {
        void onPress(HoverButtonWidget button);
    }
}
