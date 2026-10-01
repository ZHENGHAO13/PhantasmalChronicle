package com.phantasm.briefing.client.widgets;

import net.minecraft.client.gui.GuiGraphics;

public final class SolidColorRectWidget extends AbstractPixelWidget {
    private final int color;
    private final int radius;

    public SolidColorRectWidget(int x, int y, int width, int height, int color) {
        this(x, y, width, height, color, 0);
    }

    public SolidColorRectWidget(int x, int y, int width, int height, int color, int radius) {
        super(x, y, width, height);
        this.color = color;
        this.radius = Math.max(0, Math.min(Math.min(width, height) / 2, radius));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible || this.width <= 0 || this.height <= 0) {
            return;
        }

        if (this.radius <= 0) {
            guiGraphics.fill(this.x, this.y, this.x + this.width, this.y + this.height, this.color);
            return;
        }

        int left = this.x;
        int top = this.y;
        int right = this.x + this.width;
        int bottom = this.y + this.height;
        int r = this.radius;

        guiGraphics.fill(left + r, top, right - r, bottom, this.color);
        guiGraphics.fill(left, top + r, left + r, bottom - r, this.color);
        guiGraphics.fill(right - r, top + r, right, bottom - r, this.color);

        fillCorner(guiGraphics, left + r, top + r, r, Corner.TOP_LEFT);
        fillCorner(guiGraphics, right - r - 1, top + r, r, Corner.TOP_RIGHT);
        fillCorner(guiGraphics, left + r, bottom - r - 1, r, Corner.BOTTOM_LEFT);
        fillCorner(guiGraphics, right - r - 1, bottom - r - 1, r, Corner.BOTTOM_RIGHT);
    }

    private void fillCorner(GuiGraphics guiGraphics, int centerX, int centerY, int radius, Corner corner) {
        int radiusSquared = radius * radius;
        for (int offsetY = 0; offsetY < radius; offsetY++) {
            for (int offsetX = 0; offsetX < radius; offsetX++) {
                int dx = radius - offsetX - 1;
                int dy = radius - offsetY - 1;
                if ((dx * dx) + (dy * dy) > radiusSquared) {
                    continue;
                }

                int drawX = switch (corner) {
                    case TOP_LEFT, BOTTOM_LEFT -> centerX - dx;
                    case TOP_RIGHT, BOTTOM_RIGHT -> centerX + dx;
                };
                int drawY = switch (corner) {
                    case TOP_LEFT, TOP_RIGHT -> centerY - dy;
                    case BOTTOM_LEFT, BOTTOM_RIGHT -> centerY + dy;
                };
                guiGraphics.fill(drawX, drawY, drawX + 1, drawY + 1, this.color);
            }
        }
    }

    private enum Corner {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }
}
