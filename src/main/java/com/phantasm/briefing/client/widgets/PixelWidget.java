package com.phantasm.briefing.client.widgets;

import net.minecraft.client.gui.GuiGraphics;

public interface PixelWidget {

    void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick);

    default boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    boolean isMouseOver(double mouseX, double mouseY);

    default boolean isVisible() {
        return true;
    }
}
