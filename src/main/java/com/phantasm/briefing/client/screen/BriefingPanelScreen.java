package com.phantasm.briefing.client.screen;

import com.phantasm.briefing.data.DialogueOption;
import com.phantasm.briefing.client.widgets.HoverButtonWidget;
import com.phantasm.briefing.client.widgets.PixelImageWidget;
import com.phantasm.briefing.client.widgets.ShadowLabelWidget;
import com.phantasm.briefing.client.widgets.SolidColorRectWidget;
import com.phantasm.briefing.client.widgets.TextBoxWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;

public final class BriefingPanelScreen extends PixelGridScreen {
    private static final ResourceLocation WHITE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/white.png");
    private static final int BODY_LINE_SPACING = 6;

    private final String questNodeId;
    private final String titleText;
    private final List<String> bodyLines;
    private final List<DialogueOption> options;
    private final Runnable onAcknowledge;
    private final Consumer<String> onOptionSelected;
    private final Runnable onAbort;

    private boolean acknowledged;

    public BriefingPanelScreen(
            String questNodeId,
            String titleText,
            List<String> bodyLines,
            List<DialogueOption> options,
            Runnable onAcknowledge,
            Consumer<String> onOptionSelected,
            Runnable onAbort
    ) {
        super(Component.literal(titleText));
        this.questNodeId = questNodeId;
        this.titleText = titleText;
        this.bodyLines = List.copyOf(bodyLines);
        this.options = List.copyOf(options);
        this.onAcknowledge = onAcknowledge;
        this.onOptionSelected = onOptionSelected;
        this.onAbort = onAbort;
    }

    @Override
    protected void buildWidgets() {
        Font font = this.font;
        int panelWidth = 320;
        int panelHeight = 176;
        int panelX = snap((this.width - panelWidth) / 2.0D);
        int panelY = snap((this.height - panelHeight) / 2.0D);
        int bodyY = panelY + 52;

        this.addPixelWidget(new SolidColorRectWidget(panelX, panelY, panelWidth, panelHeight, 0xF412171F, 6));
        this.addPixelWidget(new SolidColorRectWidget(panelX, panelY, panelWidth, 1, 0x6634465C));
        this.addPixelWidget(new SolidColorRectWidget(panelX, panelY + panelHeight - 1, panelWidth, 1, 0x6634465C));

        this.addPixelWidget(new ShadowLabelWidget(font, panelX + 18, panelY + 16, Component.literal(this.titleText), 0xDCECFF));

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

        if (!this.questNodeId.isBlank()) {
            this.addPixelWidget(new ShadowLabelWidget(
                    font,
                    panelX + 18,
                    panelY + 42,
                    Component.literal("节点标识: " + this.questNodeId),
                    0x7F93AD
            ));
            bodyY += font.lineHeight + 6;
        }

        this.addPixelWidget(new TextBoxWidget(
                font,
                panelX + 18,
                bodyY,
                this.bodyLines.toArray(String[]::new),
                0xDCECFF,
                BODY_LINE_SPACING
        ));

        if (this.options.isEmpty()) {
            int buttonWidth = 114;
            int buttonHeight = 22;
            int buttonX = panelX + panelWidth - buttonWidth - 18;
            int buttonY = panelY + panelHeight - buttonHeight - 18;
            this.addPixelWidget(new HoverButtonWidget(
                    font,
                    buttonX,
                    buttonY,
                    buttonWidth,
                    buttonHeight,
                    Component.literal("好的，已知晓"),
                    0xFF243142,
                    0xFF35506D,
                    0xFF6B87A6,
                    0xDCECFF,
                    0xFFFFFFF2,
                    button -> {
                        this.acknowledged = true;
                        this.onAcknowledge.run();
                        this.onClose();
                    }
            ));
            return;
        }

        int buttonWidth = panelWidth - 36;
        int buttonHeight = 20;
        int buttonY = panelY + panelHeight - 18 - (this.options.size() * (buttonHeight + 6)) + 6;
        for (DialogueOption option : this.options) {
            this.addPixelWidget(new HoverButtonWidget(
                    font,
                    panelX + 18,
                    buttonY,
                    buttonWidth,
                    buttonHeight,
                    Component.literal(option.label()),
                    0xFF243142,
                    0xFF35506D,
                    0xFF6B87A6,
                    0xDCECFF,
                    0xFFFFFFF2,
                    button -> {
                        this.acknowledged = true;
                        this.onOptionSelected.accept(option.optionId());
                        this.onClose();
                    }
            ));
            buttonY += buttonHeight + 6;
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        if (!this.acknowledged) {
            this.onAbort.run();
        }
        super.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        return true;
    }
}
