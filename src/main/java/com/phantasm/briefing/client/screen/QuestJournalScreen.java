package com.phantasm.briefing.client.screen;

import com.phantasm.briefing.client.widgets.HoverButtonWidget;
import com.phantasm.briefing.client.widgets.PixelImageWidget;
import com.phantasm.briefing.client.widgets.ShadowLabelWidget;
import com.phantasm.briefing.client.widgets.SolidColorRectWidget;
import com.phantasm.briefing.data.QuestJournalEntry;
import com.phantasm.briefing.data.QuestRuntimeStatus;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class QuestJournalScreen extends PixelGridScreen {
    private static final ResourceLocation WHITE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/white.png");

    private final List<QuestJournalEntry> entries;
    private int lineScroll;

    public QuestJournalScreen(List<QuestJournalEntry> entries) {
        super(Component.literal("任务日志"));
        this.entries = List.copyOf(entries);
        this.lineScroll = 0;
    }

    @Override
    protected void buildWidgets() {
        Font font = this.font;
        int panelWidth = 356;
        int panelHeight = 210;
        int panelX = snap((this.width - panelWidth) / 2.0D);
        int panelY = snap((this.height - panelHeight) / 2.0D);

        this.addPixelWidget(new SolidColorRectWidget(panelX, panelY, panelWidth, panelHeight, 0xF412171F, 6));
        this.addPixelWidget(new SolidColorRectWidget(panelX, panelY, panelWidth, 1, 0x6634465C));
        this.addPixelWidget(new SolidColorRectWidget(panelX, panelY + panelHeight - 1, panelWidth, 1, 0x6634465C));
        this.addPixelWidget(new ShadowLabelWidget(font, panelX + 18, panelY + 16, Component.literal("任务日志"), 0xDCECFF));
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
                panelX + panelWidth - 174,
                panelY + panelHeight - 28,
                74,
                20,
                Component.literal("设置"),
                0xFF243142,
                0xFF35506D,
                0xFF6B87A6,
                0xDCECFF,
                0xFFFFFFF2,
                button -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new PhantasmConfigScreen(this));
                    }
                }
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
        int panelWidth = 356;
        int panelHeight = 210;
        int panelX = snap((this.width - panelWidth) / 2.0D);
        int panelY = snap((this.height - panelHeight) / 2.0D);

        List<String> lines = buildDisplayLines();
        int textX = panelX + 18;
        int textY = panelY + 50;
        int maxLines = 9;
        clampScroll(lines.size(), maxLines);
        int visibleLines = Math.min(maxLines, Math.max(0, lines.size() - this.lineScroll));
        for (int i = 0; i < visibleLines; i++) {
            String line = lines.get(i + this.lineScroll);
            int color = colorForLine(line);
            guiGraphics.drawString(font, line, textX, textY, color, true);
            textY += font.lineHeight + 4;
        }

        if (lines.isEmpty()) {
            guiGraphics.drawString(font, "当前没有可见任务。", textX, textY, 0x7F93AD, true);
        } else if (lines.size() > maxLines) {
            int footerY = panelY + panelHeight - 20;
            String scrollText = "滚轮翻页  " + (this.lineScroll + 1) + "-" + Math.min(lines.size(), this.lineScroll + maxLines) + "/" + lines.size();
            guiGraphics.drawString(font, scrollText, textX, footerY, 0x7F93AD, true);
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
            scrollBy(-1, 9);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            scrollBy(1, 9);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            scrollBy(-9, 9);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            scrollBy(9, 9);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta == 0.0D) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        scrollBy(delta > 0.0D ? -1 : 1, 9);
        return true;
    }

    private List<String> buildDisplayLines() {
        List<String> lines = new ArrayList<>();
        for (QuestJournalEntry entry : this.entries) {
            String prefix = "[" + entry.status().label() + "] ";
            lines.add(prefix + entry.title());
            if (!entry.description().isBlank()) {
                lines.add("  " + entry.description());
            }
            for (String objectiveLine : entry.objectiveLines()) {
                lines.add("  - " + objectiveLine);
            }
            lines.add("");
        }

        if (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    private static int colorForLine(String line) {
        if (line.startsWith("[" + QuestRuntimeStatus.COMPLETED.label() + "]")) {
            return 0x7F93AD;
        }
        if (line.startsWith("[" + QuestRuntimeStatus.READY_TO_TURN_IN.label() + "]")) {
            return 0x90F0A2;
        }
        if (line.startsWith("[" + QuestRuntimeStatus.AVAILABLE.label() + "]")) {
            return 0xF3D77B;
        }
        return 0xDCECFF;
    }

    private void scrollBy(int delta, int maxLines) {
        List<String> lines = buildDisplayLines();
        int maxScroll = Math.max(0, lines.size() - maxLines);
        this.lineScroll = Math.max(0, Math.min(maxScroll, this.lineScroll + delta));
    }

    private void clampScroll(int totalLines, int maxLines) {
        int maxScroll = Math.max(0, totalLines - maxLines);
        this.lineScroll = Math.max(0, Math.min(maxScroll, this.lineScroll));
    }
}
