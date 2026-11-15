package com.phantasm.briefing.client.screen;

import com.phantasm.briefing.client.cinematic.CinematicStateManager;
import com.phantasm.briefing.data.DialogueOption;
import com.phantasm.briefing.client.widgets.SolidColorRectWidget;
import com.phantasm.briefing.client.widgets.TypewriterTextWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class DialogueOverlayScreen extends PixelGridScreen {
    private static final int LETTERBOX_HEIGHT = 44;
    private static final int OVERLAY_PADDING = 6;

    private TypewriterTextWidget typewriterTextWidget;
    private int syncedNodeIndex = -1;

    public DialogueOverlayScreen() {
        super(Component.literal("剧情对话"));
    }

    @Override
    protected void buildWidgets() {
        this.addPixelWidget(new SolidColorRectWidget(0, 0, this.width, LETTERBOX_HEIGHT, 0xFF000000));
        this.addPixelWidget(new SolidColorRectWidget(0, this.height - LETTERBOX_HEIGHT, this.width, LETTERBOX_HEIGHT, 0xFF000000));

        int textWidth = Math.min(this.width - 120, 420);
        int textX = snap((this.width - textWidth) / 2.0D);
        int textY = this.height - LETTERBOX_HEIGHT + OVERLAY_PADDING;
        this.typewriterTextWidget = this.addPixelWidget(new TypewriterTextWidget(
                this.font,
                textX,
                textY,
                textWidth,
                0xDCECFF,
                3,
                1
        ));
        this.syncFromManager();
    }

    @Override
    protected void renderScreenContents(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        CinematicStateManager manager = CinematicStateManager.getInstance();
        if (!manager.isAwaitingOptionSelection()) {
            return;
        }

        List<DialogueOption> options = manager.getActiveOptions();
        if (options.isEmpty()) {
            return;
        }

        Font font = this.font;
        int optionsHeight = (options.size() * this.font.lineHeight) + ((options.size() - 1) * 2);
        int optionY = Math.max(
                OVERLAY_PADDING,
                this.height - LETTERBOX_HEIGHT - OVERLAY_PADDING - optionsHeight
        );

        for (int i = 0; i < options.size() && i < 9; i++) {
            DialogueOption option = options.get(i);
            guiGraphics.drawString(
                    font,
                    Component.literal((i + 1) + ". " + option.label()),
                    16,
                    optionY,
                    0xDCECFF,
                    true
            );
            optionY += font.lineHeight + 2;
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        CinematicStateManager.getInstance().endCinematicMode();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            CinematicStateManager.getInstance().handleEscapeExit();
            return true;
        }
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            CinematicStateManager.getInstance().selectOptionByIndex(keyCode - GLFW.GLFW_KEY_1);
            return true;
        }
        if (keyCode >= GLFW.GLFW_KEY_KP_1 && keyCode <= GLFW.GLFW_KEY_KP_9) {
            CinematicStateManager.getInstance().selectOptionByIndex(keyCode - GLFW.GLFW_KEY_KP_1);
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (codePoint >= '1' && codePoint <= '9') {
            CinematicStateManager.getInstance().selectOptionByIndex(codePoint - '1');
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return true;
    }

    public void syncFromManager() {
        CinematicStateManager manager = CinematicStateManager.getInstance();
        if (this.typewriterTextWidget == null) {
            return;
        }

        if (this.syncedNodeIndex != manager.getCurrentNodeIndex()) {
            this.syncedNodeIndex = manager.getCurrentNodeIndex();
            this.typewriterTextWidget.setText(manager.getCurrentNodeText());
        }
    }

    public boolean isCurrentNodeComplete() {
        return this.typewriterTextWidget != null && this.typewriterTextWidget.isComplete();
    }

    public void revealCurrentNode() {
        if (this.typewriterTextWidget != null) {
            this.typewriterTextWidget.revealAll();
        }
    }
}
