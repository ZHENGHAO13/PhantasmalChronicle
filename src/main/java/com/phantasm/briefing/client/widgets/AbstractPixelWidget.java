package com.phantasm.briefing.client.widgets;

import com.phantasm.briefing.client.ui.PixelRenderUtil;

public abstract class AbstractPixelWidget implements PixelWidget {
    protected int x;
    protected int y;
    protected int width;
    protected int height;
    protected boolean visible = true;
    protected boolean active = true;

    protected AbstractPixelWidget(int x, int y, int width, int height) {
        this.x = PixelRenderUtil.snap(x);
        this.y = PixelRenderUtil.snap(y);
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return this.visible
                && mouseX >= this.x
                && mouseX < this.x + this.width
                && mouseY >= this.y
                && mouseY < this.y + this.height;
    }

    @Override
    public boolean isVisible() {
        return this.visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
