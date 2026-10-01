package com.phantasm.briefing.client.screen;

import com.phantasm.briefing.client.widgets.HoverButtonWidget;
import com.phantasm.briefing.client.widgets.PixelImageWidget;
import com.phantasm.briefing.client.widgets.ShadowLabelWidget;
import com.phantasm.briefing.client.widgets.SolidColorRectWidget;
import com.phantasm.briefing.data.WalletBalanceEntry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class WalletScreen extends PixelGridScreen {
    private static final ResourceLocation WHITE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/white.png");

    private final List<WalletBalanceEntry> entries;
    private int entryScroll;

    public WalletScreen(List<WalletBalanceEntry> entries) {
        super(Component.literal("钱包总览"));
        this.entries = List.copyOf(entries);
        this.entryScroll = 0;
    }

    @Override
    protected void buildWidgets() {
        Font font = this.font;
        int panelWidth = 336;
        int panelHeight = 196;
        int panelX = snap((this.width - panelWidth) / 2.0D);
        int panelY = snap((this.height - panelHeight) / 2.0D);

        this.addPixelWidget(new SolidColorRectWidget(panelX, panelY, panelWidth, panelHeight, 0xF412171F, 6));
        this.addPixelWidget(new SolidColorRectWidget(panelX, panelY, panelWidth, 1, 0x6634465C));
        this.addPixelWidget(new SolidColorRectWidget(panelX, panelY + panelHeight - 1, panelWidth, 1, 0x6634465C));
        this.addPixelWidget(new ShadowLabelWidget(font, panelX + 18, panelY + 16, Component.literal("钱包总览"), 0xDCECFF));
        this.addPixelWidget(new PixelImageWidget(
                panelX + 18,
                panelY + 36,
                panelWidth - 36,
                2,
                WHITE_TEXTURE,
                0.0F,
                0.0F,
                1,
                1,
                1,
                1,
                0x6E7C95FF
        ));
        this.addPixelWidget(new HoverButtonWidget(
                font,
                panelX + panelWidth - 92,
                panelY + panelHeight - 28,
                74,
                20,
                Component.literal("关闭"),
                0xFF243142,
                0xFF35506D,
                0xFF6B87A6,
                0xDCECFF,
                0xFFFFFFF2,
                button -> this.onClose()
        ));
    }

    @Override
    protected void renderScreenContents(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Font font = this.font;
        int panelWidth = 336;
        int panelHeight = 196;
        int panelX = snap((this.width - panelWidth) / 2.0D);
        int panelY = snap((this.height - panelHeight) / 2.0D);
        int textX = panelX + 18;
        int textY = panelY + 50;

        if (this.entries.isEmpty()) {
            guiGraphics.drawString(font, "当前没有已定义的钱包货币。", textX, textY, 0x7F93AD, true);
            return;
        }

        int visibleCount = 8;
        clampScroll(visibleCount);
        int endIndex = Math.min(this.entries.size(), this.entryScroll + visibleCount);
        for (int i = this.entryScroll; i < endIndex; i++) {
            WalletBalanceEntry entry = this.entries.get(i);
            String line = entry.title() + " : " + entry.balance() + " " + entry.currencyName();
            guiGraphics.drawString(font, line, textX, textY, 0xDCECFF, true);
            textY += font.lineHeight + 6;
        }
        if (this.entries.size() > visibleCount) {
            String scrollText = "滚轮翻页  " + (this.entryScroll + 1) + "-" + endIndex + "/" + this.entries.size();
            guiGraphics.drawString(font, scrollText, textX, panelY + panelHeight - 18, 0x7F93AD, true);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            scrollBy(-1, 8);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            scrollBy(1, 8);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            scrollBy(-8, 8);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            scrollBy(8, 8);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta == 0.0D) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        scrollBy(delta > 0.0D ? -1 : 1, 8);
        return true;
    }

    private void scrollBy(int delta, int visibleCount) {
        int maxScroll = Math.max(0, this.entries.size() - visibleCount);
        this.entryScroll = Math.max(0, Math.min(maxScroll, this.entryScroll + delta));
    }

    private void clampScroll(int visibleCount) {
        int maxScroll = Math.max(0, this.entries.size() - visibleCount);
        this.entryScroll = Math.max(0, Math.min(maxScroll, this.entryScroll));
    }
}
