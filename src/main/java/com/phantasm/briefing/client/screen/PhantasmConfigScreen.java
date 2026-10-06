package com.phantasm.briefing.client.screen;

import com.phantasm.briefing.config.DialogueHudStyle;
import com.phantasm.briefing.config.PhantasmBriefingClientConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class PhantasmConfigScreen extends PixelGridScreen {
    private static final int BASE_PANEL_WIDTH = 660;
    private static final int BASE_PANEL_HEIGHT = 330;
    private static final int ROW_HEIGHT = 42;
    private static final int ROW_GAP = 8;

    private final Screen parent;
    private ConfigCategory selectedCategory = ConfigCategory.TRACKER;
    private int selectedOptionIndex;
    private int scrollOffset;
    private boolean dirty;

    private boolean questTrackingEnabled;
    private boolean questTrackerShowObjectiveTrackingText;
    private boolean questTrackerAnchorRight;
    private boolean worldMarkerEnabled;
    private boolean npcIndicatorEnabled;
    private boolean markerDistanceEnabled;
    private DialogueHudStyle dialogueHudStyle;

    public PhantasmConfigScreen(Screen parent) {
        super(Component.literal("幻象纪事设置"));
        this.parent = parent;
        this.loadCurrentValues();
    }

    @Override
    protected void buildWidgets() {
    }

    @Override
    protected void renderModalBackdrop(GuiGraphics guiGraphics) {
        guiGraphics.fill(0, 0, this.width, this.height, 0x6C000000);
    }

    @Override
    protected void renderScreenContents(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Font font = Objects.requireNonNull(this.font);
        Layout layout = this.layout();

        renderBackdrop(guiGraphics, layout);
        renderFrame(guiGraphics, layout);
        guiGraphics.drawString(font, "幻象纪事设置", layout.panelX() + 16, layout.panelY() + 16, 0xA9D9FF, false);
        guiGraphics.drawString(font, "CLIENT CONFIG", layout.panelX() + layout.panelWidth() - 92, layout.panelY() + 16, 0x7A9BC1, false);
        guiGraphics.fill(layout.panelX() + 12, layout.panelY() + 38, layout.panelX() + layout.panelWidth() - 12, layout.panelY() + 39, 0x7A4D7CA9);
        renderStatusBadge(guiGraphics, font, layout);
        renderSidebar(guiGraphics, font, layout, mouseX, mouseY);
        renderOptions(guiGraphics, font, layout, mouseX, mouseY);
        renderFooter(guiGraphics, font, layout, mouseX, mouseY);
    }

    private void renderBackdrop(GuiGraphics guiGraphics, Layout layout) {
        int left = layout.panelX();
        int top = layout.panelY();
        int right = left + layout.panelWidth();
        int bottom = top + layout.panelHeight();
        guiGraphics.fill(left, top, right, bottom, 0xF0060B12);
        guiGraphics.fillGradient(left + 1, top + 1, right - 1, bottom - 1, 0x36143454, 0x14040910);
    }

    private void renderFrame(GuiGraphics guiGraphics, Layout layout) {
        int left = layout.panelX();
        int top = layout.panelY();
        int right = left + layout.panelWidth();
        int bottom = top + layout.panelHeight();
        int border = 0xB04D7CA9;
        guiGraphics.fill(left, top, right, top + 1, border);
        guiGraphics.fill(left, bottom - 1, right, bottom, border);
        guiGraphics.fill(left, top, left + 1, bottom, border);
        guiGraphics.fill(right - 1, top, right, bottom, border);
        guiGraphics.fill(layout.dividerX(), layout.panelY() + 52, layout.dividerX() + 1, layout.footerTop() - 8, 0x805A8FC2);
    }

    private void renderStatusBadge(GuiGraphics guiGraphics, Font font, Layout layout) {
        String text = this.dirty ? "有未保存更改" : "设置已同步";
        int width = font.width(text) + 14;
        int right = layout.panelX() + layout.panelWidth() - 18;
        int left = right - width;
        int top = layout.panelY() + 44;
        guiGraphics.fill(left, top, right, top + 14, this.dirty ? 0x493B2C1C : 0x3A1B3047);
        guiGraphics.fill(left, top, left + 2, top + 14, this.dirty ? 0xD6A458 : 0x8FC8FF);
        guiGraphics.drawString(font, text, left + 7, top + 4, this.dirty ? 0xF1D7A6 : 0xCFE7FF, false);
    }

    private void renderSidebar(GuiGraphics guiGraphics, Font font, Layout layout, int mouseX, int mouseY) {
        int x = layout.panelX() + 18;
        int y = layout.contentTop();
        for (ConfigCategory category : ConfigCategory.values()) {
            boolean selected = category == this.selectedCategory;
            boolean hovered = isIn(mouseX, mouseY, x, y, layout.sidebarWidth(), 30);
            guiGraphics.fill(x, y, x + layout.sidebarWidth(), y + 30, selected ? 0xC91A2940 : hovered ? 0xAA152334 : 0x900B111A);
            if (selected) {
                guiGraphics.fill(x, y, x + 4, y + 30, 0x8FC8FF);
                guiGraphics.fill(x + 4, y, x + layout.sidebarWidth(), y + 1, 0x55426A94);
            }
            guiGraphics.drawString(font, category.title, x + 14, y + 11, selected ? 0xDDF1FF : 0xD4E6F8, false);
            y += 38;
        }
    }

    private void renderOptions(GuiGraphics guiGraphics, Font font, Layout layout, int mouseX, int mouseY) {
        List<ConfigOption> options = this.currentOptions();
        this.clampSelection(options.size(), layout.visibleRows());
        int end = Math.min(options.size(), this.scrollOffset + layout.visibleRows());
        int y = layout.contentTop();
        for (int index = this.scrollOffset; index < end; index++) {
            ConfigOption option = options.get(index);
            boolean selected = index == this.selectedOptionIndex;
            boolean hovered = isIn(mouseX, mouseY, layout.contentX(), y, layout.contentWidth(), layout.rowHeight());
            int background = selected ? 0xB0101B2A : hovered ? 0x9A0D1521 : 0x86080D15;
            guiGraphics.fill(layout.contentX(), y, layout.contentX() + layout.contentWidth(), y + layout.rowHeight(), background);
            guiGraphics.fill(layout.contentX(), y, layout.contentX() + (selected ? 4 : 2), y + layout.rowHeight(), selected ? 0x8FC8FF : 0x4D7CA9);
            guiGraphics.fill(layout.contentX() + 6, y, layout.contentX() + layout.contentWidth(), y + 1, 0x24384C);
            if (selected) {
                drawCardCorners(guiGraphics, layout.contentX(), y, layout.contentWidth(), layout.rowHeight());
            }

            int toggleWidth = 58;
            int toggleX = layout.contentX() + layout.contentWidth() - toggleWidth - 10;
            int titleWidth = Math.max(38, toggleX - layout.contentX() - 18);
            guiGraphics.drawString(font, font.plainSubstrByWidth(option.title, titleWidth), layout.contentX() + 12, y + (layout.compact() ? 13 : 8), 0xE8F3FF, false);
            if (!layout.compact()) {
                guiGraphics.drawString(font, font.plainSubstrByWidth(option.description, titleWidth), layout.contentX() + 12, y + 23, 0x8FA7C8, false);
            }
            drawToggle(guiGraphics, font, toggleX, y + ((layout.rowHeight() - 20) / 2), toggleWidth, option.value(this), option.valueLabel(this));
            y += layout.rowHeight() + ROW_GAP;
        }

        renderScrollBar(guiGraphics, layout, options.size());
        if (options.isEmpty()) {
            guiGraphics.drawString(font, "当前分类没有可用设置。", layout.contentX() + 12, layout.contentTop() + 12, 0x8FA7C8, false);
        }
    }

    private void renderScrollBar(GuiGraphics guiGraphics, Layout layout, int optionCount) {
        int x = layout.contentX() + layout.contentWidth() + 5;
        int y = layout.contentTop();
        int height = layout.contentHeight();
        guiGraphics.fill(x, y, x + 3, y + height, 0x241C2D40);
        if (optionCount <= layout.visibleRows()) {
            guiGraphics.fill(x, y, x + 3, y + height, 0x4A406A98);
            return;
        }
        int thumbHeight = Math.max(16, Math.round(height * (layout.visibleRows() / (float) optionCount)));
        int maxOffset = optionCount - layout.visibleRows();
        int travel = Math.max(1, height - thumbHeight);
        int thumbOffset = Math.round((this.scrollOffset / (float) maxOffset) * travel);
        guiGraphics.fill(x, y + thumbOffset, x + 3, y + thumbOffset + thumbHeight, 0xAA8FC8FF);
    }

    private void renderFooter(GuiGraphics guiGraphics, Font font, Layout layout, int mouseX, int mouseY) {
        guiGraphics.fill(layout.panelX() + 14, layout.footerTop() - 6, layout.panelX() + layout.panelWidth() - 14, layout.footerTop() - 5, 0x24384C);
        Rect reset = resetButton(layout);
        Rect cancel = cancelButton(layout);
        Rect save = saveButton(layout);
        drawButton(guiGraphics, font, reset, "恢复默认", false, isIn(mouseX, mouseY, reset));
        drawButton(guiGraphics, font, cancel, "取消", false, isIn(mouseX, mouseY, cancel));
        drawButton(guiGraphics, font, save, "保存并返回", true, isIn(mouseX, mouseY, save));
        if (!layout.compact()) {
            guiGraphics.drawString(font, "方向键选择  Enter 切换  Ctrl+S 保存  Esc 取消", layout.panelX() + 18, layout.panelY() + layout.panelHeight() - 12, 0x7FA8CF, false);
        }
    }

    private void drawToggle(GuiGraphics guiGraphics, Font font, int x, int y, int width, boolean enabled, String label) {
        int fill = enabled ? 0xB4254663 : 0x9A171D26;
        int accent = enabled ? 0x8FC8FF : 0x5C6A7A;
        guiGraphics.fill(x, y, x + width, y + 20, fill);
        guiGraphics.fill(x, y, x + width, y + 1, accent);
        guiGraphics.fill(x, y, x + 2, y + 20, accent);
        int textX = x + Math.max(5, (width - font.width(label)) / 2);
        guiGraphics.drawString(font, label, textX, y + 6, enabled ? 0xDDF1FF : 0x9CAABD, false);
    }

    private void drawButton(GuiGraphics guiGraphics, Font font, Rect rect, String label, boolean primary, boolean hovered) {
        int background = primary
                ? hovered ? 0xD0355D80 : 0xC5254663
                : hovered ? 0xB52A3A4D : 0xA0182533;
        int border = primary ? 0x8FC8FF : 0x4D6A86;
        guiGraphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), background);
        guiGraphics.fill(rect.x(), rect.y(), rect.right(), rect.y() + 1, border);
        guiGraphics.fill(rect.x(), rect.y(), rect.x() + 2, rect.bottom(), border);
        int textX = rect.x() + Math.max(5, (rect.width() - font.width(label)) / 2);
        guiGraphics.drawString(font, label, textX, rect.y() + 7, primary ? 0xEAF6FF : 0xC8DBED, false);
    }

    private void drawCardCorners(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        int color = 0x7A8FC8FF;
        guiGraphics.fill(x + 6, y + 6, x + 10, y + 7, color);
        guiGraphics.fill(x + 6, y + 6, x + 7, y + 10, color);
        guiGraphics.fill(x + width - 10, y + 6, x + width - 6, y + 7, color);
        guiGraphics.fill(x + width - 7, y + 6, x + width - 6, y + 10, color);
        guiGraphics.fill(x + 6, y + height - 7, x + 10, y + height - 6, color);
        guiGraphics.fill(x + 6, y + height - 10, x + 7, y + height - 6, color);
        guiGraphics.fill(x + width - 10, y + height - 7, x + width - 6, y + height - 6, color);
        guiGraphics.fill(x + width - 7, y + height - 10, x + width - 6, y + height - 6, color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        Layout layout = this.layout();
        int categoryX = layout.panelX() + 18;
        int categoryY = layout.contentTop();
        for (ConfigCategory category : ConfigCategory.values()) {
            if (isIn(mouseX, mouseY, categoryX, categoryY, layout.sidebarWidth(), 30)) {
                this.selectCategory(category);
                return true;
            }
            categoryY += 38;
        }

        List<ConfigOption> options = this.currentOptions();
        int y = layout.contentTop();
        int end = Math.min(options.size(), this.scrollOffset + layout.visibleRows());
        for (int index = this.scrollOffset; index < end; index++) {
            if (isIn(mouseX, mouseY, layout.contentX(), y, layout.contentWidth(), layout.rowHeight())) {
                this.selectedOptionIndex = index;
                this.toggleSelected();
                return true;
            }
            y += layout.rowHeight() + ROW_GAP;
        }

        if (isIn(mouseX, mouseY, resetButton(layout))) {
            this.restoreDefaults();
            return true;
        }
        if (isIn(mouseX, mouseY, cancelButton(layout))) {
            this.onClose();
            return true;
        }
        if (isIn(mouseX, mouseY, saveButton(layout))) {
            this.saveAndClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        Layout layout = this.layout();
        if (!isIn(mouseX, mouseY, layout.contentX(), layout.contentTop(), layout.contentWidth(), layout.contentHeight())) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        List<ConfigOption> options = this.currentOptions();
        int maxOffset = Math.max(0, options.size() - layout.visibleRows());
        if (delta < 0.0D) {
            this.scrollOffset = Math.min(maxOffset, this.scrollOffset + 1);
            return true;
        }
        if (delta > 0.0D) {
            this.scrollOffset = Math.max(0, this.scrollOffset - 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_S) {
            this.saveAndClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            this.moveCategory(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            this.moveCategory(1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            this.moveSelection(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            this.moveSelection(1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) {
            this.toggleSelected();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_R) {
            this.restoreDefaults();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private void saveAndClose() {
        PhantasmBriefingClientConfig.save(
                this.questTrackingEnabled,
                this.questTrackerShowObjectiveTrackingText,
                this.questTrackerAnchorRight,
                this.worldMarkerEnabled,
                this.npcIndicatorEnabled,
                this.markerDistanceEnabled,
                this.dialogueHudStyle
        );
        this.dirty = false;
        this.onClose();
    }

    private void loadCurrentValues() {
        this.questTrackingEnabled = PhantasmBriefingClientConfig.questTrackingEnabled();
        this.questTrackerShowObjectiveTrackingText = PhantasmBriefingClientConfig.questTrackerShowObjectiveTrackingText();
        this.questTrackerAnchorRight = PhantasmBriefingClientConfig.questTrackerAnchorRight();
        this.worldMarkerEnabled = PhantasmBriefingClientConfig.worldMarkerEnabled();
        this.npcIndicatorEnabled = PhantasmBriefingClientConfig.npcIndicatorEnabled();
        this.markerDistanceEnabled = PhantasmBriefingClientConfig.markerDistanceEnabled();
        this.dialogueHudStyle = PhantasmBriefingClientConfig.dialogueHudStyle();
        this.dirty = false;
    }

    private void restoreDefaults() {
        this.questTrackingEnabled = true;
        this.questTrackerShowObjectiveTrackingText = false;
        this.questTrackerAnchorRight = false;
        this.worldMarkerEnabled = true;
        this.npcIndicatorEnabled = true;
        this.markerDistanceEnabled = true;
        this.dialogueHudStyle = DialogueHudStyle.DIALOGUE_BAR;
        this.dirty = true;
    }

    private void selectCategory(ConfigCategory category) {
        this.selectedCategory = category;
        this.selectedOptionIndex = 0;
        this.scrollOffset = 0;
    }

    private void moveCategory(int delta) {
        ConfigCategory[] categories = ConfigCategory.values();
        int current = this.selectedCategory.ordinal();
        int next = Math.max(0, Math.min(categories.length - 1, current + delta));
        this.selectCategory(categories[next]);
    }

    private void moveSelection(int delta) {
        List<ConfigOption> options = this.currentOptions();
        if (options.isEmpty()) {
            return;
        }
        this.selectedOptionIndex = Math.max(0, Math.min(options.size() - 1, this.selectedOptionIndex + delta));
        this.ensureSelectionVisible(options.size(), this.layout().visibleRows());
    }

    private void toggleSelected() {
        List<ConfigOption> options = this.currentOptions();
        if (options.isEmpty()) {
            return;
        }
        this.selectedOptionIndex = Math.max(0, Math.min(options.size() - 1, this.selectedOptionIndex));
        options.get(this.selectedOptionIndex).toggle(this);
        this.dirty = true;
    }

    private void clampSelection(int optionCount, int visibleRows) {
        if (optionCount <= 0) {
            this.selectedOptionIndex = 0;
            this.scrollOffset = 0;
            return;
        }
        this.selectedOptionIndex = Math.max(0, Math.min(optionCount - 1, this.selectedOptionIndex));
        this.ensureSelectionVisible(optionCount, visibleRows);
    }

    private void ensureSelectionVisible(int optionCount, int visibleRows) {
        int maxOffset = Math.max(0, optionCount - visibleRows);
        if (this.selectedOptionIndex < this.scrollOffset) {
            this.scrollOffset = this.selectedOptionIndex;
        } else if (this.selectedOptionIndex >= this.scrollOffset + visibleRows) {
            this.scrollOffset = this.selectedOptionIndex - visibleRows + 1;
        }
        this.scrollOffset = Math.max(0, Math.min(maxOffset, this.scrollOffset));
    }

    private List<ConfigOption> currentOptions() {
        return Arrays.stream(ConfigOption.values())
                .filter(option -> option.category == this.selectedCategory)
                .toList();
    }

    private Layout layout() {
        int panelWidth = Math.min(BASE_PANEL_WIDTH, Math.max(360, this.width - 24));
        int panelHeight = Math.min(BASE_PANEL_HEIGHT, Math.max(220, this.height - 24));
        panelWidth = Math.min(panelWidth, Math.max(1, this.width - 8));
        panelHeight = Math.min(panelHeight, Math.max(1, this.height - 8));
        int panelX = snap((this.width - panelWidth) / 2.0D);
        int panelY = snap((this.height - panelHeight) / 2.0D);
        boolean compact = panelWidth < 520 || panelHeight < 270;
        int sidebarWidth = compact ? 106 : 142;
        int dividerX = panelX + sidebarWidth + 28;
        int contentX = dividerX + 14;
        int contentTop = panelY + 62;
        int footerTop = panelY + panelHeight - (compact ? 40 : 48);
        int contentWidth = Math.max(100, panelX + panelWidth - contentX - 18);
        int contentHeight = Math.max(42, footerTop - contentTop - 8);
        int rowHeight = compact ? 34 : ROW_HEIGHT;
        int visibleRows = Math.max(1, (contentHeight + ROW_GAP) / (rowHeight + ROW_GAP));
        return new Layout(panelX, panelY, panelWidth, panelHeight, sidebarWidth, dividerX, contentX, contentTop, contentWidth, contentHeight, footerTop, rowHeight, visibleRows, compact);
    }

    private Rect resetButton(Layout layout) {
        return new Rect(layout.panelX() + 18, layout.footerTop() + 2, 78, 22);
    }

    private Rect cancelButton(Layout layout) {
        return new Rect(layout.panelX() + layout.panelWidth() - 190, layout.footerTop() + 2, 70, 22);
    }

    private Rect saveButton(Layout layout) {
        return new Rect(layout.panelX() + layout.panelWidth() - 112, layout.footerTop() + 2, 94, 22);
    }

    private static boolean isIn(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static boolean isIn(double mouseX, double mouseY, Rect rect) {
        return isIn(mouseX, mouseY, rect.x(), rect.y(), rect.width(), rect.height());
    }

    private enum ConfigCategory {
        TRACKER("任务追踪"),
        GUIDANCE("目标指引"),
        DIALOGUE("对话操作");

        private final String title;

        ConfigCategory(String title) {
            this.title = title;
        }
    }

    private enum ConfigOption {
        TRACKING_ENABLED(
                ConfigCategory.TRACKER,
                I18n.get("config.phantasmbriefing.tracker.enabled"),
                I18n.get("config.phantasmbriefing.tracker.enabled.description")
        ) {
            @Override boolean value(PhantasmConfigScreen screen) { return screen.questTrackingEnabled; }
            @Override void toggle(PhantasmConfigScreen screen) { screen.questTrackingEnabled = !screen.questTrackingEnabled; }
        },
        SHOW_OBJECTIVE_TRACKING_TEXT(
                ConfigCategory.TRACKER,
                I18n.get("config.phantasmbriefing.tracker.show_objective_tracking_text"),
                I18n.get("config.phantasmbriefing.tracker.show_objective_tracking_text.description")
        ) {
            @Override boolean value(PhantasmConfigScreen screen) { return screen.questTrackerShowObjectiveTrackingText; }
            @Override void toggle(PhantasmConfigScreen screen) { screen.questTrackerShowObjectiveTrackingText = !screen.questTrackerShowObjectiveTrackingText; }
        },
        ANCHOR_RIGHT(ConfigCategory.TRACKER, "面板所在位置", "在左上角与右上角之间切换。") {
            @Override boolean value(PhantasmConfigScreen screen) { return screen.questTrackerAnchorRight; }
            @Override void toggle(PhantasmConfigScreen screen) { screen.questTrackerAnchorRight = !screen.questTrackerAnchorRight; }
            @Override String valueLabel(PhantasmConfigScreen screen) { return value(screen) ? "右上" : "左上"; }
        },
        WORLD_MARKER(ConfigCategory.GUIDANCE, "世界目标指引", "显示任务地点或结构的方向标记。") {
            @Override boolean value(PhantasmConfigScreen screen) { return screen.worldMarkerEnabled; }
            @Override void toggle(PhantasmConfigScreen screen) { screen.worldMarkerEnabled = !screen.worldMarkerEnabled; }
        },
        NPC_INDICATOR(ConfigCategory.GUIDANCE, "NPC 任务标记", "显示可接、推进和交付任务的 NPC。") {
            @Override boolean value(PhantasmConfigScreen screen) { return screen.npcIndicatorEnabled; }
            @Override void toggle(PhantasmConfigScreen screen) { screen.npcIndicatorEnabled = !screen.npcIndicatorEnabled; }
        },
        MARKER_DISTANCE(ConfigCategory.GUIDANCE, "标记显示距离", "在目标与 NPC 标记旁显示米数。") {
            @Override boolean value(PhantasmConfigScreen screen) { return screen.markerDistanceEnabled; }
            @Override void toggle(PhantasmConfigScreen screen) { screen.markerDistanceEnabled = !screen.markerDistanceEnabled; }
        },
        DIALOGUE_STYLE(ConfigCategory.DIALOGUE, "对话显示样式", "在底部栏、头像气泡和女仆式头顶气泡之间循环切换。") {
            @Override boolean value(PhantasmConfigScreen screen) { return screen.dialogueHudStyle != DialogueHudStyle.DIALOGUE_BAR; }
            @Override void toggle(PhantasmConfigScreen screen) { screen.dialogueHudStyle = screen.dialogueHudStyle.next(); }
            @Override String valueLabel(PhantasmConfigScreen screen) {
                return switch (screen.dialogueHudStyle) {
                    case DIALOGUE_BAR -> "对话栏";
                    case PORTRAIT_BUBBLE -> "头像气泡";
                    case MAID_WORLD_BUBBLE -> "头顶气泡";
                };
            }
        };

        private final ConfigCategory category;
        private final String title;
        private final String description;

        ConfigOption(ConfigCategory category, String title, String description) {
            this.category = category;
            this.title = title;
            this.description = description;
        }

        abstract boolean value(PhantasmConfigScreen screen);

        abstract void toggle(PhantasmConfigScreen screen);

        String valueLabel(PhantasmConfigScreen screen) {
            return value(screen) ? "开启" : "关闭";
        }
    }

    private record Layout(
            int panelX,
            int panelY,
            int panelWidth,
            int panelHeight,
            int sidebarWidth,
            int dividerX,
            int contentX,
            int contentTop,
            int contentWidth,
            int contentHeight,
            int footerTop,
            int rowHeight,
            int visibleRows,
            boolean compact
    ) {
    }

    private record Rect(int x, int y, int width, int height) {
        int right() {
            return this.x + this.width;
        }

        int bottom() {
            return this.y + this.height;
        }
    }
}
