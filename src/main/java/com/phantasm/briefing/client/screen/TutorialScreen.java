package com.phantasm.briefing.client.screen;

import com.phantasm.briefing.client.ui.DarkFantasyTheme;

import com.phantasm.briefing.client.TutorialTextureCache;
import com.phantasm.briefing.client.widgets.HoverButtonWidget;
import com.phantasm.briefing.data.QuestJournalObjectiveEntry;
import com.phantasm.briefing.data.TutorialPageSpec;
import com.phantasm.briefing.network.ModNetwork;
import com.phantasm.briefing.network.packet.CompleteTutorialObjectiveC2SPacket;
import com.phantasm.briefing.network.packet.MarkTutorialViewedC2SPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class TutorialScreen extends PixelGridScreen {
    private static final int BUTTON_HEIGHT = 20;

    private final QuestJournalScreen journal;
    private final String questId;
    private final String phaseId;
    private final QuestJournalObjectiveEntry objective;
    private int pageIndex;
    private int textScroll;
    private TutorialTextureCache.LoadResult imageResult;
    private HoverButtonWidget previousButton;
    private HoverButtonWidget nextButton;
    private HoverButtonWidget completeButton;

    public TutorialScreen(
            QuestJournalScreen journal,
            String questId,
            String phaseId,
            QuestJournalObjectiveEntry objective
    ) {
        super(Component.translatable("screen.phantasmbriefing.tutorial"));
        this.journal = journal;
        this.questId = questId;
        this.phaseId = phaseId;
        this.objective = objective;
    }

    @Override
    protected void init() {
        super.init();
        loadCurrentImage();
        updateButtons();
        ModNetwork.CHANNEL.sendToServer(new MarkTutorialViewedC2SPacket(
                this.questId,
                this.phaseId,
                this.objective.objectiveId()
        ));
    }

    @Override
    protected void buildWidgets() {
        Layout layout = layout();
        int buttonY = layout.panelY() + layout.panelHeight() - 28;
        this.addPixelWidget(new HoverButtonWidget(
                this.font,
                layout.panelX() + 12,
                buttonY,
                96,
                BUTTON_HEIGHT,
                Component.translatable("tutorial.phantasmbriefing.back"),
                DarkFantasyTheme.BUTTON,
                DarkFantasyTheme.BUTTON_HOVER,
                DarkFantasyTheme.BUTTON_BORDER,
                DarkFantasyTheme.BUTTON_TEXT,
                DarkFantasyTheme.BUTTON_TEXT_HOVER,
                button -> returnToJournal()
        ));
        this.previousButton = this.addPixelWidget(new HoverButtonWidget(
                this.font,
                layout.panelX() + layout.panelWidth() - 294,
                buttonY,
                82,
                BUTTON_HEIGHT,
                Component.translatable("tutorial.phantasmbriefing.previous"),
                DarkFantasyTheme.BUTTON,
                DarkFantasyTheme.BUTTON_HOVER,
                DarkFantasyTheme.BUTTON_BORDER,
                DarkFantasyTheme.BUTTON_TEXT,
                DarkFantasyTheme.BUTTON_TEXT_HOVER,
                button -> changePage(-1)
        ));
        this.nextButton = this.addPixelWidget(new HoverButtonWidget(
                this.font,
                layout.panelX() + layout.panelWidth() - 204,
                buttonY,
                82,
                BUTTON_HEIGHT,
                Component.translatable("tutorial.phantasmbriefing.next"),
                DarkFantasyTheme.BUTTON,
                DarkFantasyTheme.BUTTON_HOVER,
                DarkFantasyTheme.BUTTON_BORDER,
                DarkFantasyTheme.BUTTON_TEXT,
                DarkFantasyTheme.BUTTON_TEXT_HOVER,
                button -> changePage(1)
        ));
        this.completeButton = this.addPixelWidget(new HoverButtonWidget(
                this.font,
                layout.panelX() + layout.panelWidth() - 114,
                buttonY,
                102,
                BUTTON_HEIGHT,
                Component.translatable("tutorial.phantasmbriefing.learned"),
                DarkFantasyTheme.BUTTON,
                DarkFantasyTheme.BUTTON_HOVER,
                DarkFantasyTheme.AMBER,
                DarkFantasyTheme.GOLD,
                DarkFantasyTheme.BUTTON_TEXT_HOVER,
                button -> completeTutorial()
        ));
    }

    @Override
    protected void renderScreenContents(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Layout layout = layout();
        guiGraphics.fill(
                layout.panelX(),
                layout.panelY(),
                layout.panelX() + layout.panelWidth(),
                layout.panelY() + layout.panelHeight(),
                DarkFantasyTheme.BACKGROUND
        );
        guiGraphics.fill(
                layout.panelX(),
                layout.panelY(),
                layout.panelX() + layout.panelWidth(),
                layout.panelY() + 1,
                DarkFantasyTheme.BRASS
        );
        guiGraphics.drawString(
                this.font,
                Component.literal(this.objective.title()).withStyle(ChatFormatting.BOLD),
                layout.panelX() + 14,
                layout.panelY() + 12,
                DarkFantasyTheme.TEXT_TITLE,
                true
        );
        guiGraphics.drawString(
                this.font,
                Component.translatable(
                        "tutorial.phantasmbriefing.page_counter",
                        this.pageIndex + 1,
                        pages().size()
                ),
                layout.panelX() + layout.panelWidth() - 78,
                layout.panelY() + 12,
                DarkFantasyTheme.TEXT_SECONDARY,
                false
        );
        guiGraphics.fill(
                layout.panelX() + 12,
                layout.panelY() + 31,
                layout.panelX() + layout.panelWidth() - 12,
                layout.panelY() + 32,
                DarkFantasyTheme.EDGE_FAINT
        );
        renderPage(guiGraphics, layout);
    }

    private void renderPage(GuiGraphics guiGraphics, Layout layout) {
        TutorialPageSpec page = currentPage();
        ContentLayout content = contentLayout(layout, page);
        if (!page.image().isBlank()) {
            renderImage(guiGraphics, content.imageX(), content.imageY(), content.imageWidth(), content.imageHeight());
        }
        if (!page.title().isBlank() || !page.text().isBlank()) {
            renderText(guiGraphics, page, content.textX(), content.textY(), content.textWidth(), content.textHeight());
        }
    }

    private void renderImage(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        guiGraphics.fill(x, y, x + width, y + height, DarkFantasyTheme.BACKGROUND);
        if (this.imageResult != null && this.imageResult.texture() != null) {
            TutorialTextureCache.TextureEntry texture = this.imageResult.texture();
            double scale = Math.min((double) width / texture.width(), (double) height / texture.height());
            int drawWidth = Math.max(1, (int) Math.round(texture.width() * scale));
            int drawHeight = Math.max(1, (int) Math.round(texture.height() * scale));
            int drawX = x + (width - drawWidth) / 2;
            int drawY = y + (height - drawHeight) / 2;
            guiGraphics.blit(
                    texture.location(),
                    drawX,
                    drawY,
                    drawWidth,
                    drawHeight,
                    0.0F,
                    0.0F,
                    texture.width(),
                    texture.height(),
                    texture.width(),
                    texture.height()
            );
            return;
        }
        Component error = imageErrorComponent();
        guiGraphics.drawCenteredString(this.font, error, x + width / 2, y + height / 2 - 4, DarkFantasyTheme.TEXT_MUTED);
    }

    private void renderText(
            GuiGraphics guiGraphics,
            TutorialPageSpec page,
            int x,
            int y,
            int width,
            int height
    ) {
        guiGraphics.fill(x, y, x + width, y + height, DarkFantasyTheme.MANUAL_TEXT_PANEL);
        int drawY = y + 8 - this.textScroll;
        guiGraphics.enableScissor(x, y, x + width, y + height);
        if (!page.title().isBlank()) {
            for (var line : this.font.split(Component.literal(page.title()).withStyle(ChatFormatting.BOLD), width - 16)) {
                guiGraphics.drawString(this.font, line, x + 8, drawY, DarkFantasyTheme.TEXT_TITLE, true);
                drawY += this.font.lineHeight + 2;
            }
            drawY += 5;
        }
        if (!page.text().isBlank()) {
            for (var line : this.font.split(Component.literal(page.text()), width - 16)) {
                guiGraphics.drawString(this.font, line, x + 8, drawY, DarkFantasyTheme.TEXT_SECONDARY, false);
                drawY += this.font.lineHeight + 2;
            }
        }
        guiGraphics.disableScissor();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta == 0.0D) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        Layout layout = layout();
        TutorialPageSpec page = currentPage();
        ContentLayout content = contentLayout(layout, page);
        if (mouseX >= content.textX() && mouseX < content.textX() + content.textWidth()
                && mouseY >= content.textY() && mouseY < content.textY() + content.textHeight()
                && maxTextScroll(page, content.textWidth(), content.textHeight()) > 0) {
            int amount = delta > 0.0D ? -18 : 18;
            this.textScroll = clamp(
                    this.textScroll + amount,
                    0,
                    maxTextScroll(page, content.textWidth(), content.textHeight())
            );
            return true;
        }
        changePage(delta > 0.0D ? -1 : 1);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            changePage(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            changePage(1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            returnToJournal();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        returnToJournal();
    }

    private void changePage(int direction) {
        int next = clamp(this.pageIndex + direction, 0, pages().size() - 1);
        if (next == this.pageIndex) {
            return;
        }
        this.pageIndex = next;
        this.textScroll = 0;
        loadCurrentImage();
        updateButtons();
    }

    private void completeTutorial() {
        if (this.completeButton != null) {
            this.completeButton.setActive(false);
        }
        ModNetwork.CHANNEL.sendToServer(new CompleteTutorialObjectiveC2SPacket(
                this.questId,
                this.phaseId,
                this.objective.objectiveId()
        ));
    }

    private void returnToJournal() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.journal);
        }
    }

    private void loadCurrentImage() {
        TutorialPageSpec page = currentPage();
        this.imageResult = page.image().isBlank() ? null : TutorialTextureCache.load(page.image());
    }

    private void updateButtons() {
        if (this.previousButton != null) {
            this.previousButton.setVisible(this.pageIndex > 0);
        }
        if (this.nextButton != null) {
            this.nextButton.setVisible(this.pageIndex < pages().size() - 1);
        }
        if (this.completeButton != null) {
            this.completeButton.setVisible(this.pageIndex == pages().size() - 1 && !this.objective.completed());
        }
    }

    private Component imageErrorComponent() {
        if (this.imageResult == null) {
            return Component.empty();
        }
        return switch (this.imageResult.failure()) {
            case MISSING -> Component.translatable("tutorial.phantasmbriefing.image_missing");
            case FILE_TOO_LARGE -> Component.translatable("tutorial.phantasmbriefing.image_too_large");
            case DIMENSIONS_TOO_LARGE -> Component.translatable("tutorial.phantasmbriefing.image_dimensions_too_large");
            case INVALID_PATH, CORRUPT -> Component.translatable("tutorial.phantasmbriefing.image_invalid");
            case NONE -> Component.empty();
        };
    }

    private int maxTextScroll(TutorialPageSpec page, int width, int height) {
        int contentHeight = 16;
        if (!page.title().isBlank()) {
            contentHeight += this.font.split(Component.literal(page.title()), width - 16).size()
                    * (this.font.lineHeight + 2) + 5;
        }
        if (!page.text().isBlank()) {
            contentHeight += this.font.split(Component.literal(page.text()), width - 16).size()
                    * (this.font.lineHeight + 2);
        }
        return Math.max(0, contentHeight - height);
    }

    private List<TutorialPageSpec> pages() {
        return this.objective.tutorialPages();
    }

    private TutorialPageSpec currentPage() {
        return pages().get(clamp(this.pageIndex, 0, pages().size() - 1));
    }

    private Layout layout() {
        int panelWidth = Math.min(760, Math.max(420, this.width - 24));
        int panelHeight = Math.min(430, Math.max(260, this.height - 24));
        int panelX = snap((this.width - panelWidth) / 2.0D);
        int panelY = snap((this.height - panelHeight) / 2.0D);
        return new Layout(panelX, panelY, panelWidth, panelHeight);
    }

    private ContentLayout contentLayout(Layout layout, TutorialPageSpec page) {
        int x = layout.panelX() + 14;
        int y = layout.panelY() + 40;
        int width = layout.panelWidth() - 28;
        int height = layout.panelHeight() - 76;
        boolean hasImage = !page.image().isBlank();
        boolean hasText = !page.title().isBlank() || !page.text().isBlank();
        if (hasImage && hasText && layout.panelWidth() >= 600) {
            int imageWidth = (width * 3) / 5;
            return new ContentLayout(
                    x,
                    y,
                    imageWidth,
                    height,
                    x + imageWidth + 10,
                    y,
                    width - imageWidth - 10,
                    height
            );
        }
        if (hasImage && hasText) {
            int imageHeight = (height * 3) / 5;
            return new ContentLayout(
                    x,
                    y,
                    width,
                    imageHeight,
                    x,
                    y + imageHeight + 8,
                    width,
                    height - imageHeight - 8
            );
        }
        if (hasImage) {
            return new ContentLayout(x, y, width, height, x, y, 0, 0);
        }
        return new ContentLayout(x, y, 0, 0, x, y, width, height);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Layout(int panelX, int panelY, int panelWidth, int panelHeight) {
    }

    private record ContentLayout(
            int imageX,
            int imageY,
            int imageWidth,
            int imageHeight,
            int textX,
            int textY,
            int textWidth,
            int textHeight
    ) {
    }
}
