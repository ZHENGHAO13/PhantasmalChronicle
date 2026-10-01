package com.phantasm.briefing.client.screen;

import com.phantasm.briefing.client.TutorialTextureCache;
import com.phantasm.briefing.data.QuestJournalObjectiveEntry;
import com.phantasm.briefing.data.QuestObjectiveType;
import com.phantasm.briefing.network.ModNetwork;
import com.phantasm.briefing.network.packet.MarkTutorialViewedC2SPacket;
import com.phantasm.briefing.network.packet.CompleteTutorialObjectiveC2SPacket;
import com.phantasm.briefing.client.widgets.HoverButtonWidget;
import com.phantasm.briefing.data.ManualNodeSpec;
import com.phantasm.briefing.data.ManualPageSpec;
import com.phantasm.briefing.data.ManualSpec;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Browse the server-approved manual outline; lesson visibility is filtered before the packet is sent. */
public final class TravelerManualScreen extends PixelGridScreen {
    private static final int ROW_HEIGHT = 19;
    private final QuestJournalScreen journal;
    private final List<ManualSpec> manuals;
    private final String tutorialQuestId;
    private final String tutorialPhaseId;
    private final QuestJournalObjectiveEntry tutorialObjective;
    private final String tutorialManualId;
    private final String tutorialLessonId;
    private HoverButtonWidget learnedButton;
    private final Set<String> collapsedGroups = new HashSet<>();
    private final List<OutlineHit> outlineHits = new ArrayList<>();
    private String selectedManualId;
    private String selectedLessonId;
    private int pageIndex;
    private int outlineScroll;
    private int textScroll;
    private HoverButtonWidget previousButton;
    private HoverButtonWidget nextButton;
    private TutorialTextureCache.LoadResult imageResult;

    public TravelerManualScreen(QuestJournalScreen journal, List<ManualSpec> manuals,
                                String manualId, String lessonId, String pageId) {
        this(journal, manuals, manualId, lessonId, pageId, "", "", null);
    }

    public TravelerManualScreen(QuestJournalScreen journal, List<ManualSpec> manuals,
                                String manualId, String lessonId, String pageId,
                                String tutorialQuestId, String tutorialPhaseId,
                                QuestJournalObjectiveEntry tutorialObjective) {
        super(Component.translatable("screen.phantasmbriefing.traveler_manual"));
        this.tutorialQuestId = tutorialQuestId;
        this.tutorialPhaseId = tutorialPhaseId;
        this.tutorialObjective = tutorialObjective;
        this.tutorialManualId = manualId;
        this.tutorialLessonId = lessonId;
        this.journal = journal;
        this.manuals = List.copyOf(manuals);
        ManualSpec requested = manuals.stream().filter(m -> m.manualId().equals(manualId)).findFirst().orElse(null);
        if (requested == null && !manuals.isEmpty()) requested = manuals.get(0);
        this.selectedManualId = requested == null ? "" : requested.manualId();
        ManualNodeSpec lesson = requested == null ? null : requested.findLesson(lessonId, pageId);
        this.selectedLessonId = lesson == null ? "" : lesson.nodeId();
        if (lesson != null) {
            for (int i = 0; i < lesson.pages().size(); i++) {
                if (lesson.pages().get(i).pageId().equals(pageId)) { this.pageIndex = i; break; }
            }
        }
    }

    @Override
    protected void init() {
        super.init();
        loadImage();
        updateButtons();
    }

    @Override
    protected void buildWidgets() {
        Layout l = layout();
        int bottom = l.y + l.h - 28;
        this.addPixelWidget(button(l.x + 12, bottom, 88, Component.translatable("manual.phantasmbriefing.back"), b -> onClose()));
        this.previousButton = this.addPixelWidget(button(l.x + l.w - 186, bottom, 84,
                Component.translatable("manual.phantasmbriefing.previous"), b -> movePage(-1)));
        this.nextButton = this.addPixelWidget(button(l.x + l.w - 94, bottom, 82,
                Component.translatable("manual.phantasmbriefing.next"), b -> movePage(1)));
        this.learnedButton = this.addPixelWidget(button(l.x + l.w - 298, bottom, 106,
                Component.translatable("manual.phantasmbriefing.learned"), b -> completeLegacyTutorial()));
    }

    private HoverButtonWidget button(int x, int y, int width, Component label, HoverButtonWidget.PressAction press) {
        return new HoverButtonWidget(this.font, x, y, width, 20, label,
                0xFF243142, 0xFF35506D, 0xFF6B87A6, 0xFFFFFFFF, 0xFFFFFFFF, press);
    }

    @Override
    protected void renderScreenContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Layout l = layout();
        graphics.fill(l.x, l.y, l.x + l.w, l.y + l.h, 0xF412171F);
        graphics.fill(l.x, l.y, l.x + l.w, l.y + 1, 0xFF6687B4);
        graphics.drawString(this.font, Component.translatable("screen.phantasmbriefing.traveler_manual"),
                l.x + 16, l.y + 13, 0xFFFFFFFF, true);
        graphics.fill(l.x + 12, l.y + 33, l.x + l.w - 12, l.y + 35, 0x996687B4);
        graphics.fill(l.split, l.top, l.split + 1, l.bottom, 0x99415A7A);
        renderOutline(graphics, l, mouseX, mouseY);
        renderArticle(graphics, l);
    }

    private void renderOutline(GuiGraphics graphics, Layout l, int mouseX, int mouseY) {
        this.outlineHits.clear();
        graphics.enableScissor(l.x + 12, l.top, l.split - 6, l.bottom);
        int y = l.top + 2 - this.outlineScroll;
        for (ManualSpec manual : this.manuals) {
            y = renderOutlineRow(graphics, l, y, 0, manual.manualId(), "",
                    manual.title(), false, mouseX, mouseY);
            boolean open = !this.collapsedGroups.contains(manual.manualId());
            if (!open) continue;
            for (ManualNodeSpec node : manual.sections()) y = renderNode(graphics, l, y, 1, manual.manualId(), node, mouseX, mouseY);
            y += 4;
        }
        graphics.disableScissor();
        if (manuals.isEmpty()) graphics.drawString(this.font,
                Component.translatable("manual.phantasmbriefing.empty"), l.x + 18, l.top + 8, 0xFFFFFFFF, false);
    }

    private int renderNode(GuiGraphics graphics, Layout l, int y, int depth, String manualId,
                           ManualNodeSpec node, int mouseX, int mouseY) {
        y = renderOutlineRow(graphics, l, y, depth, manualId, node.nodeId(), node.title(),
                node.isGroup(), mouseX, mouseY);
        if (node.isGroup() && !this.collapsedGroups.contains(manualId + "/" + node.nodeId())) {
            for (ManualNodeSpec child : node.children()) y = renderNode(graphics, l, y, depth + 1, manualId, child, mouseX, mouseY);
        }
        return y;
    }

    private int renderOutlineRow(GuiGraphics graphics, Layout l, int y, int depth, String manualId,
                                 String nodeId, String title, boolean group, int mouseX, int mouseY) {
        int x = l.x + 15 + Math.min(depth, 4) * 11;
        int width = l.split - 8 - x;
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ROW_HEIGHT;
        boolean selected = manualId.equals(this.selectedManualId) && !nodeId.isBlank() && nodeId.equals(this.selectedLessonId);
        if (selected || hovered) graphics.fill(x, y, x + width, y + ROW_HEIGHT - 1, selected ? 0xAA344C70 : 0x66304560);
        String key = nodeId.isBlank() ? "manual.phantasmbriefing.outline_book"
                : group ? "manual.phantasmbriefing.outline_group" : "manual.phantasmbriefing.outline_lesson";
        graphics.drawString(this.font,
                Component.translatable(key, this.font.plainSubstrByWidth(title, Math.max(15, width - 17))),
                x + 3, y + 5, 0xFFFFFFFF, false);
        this.outlineHits.add(new OutlineHit(x, y, width, ROW_HEIGHT, manualId, nodeId, group));
        return y + ROW_HEIGHT;
    }

    private void renderArticle(GuiGraphics graphics, Layout l) {
        int x = l.split + 14;
        int width = l.x + l.w - x - 14;
        graphics.enableScissor(x, l.top, x + width, l.bottom);
        ManualNodeSpec lesson = selectedLesson();
        if (lesson == null || lesson.pages().isEmpty()) {
            graphics.drawString(this.font, Component.translatable("manual.phantasmbriefing.choose_lesson"),
                    x + 4, l.top + 8, 0xFFFFFFFF, false);
            graphics.disableScissor();
            return;
        }
        ManualPageSpec page = currentPage();
        graphics.drawString(this.font, Component.literal(lesson.title()), x + 3, l.top + 4, 0xFFFFFFFF, true);
        graphics.drawString(this.font, Component.translatable("manual.phantasmbriefing.page_counter", pageIndex + 1, lesson.pages().size()),
                x + Math.max(0, width - 74), l.top + 4, 0xFFFFFFFF, false);
        int contentY = l.top + 23;
        int available = Math.max(20, l.bottom - contentY - 3);
        boolean hasImage = !page.image().isBlank();
        int imageHeight = hasImage ? (!page.title().isBlank() || !page.text().isBlank() ? available * 3 / 5 : available) : 0;
        if (hasImage) renderImage(graphics, x, contentY, width, imageHeight);
        int textY = contentY + (hasImage ? imageHeight + 6 : 0);
        int textHeight = Math.max(0, l.bottom - textY - 3);
        if (textHeight > 0 && (!page.title().isBlank() || !page.text().isBlank())) {
            graphics.fill(x, textY, x + width, textY + textHeight, 0xBB1C2532);
            int drawY = textY + 7 - textScroll;
            graphics.disableScissor();
            graphics.enableScissor(x, textY, x + width, textY + textHeight);
            if (!page.title().isBlank()) {
                for (var line : this.font.split(Component.literal(page.title()), Math.max(15,width - 16))) {
                    graphics.drawString(this.font, line, x + 7, drawY, 0xFFFFFFFF, true);
                    drawY += this.font.lineHeight + 2;
                }
                drawY += 5;
            }
            if (!page.text().isBlank()) for (var line : this.font.split(Component.literal(page.text()), Math.max(15,width - 16))) {
                graphics.drawString(this.font, line, x + 7, drawY, 0xFFFFFFFF, false);
                drawY += this.font.lineHeight + 2;
            }
            graphics.disableScissor();
            return;
        }
        graphics.disableScissor();
    }

    private void renderImage(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xFF0D1117);
        if (imageResult == null || imageResult.texture() == null) {
            graphics.drawCenteredString(this.font, Component.translatable("manual.phantasmbriefing.image_missing"),
                    x + width / 2, y + height / 2, 0xFFFFFFFF);
            return;
        }
        TutorialTextureCache.TextureEntry tex = imageResult.texture();
        double scale = Math.min((double) width / tex.width(), (double) height / tex.height());
        int dw = Math.max(1,(int)Math.round(tex.width() * scale)), dh = Math.max(1,(int)Math.round(tex.height() * scale));
        graphics.blit(tex.location(), x + (width - dw)/2, y + (height - dh)/2, dw, dh,
                0.0F, 0.0F, tex.width(), tex.height(), tex.width(), tex.height());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Layout l = layout();
        if (button == 0 && mouseX >= l.x + 12 && mouseX < l.split && mouseY >= l.top && mouseY < l.bottom) {
            for (OutlineHit hit : this.outlineHits) if (hit.contains(mouseX, mouseY)) {
                String collapseKey = hit.nodeId.isBlank() ? hit.manualId : hit.manualId + "/" + hit.nodeId;
                if (hit.group || hit.nodeId.isBlank()) {
                    if (!collapsedGroups.add(collapseKey)) collapsedGroups.remove(collapseKey);
                } else {
                    selectedManualId = hit.manualId;
                    selectedLessonId = hit.nodeId;
                    pageIndex = 0;
                    textScroll = 0;
                    loadImage();
                    updateButtons();
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        Layout l = layout();
        if (mouseY < l.top || mouseY >= l.bottom || delta == 0.0D) return super.mouseScrolled(mouseX, mouseY, delta);
        int step = delta > 0 ? -ROW_HEIGHT : ROW_HEIGHT;
        if (mouseX >= l.x + 12 && mouseX < l.split) {
            int count = outlineRowCount();
            outlineScroll = clamp(outlineScroll + step, 0, Math.max(0, count * ROW_HEIGHT + 12 - (l.bottom - l.top)));
            return true;
        }
        if (mouseX >= l.split && mouseX < l.x + l.w) {
            ManualPageSpec page = currentPage();
            if (page == null) return true;
            int width = l.x + l.w - l.split - 28;
            int textHeight = Math.max(20, (l.bottom - l.top - 23) * (page.image().isBlank() ? 1 : 2) / (page.image().isBlank() ? 1 : 5));
            int lines = this.font.split(Component.literal(page.title() + "\n" + page.text()), Math.max(15, width - 16)).size();
            textScroll = clamp(textScroll + step, 0, Math.max(0, 23 + lines * (this.font.lineHeight + 2) - textHeight));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private int outlineRowCount() {
        int count = 0;
        for (ManualSpec manual : manuals) {
            count++;
            if (!collapsedGroups.contains(manual.manualId())) for (ManualNodeSpec node : manual.sections()) count += countRows(manual.manualId(),node);
        }
        return count;
    }

    private int countRows(String manualId, ManualNodeSpec node) {
        int count = 1;
        if (node.isGroup() && !collapsedGroups.contains(manualId + "/" + node.nodeId()))
            for (ManualNodeSpec child : node.children()) count += countRows(manualId, child);
        return count;
    }

    private ManualNodeSpec selectedLesson() {
        for (ManualSpec manual : this.manuals) if (manual.manualId().equals(this.selectedManualId)) {
            return manual.findLesson(this.selectedLessonId, "");
        }
        return null;
    }

    private ManualPageSpec currentPage() {
        ManualNodeSpec lesson = selectedLesson();
        return lesson == null || lesson.pages().isEmpty() ? null : lesson.pages().get(clamp(pageIndex, 0, lesson.pages().size() - 1));
    }

    private void loadImage() {
        ManualPageSpec page = currentPage();
        this.imageResult = page == null || page.image().isBlank() ? null : TutorialTextureCache.loadManual(page.image());
    }

    private void updateButtons() {
        ManualNodeSpec lesson = selectedLesson();
        int count = lesson == null ? 0 : lesson.pages().size();
        if (previousButton != null) previousButton.setVisible(pageIndex > 0 && count > 0);
        if (nextButton != null) nextButton.setVisible(pageIndex < count - 1);
        if (learnedButton != null) learnedButton.setVisible(tutorialObjective != null
                && tutorialObjective.objectiveType() == QuestObjectiveType.TUTORIAL
                && tutorialObjective.tutorialPages().isEmpty()
                && tutorialObjective.tutorialAccessible() && !tutorialObjective.completed()
                && tutorialManualId.equals(selectedManualId)
                && tutorialLessonId.equals(selectedLessonId) && count > 0 && pageIndex == count - 1);
    }

    private void movePage(int difference) {
        ManualNodeSpec lesson = selectedLesson();
        if (lesson == null) return;
        pageIndex = clamp(pageIndex + difference, 0, lesson.pages().size() - 1);
        textScroll = 0;
        loadImage();
        updateButtons();
    }

    private void completeLegacyTutorial() {
        if (this.learnedButton == null || !this.learnedButton.isVisible() || this.minecraft == null) return;
        this.learnedButton.setActive(false);
        // Both packets share the ordered play channel; the server checks the active objective and viewed flag.
        ModNetwork.CHANNEL.sendToServer(new MarkTutorialViewedC2SPacket(tutorialQuestId, tutorialPhaseId,
                tutorialObjective.objectiveId()));
        ModNetwork.CHANNEL.sendToServer(new CompleteTutorialObjectiveC2SPacket(tutorialQuestId, tutorialPhaseId,
                tutorialObjective.objectiveId()));
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(this.journal);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private Layout layout() {
        int w = Math.min(800, Math.max(450, width - 24)), h = Math.min(470, Math.max(270, height - 24));
        int x = snap((width - w)/2.0D), y = snap((height - h)/2.0D);
        int split = x + Math.max(145, Math.min(226, w / 3));
        return new Layout(x, y, w, h, split, y + 44, y + h - 36);
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    private record Layout(int x, int y, int w, int h, int split, int top, int bottom) {}
    private record OutlineHit(int x, int y, int w, int h, String manualId, String nodeId, boolean group) {
        boolean contains(double mx, double my) { return mx >= x && mx < x+w && my >= y && my < y+h; }
    }
}
