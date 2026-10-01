package com.phantasm.briefing.client.screen;

import com.phantasm.briefing.data.QuestRuntimeStatus;
import com.phantasm.briefing.data.QuestTreeEdgeEntry;
import com.phantasm.briefing.data.QuestTreeEdgeType;
import com.phantasm.briefing.data.QuestTreeNodeEntry;
import com.phantasm.briefing.data.QuestTreeSnapshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class QuestTreeScreen extends PixelGridScreen {
    private static final int NODE_WIDTH = 132;
    private static final int NODE_HEIGHT = 54;
    private static final int MIN_ZOOM = 75;
    private static final int MAX_ZOOM = 160;
    private static final int ZOOM_STEP = 10;

    private final QuestTreeSnapshot snapshot;
    private int panX;
    private int panY;
    private int zoomPercent;
    private boolean dragging;
    private double lastMouseX;
    private double lastMouseY;
    private String selectedQuestId;

    public QuestTreeScreen(QuestTreeSnapshot snapshot) {
        super(Component.literal("任务树"));
        this.snapshot = Objects.requireNonNull(snapshot);
        this.panX = 0;
        this.panY = 0;
        this.zoomPercent = 100;
        this.selectedQuestId = snapshot.nodes().isEmpty() ? "" : snapshot.nodes().get(0).questId();
    }

    @Override
    protected void renderModalBackdrop(GuiGraphics guiGraphics) {
        guiGraphics.fill(0, 0, this.width, this.height, 0xC0050A12);
        guiGraphics.fillGradient(0, 0, this.width, this.height, 0x280D2442, 0x08050A12);
    }

    @Override
    protected void buildWidgets() {
    }

    @Override
    protected void renderScreenContents(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Font font = Objects.requireNonNull(this.font);
        Layout layout = layout();
        guiGraphics.fill(layout.panelX(), layout.panelY(), layout.panelX() + layout.panelWidth(), layout.panelY() + layout.panelHeight(), 0xF108101A);
        guiGraphics.fillGradient(
                layout.panelX() + 1,
                layout.panelY() + 1,
                layout.panelX() + layout.panelWidth() - 1,
                layout.panelY() + layout.panelHeight() - 1,
                0x360F2440,
                0x1608111B
        );
        guiGraphics.fill(layout.panelX(), layout.panelY(), layout.panelX() + layout.panelWidth(), layout.panelY() + 1, 0xC672B7F2);
        guiGraphics.fill(layout.panelX(), layout.panelY() + layout.panelHeight() - 1, layout.panelX() + layout.panelWidth(), layout.panelY() + layout.panelHeight(), 0x7A33567A);
        guiGraphics.fill(layout.panelX(), layout.panelY(), layout.panelX() + 1, layout.panelY() + layout.panelHeight(), 0x7A33567A);
        guiGraphics.fill(layout.panelX() + layout.panelWidth() - 1, layout.panelY(), layout.panelX() + layout.panelWidth(), layout.panelY() + layout.panelHeight(), 0x7A33567A);
        guiGraphics.drawString(font, "任务树", layout.panelX() + 12, layout.panelY() + 10, 0xEAF6FF, true);
        guiGraphics.drawString(font, "ESC 关闭  |  左键查看详情  |  拖拽平移  |  滚轮平移  |  Ctrl+滚轮缩放", layout.panelX() + 84, layout.panelY() + 10, 0x9EC4E8, false);
        guiGraphics.drawString(font, "缩放 " + this.zoomPercent + "%", layout.detailX(), layout.panelY() + 10, 0x9EC4E8, false);
        guiGraphics.fill(layout.panelX() + 12, layout.headerBottomY(), layout.panelX() + layout.panelWidth() - 12, layout.headerBottomY() + 1, 0x7A4C84B5);

        int canvasLeft = layout.canvasX();
        int canvasTop = layout.canvasY();
        int canvasRight = canvasLeft + layout.canvasWidth();
        int canvasBottom = canvasTop + layout.canvasHeight();
        guiGraphics.fill(canvasLeft, canvasTop, canvasRight, canvasBottom, 0xE1091320);
        guiGraphics.fillGradient(canvasLeft, canvasTop, canvasRight, canvasBottom, 0x2A153252, 0x0C09111B);
        guiGraphics.fill(canvasLeft, canvasTop, canvasRight, canvasTop + 1, 0x7A63A6DC);
        guiGraphics.fill(canvasLeft, canvasBottom - 1, canvasRight, canvasBottom, 0x4A27425F);
        guiGraphics.fill(canvasLeft, canvasTop, canvasLeft + 1, canvasBottom, 0x5A33597F);
        guiGraphics.fill(canvasRight - 1, canvasTop, canvasRight, canvasBottom, 0x5A33597F);
        guiGraphics.fill(layout.detailDividerX(), canvasTop, layout.detailDividerX() + 1, canvasBottom, 0x6A3C658D);

        Map<String, QuestTreeNodeEntry> nodesById = new LinkedHashMap<>();
        for (QuestTreeNodeEntry node : this.snapshot.nodes()) {
            nodesById.put(node.questId(), node);
        }

        renderDetailPanel(guiGraphics, font, layout, nodesById.get(this.selectedQuestId));

        guiGraphics.enableScissor(canvasLeft, canvasTop, canvasRight, canvasBottom);
        for (QuestTreeEdgeEntry edge : this.snapshot.edges()) {
            QuestTreeNodeEntry from = nodesById.get(edge.fromQuestId());
            QuestTreeNodeEntry to = nodesById.get(edge.toQuestId());
            if (from != null && to != null) {
                drawEdge(guiGraphics, layout, from, to, edge.edgeType());
            }
        }
        for (QuestTreeNodeEntry node : this.snapshot.nodes()) {
            drawNode(guiGraphics, font, layout, node, mouseX, mouseY);
        }
        guiGraphics.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isInCanvas(mouseX, mouseY)) {
            QuestTreeNodeEntry clickedNode = findNodeAt(mouseX, mouseY, layout());
            if (clickedNode != null) {
                this.selectedQuestId = clickedNode.questId();
                return true;
            }
            this.dragging = true;
            this.lastMouseX = mouseX;
            this.lastMouseY = mouseY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!this.dragging || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        this.panX += snap(mouseX - this.lastMouseX);
        this.panY += snap(mouseY - this.lastMouseY);
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            this.dragging = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isInCanvas(mouseX, mouseY)) {
            if (hasControlDown()) {
                zoomBy(delta > 0.0D ? ZOOM_STEP : delta < 0.0D ? -ZOOM_STEP : 0);
                return true;
            }
            this.panY += delta > 0.0D ? 22 : delta < 0.0D ? -22 : 0;
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
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            this.panX += 24;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            this.panX -= 24;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            this.panY += 24;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            this.panY -= 24;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_EQUAL || keyCode == GLFW.GLFW_KEY_KP_ADD) {
            zoomBy(ZOOM_STEP);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_MINUS || keyCode == GLFW.GLFW_KEY_KP_SUBTRACT) {
            zoomBy(-ZOOM_STEP);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    private void drawNode(GuiGraphics guiGraphics, Font font, Layout layout, QuestTreeNodeEntry node, int mouseX, int mouseY) {
        int left = layout.canvasX() + this.panX + scale(node.x());
        int top = layout.canvasY() + this.panY + scale(node.y());
        int nodeWidth = scaledNodeWidth();
        int nodeHeight = scaledNodeHeight();
        int accent = switch (node.status()) {
            case AVAILABLE -> 0xE0BC63;
            case ACTIVE -> 0x8FC8FF;
            case READY_TO_TURN_IN -> 0x9CE7B2;
            case COMPLETED -> 0x778AA3;
            case LOCKED -> 0x5A6170;
        };
        int borderColor = switch (node.status()) {
            case AVAILABLE -> 0xFFD9B35A;
            case ACTIVE -> 0xFF7FC7FF;
            case READY_TO_TURN_IN -> 0xFF8FE0A6;
            case COMPLETED -> 0xFF8A9CB7;
            case LOCKED -> 0xFF5E6776;
        };
        int innerBorderColor = switch (node.status()) {
            case AVAILABLE -> 0xFFF3DA8A;
            case ACTIVE -> 0xFFD5EEFF;
            case READY_TO_TURN_IN -> 0xFFD8F7E1;
            case COMPLETED -> 0xFFD2DCEB;
            case LOCKED -> 0xFF8B96A7;
        };
        boolean selected = node.questId().equals(this.selectedQuestId);
        boolean hovered = mouseX >= left && mouseX < left + nodeWidth && mouseY >= top && mouseY < top + nodeHeight;
        int accentBarWidth = Math.max(3, scaleSize(4));
        guiGraphics.fill(left - 2, top - 2, left + nodeWidth + 2, top + nodeHeight + 2, selected ? 0xFFFFFFFF : hovered ? 0xFFF6FCFF : borderColor);
        guiGraphics.fill(left - 1, top - 1, left + nodeWidth + 1, top + nodeHeight + 1, selected ? 0xFFDDF1FF : hovered ? 0xFFBEE5FF : innerBorderColor);
        guiGraphics.fill(left, top, left + nodeWidth, top + nodeHeight, hovered ? 0xFF132338 : 0xFF0E1826);
        guiGraphics.fillGradient(left + 1, top + 1, left + nodeWidth - 1, top + nodeHeight - 1, 0x361E466D, 0x120A121C);
        guiGraphics.fill(left + 2, top + 2, left + 2 + accentBarWidth, top + nodeHeight - 2, accent);
        guiGraphics.fill(left + 2 + accentBarWidth, top + 2, left + nodeWidth - 2, top + 3, hovered ? 0xFFD1EDFF : 0xC28CC6F8);
        guiGraphics.fill(left + 2, top + nodeHeight - 3, left + nodeWidth - 2, top + nodeHeight - 2, 0x8A36597F);
        guiGraphics.fill(left + nodeWidth - 3, top + 2, left + nodeWidth - 2, top + nodeHeight - 2, 0x7A3D648B);
        guiGraphics.drawString(font, font.plainSubstrByWidth(node.title(), nodeWidth - 18), left + 10, top + 8, 0xF4FAFF, true);
        guiGraphics.drawString(font, node.status().label(), left + 10, top + 22, accent, true);
        String body = node.description().isBlank() ? node.questId() : node.description();
        guiGraphics.drawString(font, font.plainSubstrByWidth(body, nodeWidth - 18), left + 10, top + Math.max(34, nodeHeight - 18), 0xBDD7F6, true);
    }

    private void drawEdge(GuiGraphics guiGraphics, Layout layout, QuestTreeNodeEntry from, QuestTreeNodeEntry to, QuestTreeEdgeType edgeType) {
        int fromX = layout.canvasX() + this.panX + scale(from.x()) + scaledNodeWidth();
        int fromY = layout.canvasY() + this.panY + scale(from.y()) + (scaledNodeHeight() / 2);
        int toX = layout.canvasX() + this.panX + scale(to.x());
        int toY = layout.canvasY() + this.panY + scale(to.y()) + (scaledNodeHeight() / 2);
        int midX = fromX + ((toX - fromX) / 2);
        int color = edgeType == QuestTreeEdgeType.PREREQUISITE ? 0xB08FC8FF : 0x99E0BC63;

        drawSegment(guiGraphics, fromX, fromY, midX, fromY, color);
        drawSegment(guiGraphics, midX, fromY, midX, toY, color);
        drawSegment(guiGraphics, midX, toY, toX, toY, color);
    }

    private void drawSegment(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int color) {
        if (x1 == x2) {
            int top = Math.min(y1, y2);
            guiGraphics.fill(x1, top, x1 + 3, Math.max(y1, y2) + 3, color);
            return;
        }
        if (y1 == y2) {
            int left = Math.min(x1, x2);
            guiGraphics.fill(left, y1, Math.max(x1, x2) + 3, y1 + 3, color);
        }
    }

    private void renderDetailPanel(GuiGraphics guiGraphics, Font font, Layout layout, QuestTreeNodeEntry node) {
        int left = layout.detailX();
        int top = layout.detailY();
        int right = left + layout.detailWidth();
        int bottom = top + layout.detailHeight();
        guiGraphics.fill(left, top, right, bottom, 0xEE0D1623);
        guiGraphics.fillGradient(left + 1, top + 1, right - 1, bottom - 1, 0x30193B5F, 0x0E09111A);
        guiGraphics.fill(left, top, right, top + 1, 0xA272B7F2);
        guiGraphics.fill(left, bottom - 1, right, bottom, 0x55305172);
        guiGraphics.fill(left, top, left + 1, bottom, 0x6A3C658D);
        guiGraphics.fill(right - 1, top, right, bottom, 0x6A3C658D);
        guiGraphics.drawString(font, "任务详情", left + 12, top + 12, 0xEAF6FF, true);

        if (node == null) {
            guiGraphics.drawString(font, "点击左侧节点查看详情。", left + 12, top + 34, 0xAEC7E4, true);
            return;
        }

        int accent = statusAccent(node.status());
        int y = top + 34;
        guiGraphics.drawString(font, font.plainSubstrByWidth(node.title(), layout.detailWidth() - 24), left + 12, y, 0xF8FCFF, true);
        y += font.lineHeight + 6;
        guiGraphics.drawString(font, node.status().label(), left + 12, y, accent, true);
        y += font.lineHeight + 4;
        guiGraphics.drawString(font, font.plainSubstrByWidth(node.questId(), layout.detailWidth() - 24), left + 12, y, 0xA9C7E6, true);
        y += font.lineHeight + 8;

        y = drawDetailBlock(guiGraphics, font, left + 12, y, layout.detailWidth() - 24, "说明", wrapLines(font, node.description().isBlank() ? "暂无任务说明。" : node.description(), layout.detailWidth() - 24), 3);
        y = drawDetailBlock(guiGraphics, font, left + 12, y, layout.detailWidth() - 24, "目标", wrapLines(font, objectiveSummary(node), layout.detailWidth() - 24), 4);
        y = drawDetailBlock(guiGraphics, font, left + 12, y, layout.detailWidth() - 24, "前置", wrapLines(font, relationSummary(node.parentQuestIds(), "无前置任务"), layout.detailWidth() - 24), 2);
        drawDetailBlock(guiGraphics, font, left + 12, y, layout.detailWidth() - 24, "后续", wrapLines(font, relationSummary(node.nextQuestIds(), "无推荐后续任务"), layout.detailWidth() - 24), 2);
    }

    private int drawDetailBlock(GuiGraphics guiGraphics, Font font, int x, int y, int width, String label, List<String> lines, int maxLines) {
        guiGraphics.drawString(font, label, x, y, 0xE0BC63, true);
        int drawY = y + font.lineHeight + 2;
        int shown = 0;
        for (String line : lines) {
            if (shown >= maxLines) {
                guiGraphics.drawString(font, "...", x, drawY, 0xA9C7E6, true);
                drawY += font.lineHeight + 2;
                break;
            }
            guiGraphics.drawString(font, font.plainSubstrByWidth(line, width), x, drawY, 0xCFE4FB, true);
            drawY += font.lineHeight + 2;
            shown++;
        }
        return drawY + 4;
    }

    private List<String> wrapLines(Font font, String text, int width) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            lines.add("暂无");
            return lines;
        }
        String remaining = text;
        while (!remaining.isBlank()) {
            String line = font.plainSubstrByWidth(remaining, width);
            if (line.isBlank()) {
                break;
            }
            lines.add(line);
            remaining = remaining.substring(line.length()).stripLeading();
        }
        return lines.isEmpty() ? List.of(text) : lines;
    }

    private String objectiveSummary(QuestTreeNodeEntry node) {
        return node.objectiveLines().isEmpty()
                ? "暂无任务目标。"
                : String.join(" / ", node.objectiveLines());
    }

    private String relationSummary(List<String> questIds, String emptyText) {
        return questIds == null || questIds.isEmpty() ? emptyText : String.join(" / ", questIds);
    }

    private int statusAccent(QuestRuntimeStatus status) {
        return switch (status) {
            case AVAILABLE -> 0xE0BC63;
            case ACTIVE -> 0x8FC8FF;
            case READY_TO_TURN_IN -> 0x9CE7B2;
            case COMPLETED -> 0x97A9C2;
            case LOCKED -> 0x7A8596;
        };
    }

    private QuestTreeNodeEntry findNodeAt(double mouseX, double mouseY, Layout layout) {
        for (QuestTreeNodeEntry node : this.snapshot.nodes()) {
            int left = layout.canvasX() + this.panX + scale(node.x());
            int top = layout.canvasY() + this.panY + scale(node.y());
            if (mouseX >= left && mouseX < left + scaledNodeWidth() && mouseY >= top && mouseY < top + scaledNodeHeight()) {
                return node;
            }
        }
        return null;
    }

    private void zoomBy(int delta) {
        if (delta == 0) {
            return;
        }
        this.zoomPercent = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, this.zoomPercent + delta));
    }

    private int scale(int value) {
        return Math.round(value * (this.zoomPercent / 100.0F));
    }

    private int scaleSize(int value) {
        return Math.max(1, Math.round(value * (this.zoomPercent / 100.0F)));
    }

    private int scaledNodeWidth() {
        return scaleSize(NODE_WIDTH);
    }

    private int scaledNodeHeight() {
        return scaleSize(NODE_HEIGHT);
    }

    private boolean isInCanvas(double mouseX, double mouseY) {
        Layout layout = layout();
        return mouseX >= layout.canvasX()
                && mouseX < layout.canvasX() + layout.canvasWidth()
                && mouseY >= layout.canvasY()
                && mouseY < layout.canvasY() + layout.canvasHeight();
    }

    private Layout layout() {
        int panelMargin = 12;
        int panelWidth = Math.max(420, this.width - (panelMargin * 2));
        int panelHeight = Math.max(260, this.height - (panelMargin * 2));
        int panelX = snap((this.width - panelWidth) / 2.0D);
        int panelY = snap((this.height - panelHeight) / 2.0D);
        int headerBottomY = panelY + 30;
        int contentTop = headerBottomY + 12;
        int contentBottom = panelY + panelHeight - 12;
        int detailGap = 14;
        int detailWidth = Math.max(220, Math.min(280, panelWidth / 4));
        int canvasX = panelX + 12;
        int canvasY = contentTop;
        int canvasWidth = panelWidth - 24 - detailWidth - detailGap;
        int canvasHeight = contentBottom - contentTop;
        int detailX = canvasX + canvasWidth + detailGap;
        int detailY = contentTop;
        int detailHeight = canvasHeight;
        return new Layout(
                panelX,
                panelY,
                panelWidth,
                panelHeight,
                headerBottomY,
                canvasX,
                canvasY,
                canvasWidth,
                canvasHeight,
                detailX - 7,
                detailX,
                detailY,
                detailWidth,
                detailHeight
        );
    }

    private record Layout(
            int panelX,
            int panelY,
            int panelWidth,
            int panelHeight,
            int headerBottomY,
            int canvasX,
            int canvasY,
            int canvasWidth,
            int canvasHeight,
            int detailDividerX,
            int detailX,
            int detailY,
            int detailWidth,
            int detailHeight
    ) {
    }
}
