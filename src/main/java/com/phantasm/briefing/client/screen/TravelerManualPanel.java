package com.phantasm.briefing.client.screen;

import com.phantasm.briefing.client.ui.DarkFantasyTheme;

import com.phantasm.briefing.client.ContentTextureCache;
import com.phantasm.briefing.client.ui.JournalOrnaments;
import com.phantasm.briefing.client.ui.PixelRenderUtil;
import com.phantasm.briefing.data.ManualNodeSpec;
import com.phantasm.briefing.data.ManualPageSpec;
import com.phantasm.briefing.data.ManualSpec;
import com.phantasm.briefing.data.QuestJournalObjectiveEntry;
import com.phantasm.briefing.data.QuestObjectiveType;
import com.phantasm.briefing.network.ModNetwork;
import com.phantasm.briefing.network.packet.CompleteManualReadObjectiveC2SPacket;
import com.phantasm.briefing.service.ManualReadCompletionPolicy;
import com.phantasm.briefing.data.ManualReferenceSpec;
import com.phantasm.briefing.data.QuestJournalEntry;
import com.phantasm.briefing.data.QuestJournalPhaseEntry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Contents of the handbook tab. It never owns a Minecraft Screen or creates a second window. */
final class TravelerManualPanel {
    private static final int ROW_HEIGHT = 20;
    private final List<ManualSpec> manuals;
    private final Set<String> collapsedGroups = new HashSet<>();
    private final List<OutlineHit> outlineHits = new ArrayList<>();
    private Font font;
    private Layout layout;
    private String selectedManualId = "";
    private String selectedLessonId = "";
    private int pageIndex;
    private int outlineScroll;
    private int textScroll;
    private ContentTextureCache.LoadResult imageResult;
    private String readQuestId = "";
    private String readPhaseId = "";
    private QuestJournalObjectiveEntry readObjective;
    private String readSubmittedKey = "";

    TravelerManualPanel(List<ManualSpec> manuals) {
        this.manuals = List.copyOf(manuals);
        if (!this.manuals.isEmpty()) selectLesson(this.manuals.get(0), "", "");
    }

    /** Empty IDs retain the reader's current position when switching tabs. */
    void openReference(String manualId, String lessonId, String pageId, String questId,
                       String phaseId, QuestJournalObjectiveEntry objective) {
        if (manualId == null || manualId.isBlank()) return;
        ManualSpec selected = manuals.stream().filter(manual -> manual.manualId().equals(manualId))
                .findFirst().orElse(null);
        if (selected == null) return;
        ManualNodeSpec lesson = selected.findLesson(lessonId, pageId);
        if (lesson == null) return;
        selectLesson(selected, lesson.nodeId(), pageId);
        this.readQuestId = questId == null ? "" : questId;
        this.readPhaseId = phaseId == null ? "" : phaseId;
        this.readObjective = objective;
    }

    void restorePositionFrom(TravelerManualPanel previous) {
        ManualSpec manual = manuals.stream()
                .filter(item -> item.manualId().equals(previous.selectedManualId))
                .findFirst().orElse(null);
        if (manual == null) return;
        ManualNodeSpec lesson = manual.findLesson(previous.selectedLessonId, "");
        if (lesson == null) return;
        ManualPageSpec previousPage = previous.currentPage();
        selectLesson(manual, lesson.nodeId(), previousPage == null ? "" : previousPage.pageId());
        collapsedGroups.clear();
        collapsedGroups.addAll(previous.collapsedGroups);
        outlineScroll = Math.max(0, previous.outlineScroll);
        textScroll = Math.max(0, previous.textScroll);
        readSubmittedKey = previous.readSubmittedKey;
        // Read-objective context is rebuilt from the newest server snapshot, while the local acknowledgement marker
        // survives that refresh so Back does not disappear again between the click and the completed snapshot.
    }

    private void selectLesson(ManualSpec manual, String lessonId, String pageId) {
        ManualNodeSpec lesson = manual.findLesson(lessonId, pageId);
        this.selectedManualId = manual.manualId();
        this.selectedLessonId = lesson == null ? "" : lesson.nodeId();
        this.pageIndex = 0;
        if (lesson != null && pageId != null && !pageId.isBlank()) {
            for (int index = 0; index < lesson.pages().size(); index++) {
                if (lesson.pages().get(index).pageId().equals(pageId)) {
                    this.pageIndex = index;
                    break;
                }
            }
        }
        this.textScroll = 0;
        loadImage();
    }

    void render(GuiGraphics graphics, Font font, int panelX, int panelY, int panelWidth, int panelHeight,
                int top, int bottom, int mouseX, int mouseY) {
        this.font = font;
        int split = panelX + Math.max(110, Math.min(226, panelWidth / 3));
        this.layout = new Layout(panelX, panelY, panelWidth, panelHeight, split, top, bottom);
        JournalOrnaments.panel(graphics, panelX + 12, top, split - panelX - 20, bottom - top);
        JournalOrnaments.panel(graphics, split + 5, top, panelX + panelWidth - split - 17, bottom - top);
        JournalOrnaments.divider(graphics, split, top + 4, bottom - 4);
        renderOutline(graphics, layout, mouseX, mouseY);
        renderArticle(graphics, layout);
    }

    private void renderOutline(GuiGraphics graphics, Layout l, int mouseX, int mouseY) {
        this.outlineHits.clear();
        PixelRenderUtil.withClip(graphics, l.x + 14, l.top + 3, l.split - 6, l.bottom - 4, () -> {
            int y = l.top + 6 - this.outlineScroll;
            for (ManualSpec manual : manuals) {
                y = renderOutlineRow(graphics, l, y, 0, manual.manualId(), "", manual.title(), true, mouseX, mouseY);
                if (this.collapsedGroups.contains(manual.manualId())) continue;
                for (ManualNodeSpec node : manual.sections()) {
                    y = renderNode(graphics, l, y, 1, manual.manualId(), node, mouseX, mouseY);
                }
                y += 4;
            }
        });
        if (manuals.isEmpty()) graphics.drawString(font, Component.translatable("manual.phantasmbriefing.empty"),
                l.x + 18, l.top + 10, DarkFantasyTheme.TEXT_SECONDARY, false);
    }

    private int renderNode(GuiGraphics graphics, Layout l, int y, int depth, String manualId,
                           ManualNodeSpec node, int mouseX, int mouseY) {
        y = renderOutlineRow(graphics, l, y, depth, manualId, node.nodeId(), node.title(),
                node.isGroup(), mouseX, mouseY);
        if (node.isGroup() && !this.collapsedGroups.contains(manualId + "/" + node.nodeId())) {
            for (ManualNodeSpec child : node.children()) {
                y = renderNode(graphics, l, y, depth + 1, manualId, child, mouseX, mouseY);
            }
        }
        return y;
    }

    private int renderOutlineRow(GuiGraphics graphics, Layout l, int y, int depth, String manualId,
                                 String nodeId, String title, boolean group, int mouseX, int mouseY) {
        int x = l.x + 18 + Math.min(depth, 3) * 9;
        int width = Math.max(24, l.split - 10 - x);
        boolean hovered = inside(mouseX, mouseY, x, y, width, ROW_HEIGHT);
        boolean selected = manualId.equals(selectedManualId) && !nodeId.isBlank() && nodeId.equals(selectedLessonId);
        JournalOrnaments.questCard(graphics, x, y, width, ROW_HEIGHT - 1, selected, hovered, false);
        String key = nodeId.isBlank() ? "manual.phantasmbriefing.outline_book"
                : group ? "manual.phantasmbriefing.outline_group" : "manual.phantasmbriefing.outline_lesson";
        graphics.drawString(font, Component.translatable(key, font.plainSubstrByWidth(title, Math.max(12, width - 14))),
                x + 4, y + 5, DarkFantasyTheme.TEXT_SECONDARY, false);
        if (y + ROW_HEIGHT >= l.top && y <= l.bottom) {
            outlineHits.add(new OutlineHit(x, y, width, ROW_HEIGHT, manualId, nodeId, group));
        }
        return y + ROW_HEIGHT;
    }

    private void renderArticle(GuiGraphics graphics, Layout l) {
        int x = l.split + 15;
        int width = Math.max(25, l.x + l.w - x - 18);
        ManualNodeSpec lesson = selectedLesson();
        if (lesson == null || lesson.pages().isEmpty()) {
            graphics.drawString(font, Component.translatable("manual.phantasmbriefing.choose_lesson"),
                    x + 4, l.top + 10, DarkFantasyTheme.TEXT_SECONDARY, false);
            return;
        }
        ManualPageSpec page = currentPage();
        PixelRenderUtil.withClip(graphics, x, l.top + 3, x + width, l.bottom - 3, () -> {
            JournalOrnaments.headerPlaque(graphics, x, l.top + 4, width, 21, JournalOrnaments.ACCENT_BRASS);
            graphics.drawString(font, Component.literal(font.plainSubstrByWidth(lesson.title(), Math.max(16, width - 84))),
                    x + 9, l.top + 10, DarkFantasyTheme.TEXT_TITLE, true);
            graphics.drawString(font, Component.translatable("manual.phantasmbriefing.page_counter",
                            pageIndex + 1, lesson.pages().size()),
                    x + Math.max(14, width - 75), l.top + 10, DarkFantasyTheme.TEXT_SECONDARY, false);
            int contentY = l.top + 33;
            int available = Math.max(5, l.bottom - contentY - 7);
            boolean hasImage = !page.image().isBlank();
            int imageHeight = hasImage ? (!page.title().isBlank() || !page.text().isBlank()
                    ? available * 3 / 5 : available) : 0;
            if (hasImage && imageHeight > 0) renderImage(graphics, x + 3, contentY, width - 6, imageHeight);
            int textY = contentY + (hasImage ? imageHeight + 5 : 0);
            int textHeight = Math.max(0, l.bottom - textY - 7);
            if (textHeight > 0 && (!page.title().isBlank() || !page.text().isBlank())) {
                JournalOrnaments.panel(graphics, x + 2, textY, width - 4, textHeight);
                PixelRenderUtil.withClip(graphics, x + 3, textY + 2, x + width - 3, textY + textHeight - 2, () -> {
                    int lineY = textY + 7 - textScroll;
                    if (!page.title().isBlank()) {
                        for (var line : font.split(Component.literal(page.title()), Math.max(15, width - 18))) {
                            graphics.drawString(font, line, x + 8, lineY, DarkFantasyTheme.TEXT_TITLE, true);
                            lineY += font.lineHeight + 2;
                        }
                        lineY += 5;
                    }
                    if (!page.text().isBlank()) for (var line : font.split(Component.literal(page.text()), Math.max(15, width - 18))) {
                        graphics.drawString(font, line, x + 8, lineY, DarkFantasyTheme.TEXT_SECONDARY, false);
                        lineY += font.lineHeight + 2;
                    }
                });
            }
        });
    }

    private void renderImage(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, DarkFantasyTheme.BACKGROUND);
        JournalOrnaments.corners(graphics, x, y, width, height, JournalOrnaments.ACCENT_BRASS);
        if (imageResult == null || imageResult.texture() == null) {
            graphics.drawCenteredString(font, Component.translatable("manual.phantasmbriefing.image_missing"),
                    x + width / 2, y + height / 2, DarkFantasyTheme.TEXT_MUTED);
            return;
        }
        ContentTextureCache.TextureEntry texture = imageResult.texture();
        double scale = Math.min((double) width / texture.width(), (double) height / texture.height());
        int scaledWidth = Math.max(1, (int) Math.round(texture.width() * scale));
        int scaledHeight = Math.max(1, (int) Math.round(texture.height() * scale));
        graphics.blit(texture.location(), x + (width - scaledWidth) / 2, y + (height - scaledHeight) / 2,
                scaledWidth, scaledHeight, 0.0F, 0.0F, texture.width(), texture.height(), texture.width(), texture.height());
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        Layout l = layout;
        if (l == null || button != 0 || !inside(mouseX, mouseY, l.x + 12, l.top, l.split - l.x - 12, l.bottom - l.top)) return false;
        for (OutlineHit hit : outlineHits) if (hit.contains(mouseX, mouseY)) {
            String collapseKey = hit.nodeId.isBlank() ? hit.manualId : hit.manualId + "/" + hit.nodeId;
            if (hit.group || hit.nodeId.isBlank()) {
                if (!collapsedGroups.add(collapseKey)) collapsedGroups.remove(collapseKey);
            } else {
                ManualSpec manual = manuals.stream().filter(item -> item.manualId().equals(hit.manualId)).findFirst().orElse(null);
                if (manual != null) selectLesson(manual, hit.nodeId, "");
            }
            return true;
        }
        return false;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        Layout l = layout;
        if (l == null || delta == 0 || mouseY < l.top || mouseY >= l.bottom) return false;
        int step = delta > 0 ? -ROW_HEIGHT : ROW_HEIGHT;
        if (mouseX >= l.x + 12 && mouseX < l.split) {
            outlineScroll = clamp(outlineScroll + step, 0,
                    Math.max(0, outlineRowCount() * ROW_HEIGHT + manuals.size() * 4 + 12 - (l.bottom - l.top)));
            return true;
        }
        if (mouseX >= l.split && mouseX < l.x + l.w) {
            ManualPageSpec page = currentPage();
            if (page == null) return true;
            int width = Math.max(25, l.x + l.w - l.split - 33);
            int available = Math.max(5, l.bottom - l.top - 40);
            int textHeight = page.image().isBlank() ? available : available * 2 / 5;
            int lines = font.split(Component.literal(page.title() + "\n" + page.text()), Math.max(15,width - 18)).size();
            textScroll = clamp(textScroll + step, 0,
                    Math.max(0, 20 + lines * (font.lineHeight + 2) - textHeight));
            return true;
        }
        return false;
    }

    boolean hasPreviousPage() { return pageIndex > 0 && currentPage() != null; }

    boolean hasNextPage() {
        ManualNodeSpec lesson = selectedLesson();
        return lesson != null && pageIndex < lesson.pages().size() - 1;
    }

    void movePage(int delta) {
        ManualNodeSpec lesson = selectedLesson();
        if (lesson == null || lesson.pages().isEmpty()) return;
        pageIndex = clamp(pageIndex + delta, 0, lesson.pages().size() - 1);
        textScroll = 0;
        loadImage();
    }

    private boolean matchingRequiredLesson(QuestJournalObjectiveEntry objective) {
        ManualNodeSpec lesson = selectedLesson();
        if (lesson == null || objective == null) return false;
        for (ManualReferenceSpec ref : objective.manualRefs()) {
            if (!selectedManualId.equals(ref.manualId())) continue;
            ManualSpec manual = manuals.stream().filter(item -> item.manualId().equals(ref.manualId()))
                    .findFirst().orElse(null);
            ManualNodeSpec requiredLesson = manual == null ? null : manual.findLesson(ref.lessonId(), ref.pageId());
            if (requiredLesson != null && requiredLesson.nodeId().equals(lesson.nodeId())) return true;
        }
        return false;
    }

    private boolean matchingRequiredPage(QuestJournalObjectiveEntry objective) {
        ManualNodeSpec lesson = selectedLesson();
        ManualPageSpec page = currentPage();
        if (lesson == null || page == null || objective == null) return false;
        for (ManualReferenceSpec ref : objective.manualRefs()) {
            if (!selectedManualId.equals(ref.manualId())) continue;
            ManualSpec manual = manuals.stream().filter(item -> item.manualId().equals(ref.manualId()))
                    .findFirst().orElse(null);
            ManualNodeSpec requiredLesson = manual == null ? null : manual.findLesson(ref.lessonId(), ref.pageId());
            if (requiredLesson == null || !requiredLesson.nodeId().equals(lesson.nodeId())) continue;
            if (ManualReadCompletionPolicy.matches(ref.manualId(), requiredLesson.nodeId(), ref.pageId(),
                    selectedManualId, selectedLessonId, page.pageId(), pageIndex == lesson.pages().size() - 1)) {
                return true;
            }
        }
        return false;
    }

    /** Keep an active handbook-reading objective bound until it is acknowledged or completed. */
    void bindCurrentReadObjective(List<QuestJournalEntry> entries) {
        if (readObjective != null) {
            QuestJournalObjectiveEntry refreshed = findBoundReadObjective(entries, readQuestId, readPhaseId,
                    readObjective.objectiveId());
            if (refreshed != null) {
                readObjective = refreshed;
                return;
            }
            readObjective = null;
            readQuestId = "";
            readPhaseId = "";
        }

        for (QuestJournalEntry quest : entries) {
            for (QuestJournalPhaseEntry phase : quest.phases()) {
                if (!phase.current()) continue;
                for (QuestJournalObjectiveEntry objective : phase.objectives()) {
                    if (isPendingManualRead(objective) && matchingRequiredLesson(objective)) {
                        readObjective = objective;
                        readQuestId = quest.questId();
                        readPhaseId = phase.phaseId();
                        return;
                    }
                }
            }
        }
    }

    private QuestJournalObjectiveEntry findBoundReadObjective(List<QuestJournalEntry> entries, String questId,
                                                         String phaseId, String objectiveId) {
        for (QuestJournalEntry quest : entries) {
            if (!quest.questId().equals(questId)) continue;
            for (QuestJournalPhaseEntry phase : quest.phases()) {
                if (!phase.phaseId().equals(phaseId) || !phase.current()) continue;
                for (QuestJournalObjectiveEntry objective : phase.objectives()) {
                    if (objective.objectiveId().equals(objectiveId) && isPendingManualRead(objective)) return objective;
                }
            }
        }
        return null;
    }

    private static boolean isPendingManualRead(QuestJournalObjectiveEntry objective) {
        return objective != null && objective.objectiveType() == QuestObjectiveType.READ_MANUAL
                && objective.manualReadable() && !objective.completed()
                && !objective.manualRefs().isEmpty();
    }

    boolean requiresReadConfirmation() {
        String key = currentReadKey();
        return !key.isBlank() && !key.equals(readSubmittedKey) && isPendingManualRead(readObjective);
    }

    boolean canConfirmRead() {
        return requiresReadConfirmation() && matchingRequiredPage(readObjective);
    }

    void confirmRead() {
        if (!canConfirmRead()) return;
        ManualPageSpec page = currentPage();
        if (page == null) return;
        readSubmittedKey = currentReadKey();
        ModNetwork.CHANNEL.sendToServer(new CompleteManualReadObjectiveC2SPacket(
                readQuestId, readPhaseId, readObjective.objectiveId(),
                selectedManualId, selectedLessonId, page.pageId()));
    }


    private String currentReadKey() {
        return readObjective == null ? ""
                : readQuestId + "/" + readPhaseId + "/" + readObjective.objectiveId();
    }

    private int outlineRowCount() {
        int count = 0;
        for (ManualSpec manual : manuals) {
            count++;
            if (!collapsedGroups.contains(manual.manualId())) {
                for (ManualNodeSpec node : manual.sections()) count += countRows(manual.manualId(), node);
            }
        }
        return count;
    }

    private int countRows(String manualId, ManualNodeSpec node) {
        int count = 1;
        if (node.isGroup() && !collapsedGroups.contains(manualId + "/" + node.nodeId())) {
            for (ManualNodeSpec child : node.children()) count += countRows(manualId, child);
        }
        return count;
    }

    private ManualNodeSpec selectedLesson() {
        for (ManualSpec manual : manuals) if (manual.manualId().equals(selectedManualId)) {
            return manual.findLesson(selectedLessonId, "");
        }
        return null;
    }

    private ManualPageSpec currentPage() {
        ManualNodeSpec lesson = selectedLesson();
        return lesson == null || lesson.pages().isEmpty() ? null : lesson.pages().get(clamp(pageIndex, 0, lesson.pages().size() - 1));
    }

    private void loadImage() {
        ManualPageSpec page = currentPage();
        imageResult = page == null || page.image().isBlank() ? null : ContentTextureCache.load(page.image());
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    private record Layout(int x, int y, int w, int h, int split, int top, int bottom) {}
    private record OutlineHit(int x, int y, int w, int h, String manualId, String nodeId, boolean group) {
        boolean contains(double mx, double my) { return inside(mx, my, x, y, w, h); }
    }
}
