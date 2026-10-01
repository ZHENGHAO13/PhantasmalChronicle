package com.phantasm.briefing.client.widgets;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;

public final class TextBoxWidget extends AbstractPixelWidget {
    private final List<ShadowLabelWidget> lines = new ArrayList<>();

    public TextBoxWidget(@Nonnull Font font, int x, int y, @Nonnull String[] contentLines, int color, int lineSpacing) {
        super(x, y, calculateWidth(font, contentLines), calculateHeight(font, contentLines, lineSpacing));
        Font safeFont = Objects.requireNonNull(font);
        int currentY = y;
        for (String line : contentLines) {
            this.lines.add(new ShadowLabelWidget(safeFont, x, currentY, Component.literal(Objects.requireNonNull(line)), color));
            currentY += safeFont.lineHeight + lineSpacing;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) {
            return;
        }

        for (ShadowLabelWidget line : this.lines) {
            line.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    private static int calculateWidth(@Nonnull Font font, @Nonnull String[] lines) {
        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, font.width(Objects.requireNonNull(line)));
        }
        return maxWidth;
    }

    private static int calculateHeight(@Nonnull Font font, @Nonnull String[] lines, int lineSpacing) {
        if (lines.length == 0) {
            return 0;
        }
        return (lines.length * font.lineHeight) + ((lines.length - 1) * lineSpacing);
    }
}
