package com.phantasm.briefing.client.screen;

import com.phantasm.briefing.client.ui.DarkFantasyTheme;

import com.phantasm.briefing.client.widgets.HoverButtonWidget;
import com.phantasm.briefing.client.ui.JournalOrnaments;
import com.phantasm.briefing.client.ui.PixelRenderUtil;
import com.phantasm.briefing.data.QuestJournalEntry;
import com.phantasm.briefing.data.ManualSpec;
import com.phantasm.briefing.data.ManualAutoOpenTarget;
import com.phantasm.briefing.data.ManualNodeSpec;
import com.phantasm.briefing.data.ManualReferenceSpec;
import com.phantasm.briefing.data.QuestJournalObjectiveEntry;
import com.phantasm.briefing.data.QuestJournalPhaseEntry;
import com.phantasm.briefing.data.QuestObjectiveType;
import com.phantasm.briefing.data.QuestRuntimeStatus;
import com.phantasm.briefing.network.ModNetwork;
import com.phantasm.briefing.network.packet.AcceptQuestC2SPacket;
import com.phantasm.briefing.network.packet.SetTrackedQuestC2SPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class QuestJournalScreen extends PixelGridScreen {
    private static final int LIST_ROW_HEIGHT = 27;
    private static final int SECTION_HEADER_HEIGHT = 26;
    private static final ResourceLocation QUEST_MAGIC_CIRCLE = new ResourceLocation(
            "phantasmbriefing", "textures/gui/quest_magic_circle.png");
    private static final int QUEST_MAGIC_CIRCLE_TEXTURE_WIDTH = 512;
    private static final int QUEST_MAGIC_CIRCLE_TEXTURE_HEIGHT = 512;

    private final List<QuestJournalEntry> entries;
    private final List<ManualSpec> manuals;
    private final TravelerManualPanel manualPanel;
    private final List<ManualHitbox> manualHitboxes = new ArrayList<>();
    private final List<QuestFocusHitbox> questFocusHitboxes = new ArrayList<>();
    private QuestActionHitbox questActionHitbox;
    private String selectedQuestId;
    private int listScroll;
    private int detailScroll;
    private String activeTabId = JournalTabRegistry.QUESTS;
    private final Map<String, JournalTabPage> extensionPages = new HashMap<>();
    private final List<TabBounds> tabBounds = new ArrayList<>();
    private HoverButtonWidget settingsButton;
    private HoverButtonWidget closeButton;
    private HoverButtonWidget backButton;
    private HoverButtonWidget previousButton;
    private HoverButtonWidget nextButton;
    private HoverButtonWidget learnedButton;

    public QuestJournalScreen(List<QuestJournalEntry> entries, List<ManualSpec> manuals) {
        super(Component.translatable("screen.phantasmbriefing.quest_journal"));
        this.entries = List.copyOf(entries);
        this.manuals = List.copyOf(manuals);
        this.manualPanel = new TravelerManualPanel(this.manuals);
        this.selectedQuestId = initialSelection(this.entries);
    }

    /** Keep both tab locations when the server pushes an updated quest snapshot. */
    public void restoreStateFrom(QuestJournalScreen previous) {
        if (JournalTabRegistry.contains(previous.activeTabId)) {
            this.activeTabId = previous.activeTabId;
        }
        if (this.entries.stream().anyMatch(entry -> entry.questId().equals(previous.selectedQuestId))) {
            this.selectedQuestId = previous.selectedQuestId;
        }
        this.listScroll = Math.max(0, previous.listScroll);
        this.detailScroll = Math.max(0, previous.detailScroll);
        this.manualPanel.restorePositionFrom(previous.manualPanel);
    }

    /** Apply a server-authorized one-shot handbook navigation after state restoration. */
    public void openManualAutomatically(ManualAutoOpenTarget target) {
        if (target == null) return;
        for (QuestJournalEntry quest : entries) {
            if (!quest.questId().equals(target.questId())) continue;
            for (QuestJournalPhaseEntry phase : quest.phases()) {
                if (!phase.phaseId().equals(target.phaseId())) continue;
                for (QuestJournalObjectiveEntry objective : phase.objectives()) {
                    if (!objective.objectiveId().equals(target.objectiveId())) continue;
                    ManualReferenceSpec ref = objective.manualRefs().stream()
                            .filter(candidate -> candidate.manualId().equals(target.manualId()))
                            .findFirst().orElse(null);
                    if (ref == null) return;
                    this.selectedQuestId = quest.questId();
                    this.manualPanel.openReference(target.manualId(), target.lessonId(), target.pageId(),
                            quest.questId(), phase.phaseId(), objective);
                    this.activeTabId = JournalTabRegistry.MANUAL;
                    return;
                }
            }
        }
    }

    @Override
    protected void buildWidgets() {
        Font font = this.font;
        Layout l = layout();
        this.tabBounds.clear();
        List<JournalTabRegistry.TabDefinition> tabs = JournalTabRegistry.tabs();
        int tabGap = 7;
        int tabCount = Math.max(1, tabs.size());
        int availableTabWidth = Math.max(24, l.panelWidth() - 34 - tabGap * (tabCount - 1));
        int tabWidth = Math.min(148, Math.max(24, availableTabWidth / tabCount));
        int tabX = l.panelX() + 17;
        for (JournalTabRegistry.TabDefinition tab : tabs) {
            int x = tabX;
            this.addPixelWidget(button(font, x, l.panelY() + 10, tabWidth, 22,
                    Component.translatable(tab.titleTranslationKey()), b -> switchTab(tab.id())));
            this.tabBounds.add(new TabBounds(tab.id(), x, tabWidth, tab.accent()));
            tabX += tabWidth + tabGap;
        }

        int bottom = l.panelY() + l.panelHeight() - 29;
        // Handbook navigation occupies three fixed slots on the lower-right: previous, action, next.
        // The middle action slot is shared by Back and I Learned It so those controls never overlap.
        boolean compact = l.panelWidth() < 330;
        boolean narrow = l.panelWidth() < 450;
        int buttonWidth = compact ? 46 : narrow ? 54 : 74;
        int gap = compact ? 4 : narrow ? 6 : 8;
        int rightX = l.panelX() + l.panelWidth() - buttonWidth - 16;
        int actionX = rightX - buttonWidth - gap;
        int previousX = actionX - buttonWidth - gap;
        this.settingsButton = this.addPixelWidget(button(font, rightX - buttonWidth - 8, bottom, buttonWidth, 20,
                Component.translatable("gui.phantasmbriefing.settings"), b -> {
                    if (this.minecraft != null) this.minecraft.setScreen(new PhantasmConfigScreen(this));
                }));
        this.closeButton = this.addPixelWidget(button(font, rightX, bottom, buttonWidth, 20,
                Component.translatable("gui.phantasmbriefing.close"), b -> this.onClose()));
        this.backButton = this.addPixelWidget(button(font, actionX, bottom, buttonWidth, 20,
                Component.translatable("manual.phantasmbriefing.back"), b -> switchTab(JournalTabRegistry.QUESTS)));
        this.previousButton = this.addPixelWidget(button(font, previousX, bottom, buttonWidth, 20,
                Component.translatable("manual.phantasmbriefing.previous"), b -> {
                    manualPanel.movePage(-1); playUiClick(); updateViewButtons();
                }));
        this.nextButton = this.addPixelWidget(button(font, rightX, bottom, buttonWidth, 20,
                Component.translatable("manual.phantasmbriefing.next"), b -> {
                    manualPanel.movePage(1); playUiClick(); updateViewButtons();
                }));
        this.learnedButton = this.addPixelWidget(button(font, actionX, bottom, buttonWidth, 20,
                Component.translatable("manual.phantasmbriefing.learned"), b -> {
                    if (manualPanel.canConfirmRead()) {
                        manualPanel.confirmRead(); playUiClick(); updateViewButtons();
                    }
                }));
        this.listScroll = clamp(this.listScroll, 0, maxListScroll(l));
        this.detailScroll = clamp(this.detailScroll, 0, maxDetailScroll(l));
        updateViewButtons();
    }

    private HoverButtonWidget button(Font font, int x, int y, int width, int height,
                                     Component label, HoverButtonWidget.PressAction action) {
        return new HoverButtonWidget(font, x, y, width, height, label,
                DarkFantasyTheme.BUTTON, DarkFantasyTheme.BUTTON_HOVER, DarkFantasyTheme.BUTTON_BORDER,
                DarkFantasyTheme.BUTTON_TEXT, DarkFantasyTheme.BUTTON_TEXT_HOVER, action);
    }

    private void switchTab(String targetId) {
        if (!JournalTabRegistry.contains(targetId) || targetId.equals(this.activeTabId)) return;
        JournalTabPage previous = extensionPage(this.activeTabId);
        if (previous != null) previous.onDeselected();
        this.activeTabId = targetId;
        JournalTabPage next = extensionPage(this.activeTabId);
        if (next != null) next.onSelected();
        updateViewButtons();
        playUiClick();
    }

    private JournalTabPage extensionPage(String tabId) {
        if (JournalTabRegistry.QUESTS.equals(tabId) || JournalTabRegistry.MANUAL.equals(tabId)) return null;
        return this.extensionPages.computeIfAbsent(tabId, JournalTabRegistry::createPage);
    }

    private void playUiClick() {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private void updateViewButtons() {
        boolean journal = JournalTabRegistry.QUESTS.equals(this.activeTabId);
        boolean manual = JournalTabRegistry.MANUAL.equals(this.activeTabId);
        boolean learned = manual && manualPanel.canConfirmRead();
        boolean awaitingReadConfirmation = manual && manualPanel.requiresReadConfirmation();
        if (settingsButton != null) settingsButton.setVisible(journal);
        if (closeButton != null) closeButton.setVisible(!manual);
        if (backButton != null) backButton.setVisible(manual && !awaitingReadConfirmation);
        if (previousButton != null) previousButton.setVisible(manual && manualPanel.hasPreviousPage());
        if (nextButton != null) nextButton.setVisible(manual && manualPanel.hasNextPage());
        if (learnedButton != null) learnedButton.setVisible(learned);
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        Layout l = layout();
        for (TabBounds tab : this.tabBounds) {
            if (!tab.id().equals(this.activeTabId)) continue;
            graphics.fill(tab.x() + 2, l.panelY() + 30, tab.x() + tab.width() - 2, l.panelY() + 32, tab.accent());
            if (JournalTabRegistry.MANUAL.equals(tab.id())) {
                JournalOrnaments.bookIcon(graphics, tab.x() + 8, l.panelY() + 15, JournalOrnaments.ACCENT_GOLD);
            }
            break;
        }
    }

    @Override
    protected void renderScreenContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Layout l = layout();
        renderPanelBackground(graphics, l);
        if (JournalTabRegistry.MANUAL.equals(this.activeTabId)) {
            manualPanel.bindCurrentReadObjective(entries);
            manualPanel.render(graphics, this.font, l.panelX(), l.panelY(), l.panelWidth(), l.panelHeight(),
                    l.contentY(), l.contentBottom(), mouseX, mouseY);
            updateViewButtons();
        } else if (JournalTabRegistry.QUESTS.equals(this.activeTabId)) {
            renderLeftList(graphics, l, mouseX, mouseY);
            renderDetails(graphics, l, mouseX, mouseY);
        } else {
            JournalTabPage page = extensionPage(this.activeTabId);
            if (page != null) {
                int x = l.panelX() + 12;
                int y = l.contentY();
                page.render(graphics, this.font, x, y, Math.max(1, l.panelWidth() - 24),
                        Math.max(1, l.contentBottom() - y), mouseX, mouseY, partialTick);
            }
        }
    }

    private void renderPanelBackground(GuiGraphics graphics, Layout l) {
        JournalOrnaments.frame(graphics, l.panelX(), l.panelY(), l.panelWidth(), l.panelHeight());
    }

    private void renderLeftList(GuiGraphics graphics, Layout l, int mouseX, int mouseY) {
        this.questFocusHitboxes.clear();
        int x = l.listX(), top = l.contentY(), width = l.listWidth(), bottom = l.contentBottom();
        JournalOrnaments.panel(graphics, x - 5, top, width + 4, bottom - top);
        JournalOrnaments.divider(graphics, l.dividerX(), top + 4, bottom - 4);
        renderQuestMagicCircle(graphics, l);
        PixelRenderUtil.withClip(graphics, x, top + 2, x + width, bottom - 2, () -> {
            int y = top + 3 - listScroll;
            y = renderSection(graphics, this.font, x, y, width, mouseX, mouseY,
                    Component.translatable("journal.in_progress"), Component.translatable("journal.no_active"),
                    activeEntries(), JournalOrnaments.StatusTone.ACTIVE);
            y += 6;
            y = renderSection(graphics, this.font, x, y, width, mouseX, mouseY,
                    Component.translatable("journal.available"), Component.translatable("journal.no_available"),
                    availableEntries(), JournalOrnaments.StatusTone.AVAILABLE);
            y += 6;
            renderSection(graphics, this.font, x, y, width, mouseX, mouseY,
                    Component.translatable("journal.completed"), Component.translatable("journal.no_completed"),
                    completedEntries(), JournalOrnaments.StatusTone.COMPLETED);
        });
    }

    private void renderQuestMagicCircle(GuiGraphics graphics, Layout l) {
        int clipLeft = l.panelX() + 4;
        int clipTop = l.contentY() + 1;
        int clipRight = l.dividerX() - 1;
        int clipBottom = l.contentBottom() - 1;
        int drawWidth = Math.max(236, Math.min(296, l.listWidth() + 78));
        int drawHeight = Math.max(1, Math.round(drawWidth * (QUEST_MAGIC_CIRCLE_TEXTURE_HEIGHT
                / (float) QUEST_MAGIC_CIRCLE_TEXTURE_WIDTH)));

        // The replacement seal is denser and perfectly square, so keep it slightly smaller and
        // sink more of the circle below the lower-left edge. Only the upper-right arc should remain visible.
        int drawX = l.listX() + 8 - drawWidth / 2;
        int drawY = l.contentBottom() - 4 - Math.round(drawHeight * 0.74F);
        PixelRenderUtil.withClip(graphics, clipLeft, clipTop, clipRight, clipBottom,
                () -> PixelRenderUtil.drawAdditiveOverlay(graphics, QUEST_MAGIC_CIRCLE,
                        drawX, drawY, drawWidth, drawHeight,
                        QUEST_MAGIC_CIRCLE_TEXTURE_WIDTH, QUEST_MAGIC_CIRCLE_TEXTURE_HEIGHT,
                        DarkFantasyTheme.MAGIC_CIRCLE_OVERLAY));
    }

    private int renderSection(GuiGraphics g, Font font, int x, int y, int width,
                              int mouseX, int mouseY, Component label, Component emptyLabel,
                              List<QuestJournalEntry> sectionEntries, JournalOrnaments.StatusTone sectionTone) {
        JournalOrnaments.statusHeader(g, x, y, width - 2, 19, sectionTone);
        g.drawString(font, label, x + 12, y + 5, JournalOrnaments.statusText(sectionTone),
                sectionTone == JournalOrnaments.StatusTone.ACTIVE);
        y += SECTION_HEADER_HEIGHT;
        if (sectionEntries.isEmpty()) {
            g.drawString(font, emptyLabel, x + 5, y + 3, DarkFantasyTheme.TEXT_MUTED, false);
            return y + font.lineHeight + 10;
        }
        for (QuestJournalEntry entry : sectionEntries) {
            boolean selected = entry.questId().equals(selectedQuestId);
            boolean hovered = mouseX >= x && mouseX < x + width - 3
                    && mouseY >= y && mouseY < y + LIST_ROW_HEIGHT;
            boolean focusable = entry.status() == QuestRuntimeStatus.ACTIVE
                    || entry.status() == QuestRuntimeStatus.READY_TO_TURN_IN;
            JournalOrnaments.StatusTone entryTone = statusTone(entry.status());
            JournalOrnaments.statusCard(g, x, y, width - 3, LIST_ROW_HEIGHT - 2,
                    entryTone, selected, hovered, !focusable);
            int titleReserve = focusable ? 38 : 26;
            Component title = Component.literal(font.plainSubstrByWidth(entry.title(), Math.max(15, width - titleReserve)));
            int textColor = JournalOrnaments.statusText(entryTone);
            g.drawString(font, title, x + 10, y + 4, textColor,
                    entryTone == JournalOrnaments.StatusTone.ACTIVE);
            g.drawString(font, statusLine(entry), x + 10, y + 5 + font.lineHeight, textColor, false);
            if (focusable) {
                int focusX = x + width - 14;
                int focusY = y + (LIST_ROW_HEIGHT - 2) / 2;
                boolean focusHovered = mouseX >= focusX - 10 && mouseX < focusX + 11
                        && mouseY >= focusY - 10 && mouseY < focusY + 11;
                JournalOrnaments.focusDiamond(g, focusX, focusY, entry.tracked(), focusHovered);
                this.questFocusHitboxes.add(new QuestFocusHitbox(
                        focusX - 11, focusY - 11, 22, 22, entry.questId(), entry.tracked()));
            }
            y += LIST_ROW_HEIGHT;
        }
        return y + 4;
    }

    private void renderDetails(GuiGraphics graphics, Layout l, int mouseX, int mouseY) {
        int x = l.detailX(), top = l.contentY(), width = l.detailWidth(), bottom = l.contentBottom();
                this.manualHitboxes.clear();
        this.questActionHitbox = null;
        JournalOrnaments.panel(graphics, x - 5, top, width + 10, bottom - top);
        PixelRenderUtil.withClip(graphics, x, top + 2, x + width, bottom - 2,
                () -> renderDetailContent(graphics, l, mouseX, mouseY));
    }

    private void renderDetailContent(GuiGraphics graphics, Layout l, int mouseX, int mouseY) {
        Font font = this.font;
        int x = l.detailX(), width = l.detailWidth();
        int y = l.contentY() + 3 - this.detailScroll;
        JournalOrnaments.headerPlaque(graphics, x, y, width, 21, JournalOrnaments.ACCENT_GOLD);
        graphics.drawString(font, Component.translatable("journal.details").withStyle(ChatFormatting.BOLD),
                x + 12, y + 5, DarkFantasyTheme.TEXT_TITLE, false);
        y += 27;
        QuestJournalEntry selected = selectedEntry();
        if (selected == null) {
            graphics.drawString(font, Component.translatable("journal.select_task"), x + 8, y + 6, DarkFantasyTheme.TEXT_SECONDARY, false);
            return;
        }
        JournalOrnaments.StatusTone selectedTone = statusTone(selected.status());
        JournalOrnaments.statusHeader(graphics, x, y, width, 24, selectedTone);
        graphics.drawString(font, Component.literal(font.plainSubstrByWidth(selected.title(), Math.max(20, width - 28)))
                .withStyle(ChatFormatting.BOLD), x + 13, y + 8, JournalOrnaments.statusText(selectedTone), true);
        y += 29;
        Component statusLine = statusLine(selected);
        graphics.fill(x + 3, y, x + Math.min(width - 4, font.width(statusLine) + 20), y + 15,
                selectedTone == JournalOrnaments.StatusTone.COMPLETED ? 0x78161A21 : 0xA91B1E24);
        JournalOrnaments.diamond(graphics, x + 8, y + 7, 3, JournalOrnaments.statusAccent(selectedTone));
        graphics.drawString(font, statusLine, x + 15, y + 3, JournalOrnaments.statusText(selectedTone), false);
        y += 20;

        QuestActionType actionType = questActionType(selected);
        if (actionType != null) {
            Component actionLabel = questActionLabel(actionType);
            int actionWidth = Math.min(Math.max(76, font.width(actionLabel) + 20), Math.max(76, width - 14));
            int actionX = x + 7;
            int actionHeight = 20;
            boolean hovered = mouseX >= actionX && mouseX < actionX + actionWidth
                    && mouseY >= y && mouseY < y + actionHeight;
            JournalOrnaments.questCard(graphics, actionX, y, actionWidth, actionHeight, false, hovered, false);
            graphics.drawString(font, actionLabel, actionX + Math.max(6, (actionWidth - font.width(actionLabel)) / 2),
                    y + (actionHeight - font.lineHeight) / 2, DarkFantasyTheme.TEXT_SECONDARY, false);
            this.questActionHitbox = new QuestActionHitbox(actionX, y, actionWidth, actionHeight, selected.questId(), actionType);
            y += 26;
        }
        if (!selected.description().isBlank()) {
            for (var line : font.split(Component.literal(selected.description()), Math.max(18, width - 14))) {
                graphics.drawString(font, line, x + 7, y, DarkFantasyTheme.TEXT_SECONDARY, false);
                y += font.lineHeight + 2;
            }
            y += 8;
        }
        y = renderManualLinks(graphics, selected.manualRefs(), x + 8, y, width - 16,
                selected.questId(), "", null);

        int phaseIndex = 1;
        for (QuestJournalPhaseEntry phase : selected.phases()) {
            int phaseStart = y;
            boolean inactive = !phase.completed() && !phase.current();
            JournalOrnaments.StatusTone phaseTone = phase.current() ? JournalOrnaments.StatusTone.ACTIVE
                    : phase.completed() ? JournalOrnaments.StatusTone.COMPLETED : JournalOrnaments.StatusTone.INACTIVE;
            JournalOrnaments.statusHeader(graphics, x + 17, y, Math.max(15, width - 19), 22, phaseTone);
            JournalOrnaments.phaseNode(graphics, x + 9, y + 11, phase.completed(), phase.current());
            Component phaseTitle = Component.literal(phaseIndex + ". " + phase.title());
            graphics.drawString(font, phaseTitle, x + 27, y + 6, JournalOrnaments.statusText(phaseTone), phase.current());
            y += 27;
            if (phase.objectives().isEmpty()) {
                graphics.drawString(font, Component.translatable("journal.no_objectives"), x + 27, y,
                        inactive ? JournalOrnaments.TEXT_MUTED : DarkFantasyTheme.TEXT_SECONDARY, false);
                y += font.lineHeight + 4;
            } else {
                for (QuestJournalObjectiveEntry objective : phase.objectives()) {
                    if (objective.objectiveType() == QuestObjectiveType.READ_MANUAL && !objective.manualRefs().isEmpty()) {
                        int rowHeight = font.lineHeight * 2 + 12;
                        int rowTop = y - 2;
                        boolean hovered = objective.manualReadable()
                                && mouseX >= x + 18 && mouseX < x + width - 4
                                && mouseY >= rowTop && mouseY < rowTop + rowHeight;
                        boolean objectiveInactive = inactive || (!objective.completed() && !objective.active());
                        JournalOrnaments.StatusTone objectiveTone = objective.completed() ? JournalOrnaments.StatusTone.COMPLETED
                                : objectiveInactive ? JournalOrnaments.StatusTone.INACTIVE : JournalOrnaments.StatusTone.ACTIVE;
                        JournalOrnaments.statusCard(graphics, x + 18, rowTop, width - 23, rowHeight, objectiveTone,
                                objective.active() && objective.manualReadable() && !objective.completed(), hovered, false);
                        if (objective.manualReadable()) {
                            ManualReferenceSpec primaryRef = objective.manualRefs().get(0);
                            manualHitboxes.add(new ManualHitbox(x + 18, rowTop, width - 23, rowHeight, primaryRef,
                                    selected.questId(), phase.phaseId(), objective));
                        }
                        JournalOrnaments.diamond(graphics, x + 26, y + 5, 2,
                                JournalOrnaments.statusAccent(objectiveTone));
                        graphics.drawString(font, Component.literal(objective.title()), x + 34, y,
                                JournalOrnaments.statusText(objectiveTone), false);
                        graphics.drawString(font, Component.translatable(objective.manualReadable()
                                        ? "journal.phantasmbriefing.manual_read_open" : "journal.phantasmbriefing.manual_read_locked"),
                                x + 34, y + font.lineHeight + 3,
                                JournalOrnaments.statusText(objectiveTone), false);
                        y += rowHeight + 2;
                        y = renderManualLinks(graphics, objective.manualRefs(), x + 25, y, width - 31,
                                selected.questId(), phase.phaseId(), objective, objectiveInactive);
                        continue;
                    }
                    boolean objectiveInactive = inactive || (!objective.completed() && !objective.active());
                    JournalOrnaments.StatusTone objectiveTone = objective.completed() ? JournalOrnaments.StatusTone.COMPLETED
                            : objectiveInactive ? JournalOrnaments.StatusTone.INACTIVE : JournalOrnaments.StatusTone.ACTIVE;
                    int objectiveRowTop = y - 2;
                    int objectiveRowHeight = font.lineHeight + 7;
                    int objectiveTextY = objectiveRowTop + Math.max(0, (objectiveRowHeight - font.lineHeight) / 2);
                    JournalOrnaments.statusHeader(graphics, x + 20, objectiveRowTop, Math.max(14, width - 23),
                            objectiveRowHeight, objectiveTone);
                    JournalOrnaments.diamond(graphics, x + 26, objectiveRowTop + objectiveRowHeight / 2, 2,
                            JournalOrnaments.statusAccent(objectiveTone));
                    int titleWidth = Math.max(0, width - 48);
                    if (shouldShowProgress(objective)) {
                        Component count = Component.literal(objective.progress() + "/" + objective.requiredCount());
                        Component progress = width < 170 ? count
                                : Component.translatable("journal.objective_progress").append(count);
                        int progressWidth = Math.min(96, font.width(progress) + 10);
                        int progressX = x + width - 12 - progressWidth;
                        titleWidth = Math.max(0, progressX - x - 39);
                        graphics.fill(progressX, objectiveRowTop, progressX + progressWidth,
                                objectiveRowTop + objectiveRowHeight, objectiveInactive ? 0xD315181D : 0xCF191C22);
                        graphics.fill(progressX, objectiveRowTop, progressX + 2,
                                objectiveRowTop + objectiveRowHeight,
                                objectiveInactive ? JournalOrnaments.ACCENT_MUTED : JournalOrnaments.ACCENT_BRASS);
                        graphics.drawString(font, progress, progressX + 5, objectiveTextY,
                                JournalOrnaments.statusText(objectiveTone), false);
                    }
                    graphics.drawString(font, Component.literal(font.plainSubstrByWidth(objective.title(), titleWidth)),
                            x + 34, objectiveTextY, JournalOrnaments.statusText(objectiveTone), false);
                    y += font.lineHeight + 4;
                    y = renderManualLinks(graphics, objective.manualRefs(), x + 25, y, width - 31,
                            selected.questId(), phase.phaseId(), objective, objectiveInactive);
                }
            }
            if (y > phaseStart + 27) {
                graphics.fill(x + 9, phaseStart + 17, x + 10, y - 3,
                        phase.completed() ? 0x66708091 : inactive ? 0x44545E69 : 0x78B98D52);
                JournalOrnaments.phaseNode(graphics, x + 9, phaseStart + 11, phase.completed(), phase.current());
            }
            y += 9;
            phaseIndex++;
        }
    }

    private int renderManualLinks(GuiGraphics graphics, List<ManualReferenceSpec> refs,
                                  int x, int y, int width, String questId, String phaseId, QuestJournalObjectiveEntry objective) {
        return renderManualLinks(graphics, refs, x, y, width, questId, phaseId, objective, false);
    }

    private int renderManualLinks(GuiGraphics graphics, List<ManualReferenceSpec> refs,
                                  int x, int y, int width, String questId, String phaseId,
                                  QuestJournalObjectiveEntry objective, boolean inactive) {
        for (ManualReferenceSpec ref : refs) {
            ManualSpec manual = manuals.stream().filter(m -> m.manualId().equals(ref.manualId())).findFirst().orElse(null);
            if (manual == null) continue;
            ManualNodeSpec lesson = manual.findLesson(ref.lessonId(), ref.pageId());
            if (lesson == null) continue;
            JournalOrnaments.questCard(graphics, x, y, width, this.font.lineHeight + 10, false, false, false, inactive);
            JournalOrnaments.bookIcon(graphics, x + 7, y + 1,
                    inactive ? JournalOrnaments.ACCENT_MUTED : JournalOrnaments.ACCENT_GOLD);
            Component label = Component.translatable("journal.phantasmbriefing.open_manual", lesson.title());
            graphics.drawString(this.font, label, x + 25, y + 3,
                    inactive ? JournalOrnaments.TEXT_MUTED : DarkFantasyTheme.TEXT_SECONDARY, false);
            manualHitboxes.add(new ManualHitbox(x, y, width, this.font.lineHeight + 10, ref, questId, phaseId, objective));
            y += this.font.lineHeight + 12;
        }
        return y;
    }

    private boolean triggerQuestActionAt(double mouseX, double mouseY) {
        Layout l = layout();
        if (this.questActionHitbox == null
                || mouseX < l.detailX() || mouseX >= l.detailX() + l.detailWidth()
                || mouseY < l.contentY() || mouseY >= l.contentBottom()
                || !this.questActionHitbox.contains(mouseX, mouseY)) {
            return false;
        }
        QuestActionHitbox hitbox = this.questActionHitbox;
        ModNetwork.CHANNEL.sendToServer(new AcceptQuestC2SPacket(hitbox.questId()));
        playUiClick();
        return true;
    }

    private boolean openManualAt(double mouseX, double mouseY) {
        Layout l = layout();
        if (mouseX < l.detailX() || mouseX >= l.detailX() + l.detailWidth()
                || mouseY < l.contentY() || mouseY >= l.contentBottom()) return false;
        for (ManualHitbox hit : this.manualHitboxes) {
            if (hit.contains(mouseX, mouseY)) {
                ManualReferenceSpec ref = hit.ref();
                if (this.minecraft != null) {
                    manualPanel.openReference(ref.manualId(), ref.lessonId(), ref.pageId(),
                            hit.questId(), hit.phaseId(), hit.objective());
                    switchTab(JournalTabRegistry.MANUAL);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (JournalTabRegistry.MANUAL.equals(this.activeTabId)) {
            if (manualPanel.mouseClicked(mouseX, mouseY, button)) {
                playUiClick();
                updateViewButtons();
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (!JournalTabRegistry.QUESTS.equals(this.activeTabId)) {
            JournalTabPage page = extensionPage(this.activeTabId);
            if (page != null && page.mouseClicked(mouseX, mouseY, button)) return true;
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (button == 0 && focusQuestAt(mouseX, mouseY)) return true;
        if (button == 0 && triggerQuestActionAt(mouseX, mouseY)) return true;
        if (button == 0 && openManualAt(mouseX, mouseY)) return true;
        if (button == 0 && selectQuestAt(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta == 0.0D) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        if (JournalTabRegistry.MANUAL.equals(this.activeTabId)) {
            if (manualPanel.mouseScrolled(mouseX, mouseY, delta)) return true;
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        if (!JournalTabRegistry.QUESTS.equals(this.activeTabId)) {
            JournalTabPage page = extensionPage(this.activeTabId);
            if (page != null && page.mouseScrolled(mouseX, mouseY, delta)) return true;
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        Layout layout = layout();
        if (mouseY < layout.contentY() || mouseY >= layout.contentBottom()) return super.mouseScrolled(mouseX, mouseY, delta);
        int amount = delta > 0.0D ? -LIST_ROW_HEIGHT : LIST_ROW_HEIGHT;
        if (mouseX >= layout.listX() && mouseX < layout.dividerX()) {
            this.listScroll = clamp(this.listScroll + amount, 0, maxListScroll(layout));
            return true;
        }
        if (mouseX >= layout.detailX() && mouseX < layout.detailX() + layout.detailWidth()) {
            this.detailScroll = clamp(this.detailScroll + amount, 0, maxDetailScroll(layout));
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
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    private boolean focusQuestAt(double mouseX, double mouseY) {
        Layout layout = layout();
        if (mouseX < layout.listX() || mouseX >= layout.dividerX()
                || mouseY < layout.contentY() || mouseY >= layout.contentBottom()) {
            return false;
        }
        for (QuestFocusHitbox hitbox : this.questFocusHitboxes) {
            if (hitbox.contains(mouseX, mouseY)) {
                if (!hitbox.focused()) {
                    ModNetwork.CHANNEL.sendToServer(new SetTrackedQuestC2SPacket(hitbox.questId(), true));
                }
                this.selectedQuestId = hitbox.questId();
                this.detailScroll = 0;
                playUiClick();
                return true;
            }
        }
        return false;
    }

    private boolean selectQuestAt(double mouseX, double mouseY) {
        Layout layout = layout();
        if (mouseX < layout.listX() || mouseX >= layout.dividerX()
                || mouseY < layout.contentY() || mouseY >= layout.contentBottom()) {
            return false;
        }
        int y = layout.contentY() + 3 - this.listScroll;
        List<QuestJournalEntry> active = activeEntries();
        y += SECTION_HEADER_HEIGHT;
        if (active.isEmpty()) {
            y += this.font.lineHeight + 10;
        } else {
            for (QuestJournalEntry entry : active) {
                if (mouseY >= y && mouseY < y + LIST_ROW_HEIGHT) { select(entry); return true; }
                y += LIST_ROW_HEIGHT;
            }
            y += 4;
        }

        y += 6 + SECTION_HEADER_HEIGHT;
        List<QuestJournalEntry> available = availableEntries();
        if (available.isEmpty()) {
            y += this.font.lineHeight + 10;
        } else {
            for (QuestJournalEntry entry : available) {
                if (mouseY >= y && mouseY < y + LIST_ROW_HEIGHT) { select(entry); return true; }
                y += LIST_ROW_HEIGHT;
            }
            y += 4;
        }

        y += 6 + SECTION_HEADER_HEIGHT;
        List<QuestJournalEntry> completed = completedEntries();
        if (completed.isEmpty()) {
            return false;
        }
        for (QuestJournalEntry entry : completed) {
            if (mouseY >= y && mouseY < y + LIST_ROW_HEIGHT) { select(entry); return true; }
            y += LIST_ROW_HEIGHT;
        }
        return false;
    }

    private void select(QuestJournalEntry entry) {
        if (entry == null) {
            return;
        }
        this.selectedQuestId = entry.questId();
        this.detailScroll = 0;
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private List<QuestJournalEntry> activeEntries() {
        List<QuestJournalEntry> result = new ArrayList<>();
        for (QuestJournalEntry entry : this.entries) {
            if (entry.status() == QuestRuntimeStatus.ACTIVE || entry.status() == QuestRuntimeStatus.READY_TO_TURN_IN) {
                result.add(entry);
            }
        }
        return result;
    }

    private List<QuestJournalEntry> availableEntries() {
        List<QuestJournalEntry> result = new ArrayList<>();
        for (QuestJournalEntry entry : this.entries) {
            if (entry.status() == QuestRuntimeStatus.AVAILABLE) {
                result.add(entry);
            }
        }
        return result;
    }

    private List<QuestJournalEntry> completedEntries() {
        List<QuestJournalEntry> result = new ArrayList<>();
        for (QuestJournalEntry entry : this.entries) {
            if (entry.status() == QuestRuntimeStatus.COMPLETED) {
                result.add(entry);
            }
        }
        return result;
    }

    private QuestJournalEntry selectedEntry() {
        for (QuestJournalEntry entry : this.entries) {
            if (entry.questId().equals(this.selectedQuestId)) {
                return entry;
            }
        }
        return null;
    }

    private int maxListScroll(Layout layout) {
        int content = SECTION_HEADER_HEIGHT * 3
                + (activeEntries().size() + availableEntries().size() + completedEntries().size()) * LIST_ROW_HEIGHT
                + (activeEntries().isEmpty() ? this.font.lineHeight + 10 : 4)
                + (availableEntries().isEmpty() ? this.font.lineHeight + 10 : 4)
                + (completedEntries().isEmpty() ? this.font.lineHeight + 10 : 4) + 18;
        return Math.max(0, content - (layout.contentBottom() - layout.contentY() - 6));
    }

    private int maxDetailScroll(Layout layout) {
        QuestJournalEntry selected = selectedEntry();
        if (selected == null) return 0;
        int height = 27 + 29 + 20 + 18;
        if (questActionType(selected) != null) height += 26;
        if (!selected.description().isBlank()) {
            height += this.font.split(Component.literal(selected.description()), Math.max(18, layout.detailWidth() - 14)).size()
                    * (this.font.lineHeight + 2) + 8;
        }
        height += selected.manualRefs().size() * (this.font.lineHeight + 12);
        for (QuestJournalPhaseEntry phase : selected.phases()) {
            height += 27 + 9;
            if (phase.objectives().isEmpty()) height += this.font.lineHeight + 4;
            for (QuestJournalObjectiveEntry objective : phase.objectives()) {
                height += objective.manualRefs().size() * (this.font.lineHeight + 12);
                if (objective.objectiveType() == QuestObjectiveType.READ_MANUAL && !objective.manualRefs().isEmpty()) {
                    height += this.font.lineHeight * 2 + 14;
                } else {
                    height += this.font.lineHeight + 4;
                }
            }
        }
        return Math.max(0, height - (layout.contentBottom() - layout.contentY() - 8));
    }

    private Layout layout() {
        int panelWidth = Math.min(800, Math.max(240, this.width - 16));
        int panelHeight = Math.min(470, Math.max(180, this.height - 16));
        int panelX = snap((this.width - panelWidth) / 2.0D);
        int panelY = snap((this.height - panelHeight) / 2.0D);
        int listWidth = Math.max(95, Math.min(210, panelWidth / 3));
        int listX = panelX + 17;
        int dividerX = listX + listWidth;
        int detailX = dividerX + 14;
        int detailWidth = Math.max(28, panelX + panelWidth - 18 - detailX);
        int contentY = panelY + 45;
        int contentBottom = panelY + panelHeight - 38;
        return new Layout(panelX, panelY, panelWidth, panelHeight, listX, listWidth, dividerX,
                detailX, detailWidth, contentY, contentBottom);
    }

    private static Component statusComponent(QuestRuntimeStatus status) {
        return switch (status) {
            case AVAILABLE -> Component.translatable("journal.status_available");
            case READY_TO_TURN_IN -> Component.translatable("journal.status_ready");
            case COMPLETED -> Component.translatable("journal.status_completed");
            default -> Component.translatable("journal.status_active");
        };
    }

    private static JournalOrnaments.StatusTone statusTone(QuestRuntimeStatus status) {
        if (status == QuestRuntimeStatus.COMPLETED) return JournalOrnaments.StatusTone.COMPLETED;
        if (status == QuestRuntimeStatus.AVAILABLE) return JournalOrnaments.StatusTone.AVAILABLE;
        if (status == QuestRuntimeStatus.ACTIVE || status == QuestRuntimeStatus.READY_TO_TURN_IN) {
            return JournalOrnaments.StatusTone.ACTIVE;
        }
        return JournalOrnaments.StatusTone.INACTIVE;
    }

    private static Component statusLine(QuestJournalEntry entry) {
        Component base = statusComponent(entry.status());
        return entry.recommended()
                ? base.copy().append(Component.literal(" ")).append(Component.translatable("journal.recommended"))
                : base;
    }

    private static QuestActionType questActionType(QuestJournalEntry entry) {
        if (entry == null) return null;
        return entry.status() == QuestRuntimeStatus.AVAILABLE ? QuestActionType.START : null;
    }

    private static Component questActionLabel(QuestActionType actionType) {
        return Component.translatable("journal.start_quest");
    }

    private static boolean shouldShowProgress(QuestJournalObjectiveEntry objective) {
        return objective.objectiveType() == QuestObjectiveType.KILL
                || objective.objectiveType() == QuestObjectiveType.COLLECT
                || objective.objectiveType() == QuestObjectiveType.CRAFT
                || objective.objectiveType() == QuestObjectiveType.INTERACT;
    }

    private static String initialSelection(List<QuestJournalEntry> entries) {
        for (QuestJournalEntry entry : entries) {
            if (entry.tracked() && (entry.status() == QuestRuntimeStatus.ACTIVE
                    || entry.status() == QuestRuntimeStatus.READY_TO_TURN_IN)) {
                return entry.questId();
            }
        }
        for (QuestJournalEntry entry : entries) {
            if ((entry.status() == QuestRuntimeStatus.ACTIVE || entry.status() == QuestRuntimeStatus.READY_TO_TURN_IN)
                    && hasAccessibleManualRead(entry)) {
                return entry.questId();
            }
        }
        for (QuestJournalEntry entry : entries) {
            if (entry.status() == QuestRuntimeStatus.ACTIVE || entry.status() == QuestRuntimeStatus.READY_TO_TURN_IN) {
                return entry.questId();
            }
        }
        for (QuestJournalEntry entry : entries) {
            if (entry.status() == QuestRuntimeStatus.AVAILABLE && entry.recommended()) {
                return entry.questId();
            }
        }
        for (QuestJournalEntry entry : entries) {
            if (entry.status() == QuestRuntimeStatus.AVAILABLE) {
                return entry.questId();
            }
        }
        return entries.isEmpty() ? "" : entries.get(0).questId();
    }

    private static boolean hasAccessibleManualRead(QuestJournalEntry entry) {
        for (QuestJournalPhaseEntry phase : entry.phases()) {
            for (QuestJournalObjectiveEntry objective : phase.objectives()) {
                if (objective.objectiveType() == QuestObjectiveType.READ_MANUAL && objective.manualReadable()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum QuestActionType { START }

    private record QuestFocusHitbox(int x, int y, int width, int height, String questId, boolean focused) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= this.x && mouseX < this.x + this.width
                    && mouseY >= this.y && mouseY < this.y + this.height;
        }
    }

    private record QuestActionHitbox(int x, int y, int width, int height, String questId, QuestActionType action) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= this.x && mouseX < this.x + this.width
                    && mouseY >= this.y && mouseY < this.y + this.height;
        }
    }

    private record TabBounds(String id, int x, int width, int accent) {
    }

    private record Layout(
            int panelX,
            int panelY,
            int panelWidth,
            int panelHeight,
            int listX,
            int listWidth,
            int dividerX,
            int detailX,
            int detailWidth,
            int contentY,
            int contentBottom
    ) {
    }

    private record ManualHitbox(int x, int y, int width, int height, ManualReferenceSpec ref,
                                String questId, String phaseId, QuestJournalObjectiveEntry objective) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + width && my >= y && my < y + height;
        }
    }

}