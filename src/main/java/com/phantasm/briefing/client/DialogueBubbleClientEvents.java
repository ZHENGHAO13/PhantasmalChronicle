package com.phantasm.briefing.client;

import com.phantasm.briefing.PhantasmBriefing;
import com.phantasm.briefing.data.DialogueOption;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.List;

@Mod.EventBusSubscriber(modid = PhantasmBriefing.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DialogueBubbleClientEvents {
    private static final int DIALOGUE_BACKGROUND = 0xD812171F;
    private static final int DIALOGUE_ACCENT = 0xFF55D8E8;
    private static final int DIALOGUE_TITLE = 0xFF63E6F5;
    private static final int DIALOGUE_TEXT = 0xFFE8F4F7;
    private static final int DIALOGUE_OPTION = 0xFFFFD36A;
    private static final int DIALOGUE_MUTED = 0xFF8EA8AF;

    private DialogueBubbleClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !Minecraft.getInstance().isPaused()) {
            Minecraft minecraft = Minecraft.getInstance();
            ClientDialogueSession session = ClientDialogueSession.getInstance();
            if (!DialogueFocusClient.usesGazeBubble(session)
                    || (minecraft.screen == null && DialogueFocusClient.isFocusedOnSpeaker(minecraft, session))) {
                session.tick();
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientDialogueSession.getInstance().clear();
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS || Minecraft.getInstance().screen != null) {
            return;
        }
        ClientDialogueSession session = ClientDialogueSession.getInstance();
        if (!session.isActive()) {
            return;
        }
        if (DialogueFocusClient.usesGazeBubble(session)
                && !DialogueFocusClient.isFocusedOnSpeaker(Minecraft.getInstance(), session)) {
            return;
        }
        if (event.getKey() == GLFW.GLFW_KEY_ENTER || event.getKey() == GLFW.GLFW_KEY_KP_ENTER) {
            session.advance();
        } else if (event.getKey() >= GLFW.GLFW_KEY_1 && event.getKey() <= GLFW.GLFW_KEY_9) {
            session.selectOptionByIndex(event.getKey() - GLFW.GLFW_KEY_1);
        }
    }

    @SubscribeEvent
    public static void onRenderHud(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientDialogueSession session = ClientDialogueSession.getInstance();
        if (!session.isActive() || minecraft.options.hideGui || minecraft.player == null || minecraft.screen != null) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int screenWidth = event.getWindow().getGuiScaledWidth();
        int screenHeight = event.getWindow().getGuiScaledHeight();
        if (DialogueFocusClient.usesGazeBubble(session)) {
            if (DialogueFocusClient.isFocusedOnSpeaker(minecraft, session)) {
                if (DialogueFocusClient.usesPortraitBubble(session)) {
                    renderGazeBubble(
                            graphics,
                            minecraft.font,
                            session,
                            DialogueFocusClient.getSpeaker(minecraft, session),
                            screenWidth,
                            screenHeight
                    );
                } else if (DialogueFocusClient.usesMaidWorldBubble(session)) {
                    renderMaidWorldControls(graphics, minecraft.font, session, screenWidth, screenHeight);
                }
            }
            return;
        }

        renderDialogueBar(graphics, minecraft.font, session, screenWidth, screenHeight);
    }

    private static void renderDialogueBar(
            GuiGraphics graphics,
            Font font,
            ClientDialogueSession session,
            int screenWidth,
            int screenHeight
    ) {
        int width = Math.min(430, screenWidth - 48);
        int left = (screenWidth - width) / 2;
        List<FormattedCharSequence> lines = font.split(Component.literal(session.getVisibleLine()), width - 28);
        int optionCount = session.isAwaitingChoice() ? Math.min(9, session.getOptions().size()) : 0;
        int height = 28 + Math.max(1, lines.size()) * (font.lineHeight + 2)
                + (optionCount == 0 ? 14 : 10 + optionCount * (font.lineHeight + 5));
        int top = screenHeight - height - 42;

        graphics.fill(left, top, left + width, top + height, DIALOGUE_BACKGROUND);
        graphics.fill(left, top, left + 3, top + height, DIALOGUE_ACCENT);
        graphics.drawString(font, session.getTitle(), left + 14, top + 9, DIALOGUE_TITLE, true);
        int y = top + 24;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, left + 14, y, DIALOGUE_TEXT, true);
            y += font.lineHeight + 2;
        }
        if (optionCount > 0) {
            y += 5;
            for (int i = 0; i < optionCount; i++) {
                DialogueOption option = session.getOptions().get(i);
                graphics.drawString(font, (i + 1) + "  " + option.label(), left + 20, y, DIALOGUE_OPTION, true);
                y += font.lineHeight + 5;
            }
        } else {
            graphics.drawString(font, "[Enter] 继续", left + width - 76, top + height - 13, DIALOGUE_MUTED, false);
        }
    }

    private static void renderGazeBubble(
            GuiGraphics graphics,
            Font font,
            ClientDialogueSession session,
            Entity speaker,
            int screenWidth,
            int screenHeight
    ) {
        int availableWidth = Math.max(150, screenWidth - 24);
        int portraitSize = Math.min(36, Math.max(28, availableWidth / 7));
        int portraitGap = 8;
        int maximumBubbleWidth = Math.min(330, Math.max(104, availableWidth - portraitSize - portraitGap));
        int desiredBubbleWidth = Math.max(
                font.width(session.getTitle()) + 24,
                font.width(session.getCurrentLine()) + 24
        );
        if (session.isAwaitingChoice()) {
            for (int i = 0; i < Math.min(9, session.getOptions().size()); i++) {
                desiredBubbleWidth = Math.max(desiredBubbleWidth, font.width(session.getOptions().get(i).label()) + 51);
            }
        } else {
            desiredBubbleWidth = Math.max(desiredBubbleWidth, font.width("[Enter] 显示全文") + 22);
        }
        int bubbleWidth = Math.max(104, Math.min(maximumBubbleWidth, desiredBubbleWidth));
        int groupWidth = portraitSize + portraitGap + bubbleWidth;
        int groupLeft = (screenWidth - groupWidth) / 2;
        int bubbleLeft = groupLeft + portraitSize + portraitGap;
        int textWidth = bubbleWidth - 24;
        List<FormattedCharSequence> lines = font.split(Component.literal(session.getVisibleLine()), textWidth);
        int lineCount = Math.max(1, font.split(Component.literal(session.getCurrentLine()), textWidth).size());
        int bubbleHeight = 29 + lineCount * (font.lineHeight + 2) + 16;
        int optionCount = session.isAwaitingChoice() ? Math.min(9, session.getOptions().size()) : 0;
        int rowHeight = font.lineHeight + 7;
        int optionsHeight = optionCount * rowHeight;
        int desiredTop = (screenHeight / 2) - bubbleHeight - 30;
        int maximumTop = screenHeight - bubbleHeight - (optionCount == 0 ? 9 : optionsHeight + 18);
        int top = Math.max(8, Math.min(desiredTop, maximumTop));
        int bottom = top + bubbleHeight;
        int portraitTop = top + Math.max(0, (bubbleHeight - portraitSize) / 2);
        int tailY = portraitTop + portraitSize / 2;

        drawChatBubble(graphics, bubbleLeft, top, bubbleWidth, bubbleHeight, tailY);
        renderSpeakerPortrait(graphics, font, speaker, session.getTitle(), groupLeft, portraitTop, portraitSize);
        String title = font.plainSubstrByWidth(session.getTitle(), textWidth);
        graphics.drawString(font, title, bubbleLeft + 12, top + 8, DIALOGUE_TITLE, true);

        int y = top + 21;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, bubbleLeft + 12, y, DIALOGUE_TEXT, true);
            y += font.lineHeight + 2;
        }

        if (!session.isAwaitingChoice()) {
            String hint = session.isLineFullyVisible() ? "[Enter] 继续" : "[Enter] 显示全文";
            graphics.drawString(font, hint, bubbleLeft + bubbleWidth - font.width(hint) - 11, bottom - 12, DIALOGUE_MUTED, false);
            return;
        }

        int optionTop = bottom + 10;
        for (int i = 0; i < optionCount; i++) {
            DialogueOption option = session.getOptions().get(i);
            int rowTop = optionTop + i * rowHeight;
            drawChoiceBubble(graphics, bubbleLeft, rowTop, bubbleWidth, rowHeight - 3);
            graphics.drawString(font, Integer.toString(i + 1), bubbleLeft + 11, rowTop + 4, DIALOGUE_OPTION, true);
            String label = font.plainSubstrByWidth(option.label(), bubbleWidth - 42);
            graphics.drawString(font, label, bubbleLeft + 27, rowTop + 4, DIALOGUE_TEXT, true);
        }
    }

    private static void drawChatBubble(GuiGraphics graphics, int left, int top, int width, int height, int tailY) {
        int right = left + width;
        int bottom = top + height;
        int shadow = 0x38000000;
        int background = DIALOGUE_BACKGROUND;

        graphics.fill(left + 5, top + 3, right - 1, bottom + 2, shadow);
        graphics.fill(left + 2, top + 7, right + 2, bottom - 3, shadow);
        graphics.fill(left + 5, top, right - 5, bottom, background);
        graphics.fill(left, top + 5, right, bottom - 5, background);
        graphics.fill(left, top + 5, left + 2, bottom - 5, DIALOGUE_ACCENT);
        graphics.fill(left - 4, tailY - 4, left + 2, tailY + 5, background);
        graphics.fill(left - 7, tailY - 2, left - 2, tailY + 3, background);
        graphics.fill(left - 9, tailY, left - 5, tailY + 1, background);
    }

    private static void drawChoiceBubble(GuiGraphics graphics, int left, int top, int width, int height) {
        int right = left + width;
        int bottom = top + height;
        graphics.fill(left + 4, top + 2, right, bottom + 2, 0x30000000);
        graphics.fill(left + 4, top, right - 4, bottom, DIALOGUE_BACKGROUND);
        graphics.fill(left, top + 4, right, bottom - 4, DIALOGUE_BACKGROUND);
        graphics.fill(left, top + 4, left + 2, bottom - 4, DIALOGUE_ACCENT);
    }

    private static void renderMaidWorldControls(
            GuiGraphics graphics,
            Font font,
            ClientDialogueSession session,
            int screenWidth,
            int screenHeight
    ) {
        if (!session.isAwaitingChoice()) {
            String hint = session.isLineFullyVisible() ? "[Enter] 继续" : "[Enter] 显示全文";
            int width = font.width(hint) + 14;
            int left = (screenWidth - width) / 2;
            int top = Math.min(screenHeight - 30, screenHeight / 2 + 28);
            graphics.fill(left + 3, top + 2, left + width + 2, top + 15, 0x30000000);
            graphics.fill(left, top, left + width, top + 13, DIALOGUE_BACKGROUND);
            graphics.fill(left, top, left + 2, top + 13, DIALOGUE_ACCENT);
            graphics.drawString(font, hint, left + 7, top + 3, DIALOGUE_MUTED, false);
            return;
        }

        int optionCount = Math.min(9, session.getOptions().size());
        int width = 140;
        for (int i = 0; i < optionCount; i++) {
            width = Math.max(width, font.width(session.getOptions().get(i).label()) + 51);
        }
        width = Math.min(Math.max(140, screenWidth - 32), Math.min(310, width));
        int rowHeight = font.lineHeight + 7;
        int left = (screenWidth - width) / 2;
        int top = Math.min(screenHeight - optionCount * rowHeight - 14, screenHeight / 2 + 28);
        for (int i = 0; i < optionCount; i++) {
            int rowTop = top + i * rowHeight;
            drawChoiceBubble(graphics, left, rowTop, width, rowHeight - 3);
            graphics.drawString(font, Integer.toString(i + 1), left + 11, rowTop + 4, DIALOGUE_OPTION, true);
            String label = font.plainSubstrByWidth(session.getOptions().get(i).label(), width - 42);
            graphics.drawString(font, label, left + 27, rowTop + 4, DIALOGUE_TEXT, true);
        }
    }

    private static void renderSpeakerPortrait(
            GuiGraphics graphics,
            Font font,
            Entity speaker,
            String fallbackTitle,
            int left,
            int top,
            int size
    ) {
        int right = left + size;
        int bottom = top + size;
        graphics.fill(left + 5, top + 2, right - 1, bottom + 2, 0x40000000);
        graphics.fill(left + 5, top, right - 5, bottom, 0xF4192026);
        graphics.fill(left, top + 5, right, bottom - 5, 0xF4192026);

        if (speaker instanceof LivingEntity livingEntity) {
            graphics.enableScissor(left + 2, top + 2, right - 2, bottom - 2);
            float entityWidth = Math.max(0.45F, livingEntity.getBbWidth());
            int renderScale = Math.max(22, Math.min(64, Math.round((size * 0.85F) / entityWidth)));
            int renderBaseY = top + size / 2 + Math.round(livingEntity.getEyeHeight() * renderScale);
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    graphics,
                    left + size / 2,
                    renderBaseY,
                    renderScale,
                    0.0F,
                    0.0F,
                    livingEntity
            );
            graphics.disableScissor();
        } else {
            String initial = fallbackTitle == null || fallbackTitle.isBlank()
                    ? "?"
                    : fallbackTitle.substring(0, 1);
            graphics.drawCenteredString(font, initial, left + size / 2, top + (size - font.lineHeight) / 2, 0xFFF3F6F7);
        }

        int border = DIALOGUE_ACCENT;
        graphics.fill(left + 5, top, right - 5, top + 1, border);
        graphics.fill(left + 5, bottom - 1, right - 5, bottom, border);
        graphics.fill(left, top + 5, left + 1, bottom - 5, border);
        graphics.fill(right - 1, top + 5, right, bottom - 5, border);
    }
}
