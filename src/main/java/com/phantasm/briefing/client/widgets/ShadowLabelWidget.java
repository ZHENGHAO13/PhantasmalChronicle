package com.phantasm.briefing.client.widgets;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.Objects;
import javax.annotation.Nonnull;

public final class ShadowLabelWidget extends AbstractPixelWidget {
    private final Font font;
    private final Component text;
    private final int color;

    public ShadowLabelWidget(@Nonnull Font font, int x, int y, @Nonnull Component text, int color) {
        super(x, y, font.width(text), font.lineHeight);
        this.font = Objects.requireNonNull(font);
        this.text = Objects.requireNonNull(text);
        this.color = color;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) {
            return;
        }

        Font font = Objects.requireNonNull(this.font);
        Component text = Objects.requireNonNull(this.text);
        guiGraphics.drawString(font, text, this.x, this.y, this.color, true);
    }
}
