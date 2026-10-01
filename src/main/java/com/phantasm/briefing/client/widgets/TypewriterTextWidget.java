package com.phantasm.briefing.client.widgets;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public final class TypewriterTextWidget extends AbstractPixelWidget implements TickablePixelWidget {
    private final Font font;
    private final int color;
    private final int lineSpacing;
    private final int charactersPerTick;

    private String fullText = "";
    private int visibleCharacters;

    public TypewriterTextWidget(
            Font font,
            int x,
            int y,
            int width,
            int color,
            int lineSpacing,
            int charactersPerTick
    ) {
        super(x, y, width, font.lineHeight);
        this.font = font;
        this.color = color;
        this.lineSpacing = Math.max(0, lineSpacing);
        this.charactersPerTick = Math.max(1, charactersPerTick);
    }

    public void setText(String text) {
        this.fullText = text == null ? "" : text;
        this.visibleCharacters = 0;
        this.recalculateHeight();
    }

    public void revealAll() {
        this.visibleCharacters = this.fullText.length();
        this.recalculateHeight();
    }

    public boolean isComplete() {
        return this.visibleCharacters >= this.fullText.length();
    }

    public String getFullText() {
        return this.fullText;
    }

    @Override
    public void tick() {
        if (!this.visible || this.isComplete()) {
            return;
        }

        this.visibleCharacters = Math.min(this.fullText.length(), this.visibleCharacters + this.charactersPerTick);
        this.recalculateHeight();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible || this.fullText.isEmpty()) {
            return;
        }

        String visibleText = this.fullText.substring(0, Math.min(this.visibleCharacters, this.fullText.length()));
        List<FormattedCharSequence> lines = this.font.split(Component.literal(visibleText), this.width);
        int currentY = this.y;
        for (FormattedCharSequence line : lines) {
            guiGraphics.drawString(this.font, line, this.x, currentY, this.color, true);
            currentY += this.font.lineHeight + this.lineSpacing;
        }
    }

    private void recalculateHeight() {
        if (this.fullText.isEmpty()) {
            this.height = this.font.lineHeight;
            return;
        }

        String visibleText = this.fullText.substring(0, Math.min(this.visibleCharacters, this.fullText.length()));
        List<FormattedCharSequence> lines = this.font.split(Component.literal(visibleText), this.width);
        int lineCount = Math.max(1, lines.size());
        this.height = (lineCount * this.font.lineHeight) + ((lineCount - 1) * this.lineSpacing);
    }
}
