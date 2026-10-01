package com.phantasm.briefing.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Extension point for additional pages inside the quest journal shell.
 * Pages receive only the shared content rectangle; the journal owns the outer frame and top tabs.
 */
public interface JournalTabPage {
    void render(GuiGraphics graphics, Font font, int x, int y, int width, int height,
                int mouseX, int mouseY, float partialTick);

    default boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    default boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return false;
    }

    default void onSelected() {
    }

    default void onDeselected() {
    }
}
