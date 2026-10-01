package com.phantasm.briefing.client.screen;

import com.phantasm.briefing.client.ui.DarkFantasyTheme;

import com.phantasm.briefing.client.ClientDialogueSession;
import com.phantasm.briefing.data.DialogueOption;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class DialogueChoiceScreen extends Screen {
    private static final int DIALOGUE_BACKGROUND = DarkFantasyTheme.DIALOGUE_BACKGROUND;
    private static final int DIALOGUE_ACCENT = DarkFantasyTheme.DIALOGUE_ACCENT;
    private static final int DIALOGUE_TEXT = DarkFantasyTheme.DIALOGUE_TEXT;
    private static final int DIALOGUE_HOVER = DarkFantasyTheme.DIALOGUE_HOVER;

    private boolean choosing;

    public DialogueChoiceScreen() {
        super(Component.empty());
    }

    @Override
    protected void init() {
        ClientDialogueSession session = ClientDialogueSession.getInstance();
        if (!session.isAwaitingChoice() || session.getOptions().isEmpty()) {
            Minecraft.getInstance().setScreen(null);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ClientDialogueSession session = ClientDialogueSession.getInstance();
        List<DialogueOption> options = session.getOptions();
        if (!session.isAwaitingChoice() || options.isEmpty()) {
            return;
        }

        ChoiceLayout layout = choiceLayout(options);
        for (int i = 0; i < options.size(); i++) {
            DialogueOption option = options.get(i);
            int rowTop = layout.top() + i * layout.rowHeight();
            boolean hovered = isInside(mouseX, mouseY, layout.left(), rowTop, layout.width(), layout.rowHeight() - 3);
            drawChoiceBubble(graphics, layout.left(), rowTop, layout.width(), layout.rowHeight() - 3, hovered);
            String label = this.font.plainSubstrByWidth(option.label(), layout.width() - 30);
            graphics.drawString(
                    this.font,
                    label,
                    layout.left() + 16,
                    rowTop + 4,
                    hovered ? DarkFantasyTheme.BUTTON_TEXT_HOVER : DIALOGUE_TEXT,
                    true
            );
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }

        ClientDialogueSession session = ClientDialogueSession.getInstance();
        List<DialogueOption> options = session.getOptions();
        if (!session.isAwaitingChoice() || options.isEmpty()) {
            return true;
        }

        ChoiceLayout layout = choiceLayout(options);
        for (int i = 0; i < options.size(); i++) {
            int rowTop = layout.top() + i * layout.rowHeight();
            if (!isInside(mouseX, mouseY, layout.left(), rowTop, layout.width(), layout.rowHeight() - 3)) {
                continue;
            }
            if (this.minecraft != null) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            this.choose(options.get(i));
            return true;
        }
        return true;
    }

    private ChoiceLayout choiceLayout(List<DialogueOption> options) {
        int width = 140;
        for (DialogueOption option : options) {
            width = Math.max(width, this.font.width(option.label()) + 24);
        }
        width = Math.min(Math.max(140, this.width - 32), Math.min(310, width));
        int rowHeight = this.font.lineHeight + 7;
        int left = (this.width - width) / 2;
        int top = Math.min(this.height - options.size() * rowHeight - 14, this.height / 2 + 28);
        return new ChoiceLayout(left, top, width, rowHeight);
    }

    private static boolean isInside(double mouseX, double mouseY, int left, int top, int width, int height) {
        return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height;
    }

    private static void drawChoiceBubble(GuiGraphics graphics, int left, int top, int width, int height, boolean hovered) {
        int right = left + width;
        int bottom = top + height;
        graphics.fill(left + 4, top + 2, right, bottom + 2, 0x30000000);
        int background = hovered ? DIALOGUE_HOVER : DIALOGUE_BACKGROUND;
        graphics.fill(left + 4, top, right - 4, bottom, background);
        graphics.fill(left, top + 4, right, bottom - 4, background);
        graphics.fill(left, top + 4, left + 2, bottom - 4, DIALOGUE_ACCENT);
        graphics.fill(left + 5, top, right - 5, top + 1, hovered ? DarkFantasyTheme.GOLD : DarkFantasyTheme.EDGE_SOFT);
        graphics.fill(left + 5, bottom - 1, right - 5, bottom, DarkFantasyTheme.EDGE_FAINT);
        if (hovered) {
            int cx = left + 8;
            int cy = top + height / 2;
            graphics.fill(cx, cy - 2, cx + 1, cy + 3, DarkFantasyTheme.AMBER);
            graphics.fill(cx - 2, cy, cx + 3, cy + 1, DarkFantasyTheme.AMBER);
        }
    }

    private void choose(DialogueOption option) {
        this.choosing = true;
        ClientDialogueSession.getInstance().selectOption(option.optionId());
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        if (!this.choosing && ClientDialogueSession.getInstance().isActive()) {
            ClientDialogueSession.getInstance().abortActiveNode();
        }
        super.onClose();
    }

    private record ChoiceLayout(int left, int top, int width, int rowHeight) {
    }
}
