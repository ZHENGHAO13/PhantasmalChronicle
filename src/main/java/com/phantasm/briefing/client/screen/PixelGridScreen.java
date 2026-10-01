package com.phantasm.briefing.client.screen;

import com.phantasm.briefing.client.ui.PixelRenderUtil;
import com.phantasm.briefing.client.widgets.PixelWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import javax.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

public abstract class PixelGridScreen extends Screen {
    private final List<PixelWidget> widgets = new ArrayList<>();

    protected PixelGridScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        super.init();
        this.widgets.clear();
        this.buildWidgets();
    }

    protected abstract void buildWidgets();

    protected <T extends PixelWidget> T addPixelWidget(T widget) {
        this.widgets.add(widget);
        return widget;
    }

    protected final int snap(double value) {
        return PixelRenderUtil.snap(value);
    }

    @Override
    public void render(@Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderModalBackdrop(guiGraphics);
        this.renderScreenContents(guiGraphics, mouseX, mouseY, partialTick);
        for (PixelWidget widget : this.widgets) {
            if (widget.isVisible()) {
                widget.render(guiGraphics, mouseX, mouseY, partialTick);
            }
        }
    }

    protected void renderModalBackdrop(GuiGraphics guiGraphics) {
        guiGraphics.fill(0, 0, this.width, this.height, 0xB0000000);
    }

    protected void renderScreenContents(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = this.widgets.size() - 1; i >= 0; i--) {
            PixelWidget widget = this.widgets.get(i);
            if (widget.isVisible() && widget.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
