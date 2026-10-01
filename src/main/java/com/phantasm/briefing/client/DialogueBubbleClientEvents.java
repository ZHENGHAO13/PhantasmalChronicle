package com.phantasm.briefing.client;

import com.phantasm.briefing.client.ui.DarkFantasyTheme;
import com.phantasm.briefing.client.ui.PixelRenderUtil;

import com.phantasm.briefing.PhantasmBriefing;
import com.phantasm.briefing.client.screen.DialogueChoiceScreen;
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
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.List;

@Mod.EventBusSubscriber(modid = PhantasmBriefing.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DialogueBubbleClientEvents {
    private static final int DIALOGUE_BACKGROUND = DarkFantasyTheme.DIALOGUE_BACKGROUND;
    private static final int DIALOGUE_ACCENT = DarkFantasyTheme.DIALOGUE_ACCENT;
    private static final int DIALOGUE_TITLE = DarkFantasyTheme.DIALOGUE_TITLE;
    private static final int DIALOGUE_TEXT = DarkFantasyTheme.DIALOGUE_TEXT;
    private static final int DIALOGUE_MUTED = DarkFantasyTheme.DIALOGUE_MUTED;

    private DialogueBubbleClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || Minecraft.getInstance().isPaused()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientDialogueSession session = ClientDialogueSession.getInstance();
        if (session.clearIfSpeakerMissing(minecraft)) {
            return;
        }
        boolean focused = DialogueFocusClient.canPresentActiveDialogue(minecraft, session);
        if (focused && (minecraft.screen == null || minecraft.screen instanceof DialogueChoiceScreen)) {
            session.tick();
        }
        if (focused && session.isAwaitingChoice() && minecraft.screen == null) {
            minecraft.setScreen(new DialogueChoiceScreen());
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientDialogueSession.getInstance().clear();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getAction() != GLFW.GLFW_PRESS
                || event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT
                || Minecraft.getInstance().screen != null) {
            return;
        }

        ClientDialogueSession session = ClientDialogueSession.getInstance();
        if (!session.isActive() || session.isAwaitingChoice()) {
            return;
        }
        if (!DialogueFocusClient.canPresentActiveDialogue(Minecraft.getInstance(), session)) {
            return;
        }

        session.advance();
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRenderHud(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientDialogueSession session = ClientDialogueSession.getInstance();
        if (!session.isActive() || minecraft.options.hideGui || minecraft.player == null
                || (minecraft.screen != null && !(minecraft.screen instanceof DialogueChoiceScreen))) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int screenWidth = event.getWindow().getGuiScaledWidth();
        int screenHeight = event.getWindow().getGuiScaledHeight();
        if (DialogueFocusClient.usesGazeBubble(session)) {
            if (DialogueFocusClient.canPresentActiveDialogue(minecraft, session)) {
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
        DialogueImage image = session.isImageContent()
                ? resolveDialogueImage(session.getCurrentImage(), width - 28, Math.max(72, Math.min(220, screenHeight / 3)))
                : null;
        List<FormattedCharSequence> lines = image == null
                ? font.split(Component.literal(session.getVisibleLine()), width - 28)
                : List.of();
        int contentHeight = image != null ? image.height() : Math.max(1, lines.size()) * (font.lineHeight + 2);
        int height = 28 + contentHeight + 14;
        int top = screenHeight - height - 42;

        graphics.fill(left, top, left + width, top + height, DIALOGUE_BACKGROUND);
        graphics.fill(left, top, left + width, top + 1, DarkFantasyTheme.EDGE_SOFT);
        graphics.fill(left, top + height - 1, left + width, top + height, DarkFantasyTheme.EDGE_FAINT);
        graphics.fill(left, top, left + 3, top + height, DIALOGUE_ACCENT);
        graphics.fill(left + width - 14, top + 2, left + width - 5, top + 3, DarkFantasyTheme.BRASS_DIM);
        graphics.drawString(font, session.getTitle(), left + 14, top + 9, DIALOGUE_TITLE, true);
        int y = top + 24;
        if (image != null) {
            drawDialogueImage(graphics, image, left + (width - image.width()) / 2, y);
        } else {
            for (FormattedCharSequence line : lines) {
                graphics.drawString(font, line, left + 14, y, DIALOGUE_TEXT, true);
                y += font.lineHeight + 2;
            }
        }
        if (!session.isAwaitingChoice()) {
            Component hint = rightClickHint(session);
            graphics.drawString(font, hint, left + width - font.width(hint) - 10, top + height - 13, DIALOGUE_MUTED, false);
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
        DialogueImage image = session.isImageContent()
                ? resolveDialogueImage(session.getCurrentImage(), maximumBubbleWidth - 24, Math.max(72, Math.min(190, screenHeight / 3)))
                : null;
        int desiredBubbleWidth = Math.max(
                font.width(session.getTitle()) + 24,
                image != null ? image.width() + 24 : font.width(session.getCurrentLine()) + 24
        );
        if (!session.isAwaitingChoice()) {
            desiredBubbleWidth = Math.max(desiredBubbleWidth, font.width(rightClickHint(session)) + 22);
        }
        int bubbleWidth = Math.max(104, Math.min(maximumBubbleWidth, desiredBubbleWidth));
        int groupWidth = portraitSize + portraitGap + bubbleWidth;
        int groupLeft = (screenWidth - groupWidth) / 2;
        int bubbleLeft = groupLeft + portraitSize + portraitGap;
        int textWidth = bubbleWidth - 24;
        List<FormattedCharSequence> lines = image == null
                ? font.split(Component.literal(session.getVisibleLine()), textWidth)
                : List.of();
        int contentHeight = image != null
                ? image.height()
                : Math.max(1, font.split(Component.literal(session.getCurrentLine()), textWidth).size()) * (font.lineHeight + 2);
        int bubbleHeight = 29 + contentHeight + 16;
        int desiredTop = (screenHeight / 2) - bubbleHeight - 30;
        int maximumTop = screenHeight - bubbleHeight - 9;
        int top = Math.max(8, Math.min(desiredTop, maximumTop));
        int bottom = top + bubbleHeight;
        int portraitTop = top + Math.max(0, (bubbleHeight - portraitSize) / 2);
        int tailY = portraitTop + portraitSize / 2;

        drawChatBubble(graphics, bubbleLeft, top, bubbleWidth, bubbleHeight, tailY);
        renderSpeakerPortrait(graphics, font, speaker, session.getTitle(), groupLeft, portraitTop, portraitSize);
        String title = font.plainSubstrByWidth(session.getTitle(), textWidth);
        graphics.drawString(font, title, bubbleLeft + 12, top + 8, DIALOGUE_TITLE, true);

        int y = top + 21;
        if (image != null) {
            drawDialogueImage(graphics, image, bubbleLeft + (bubbleWidth - image.width()) / 2, y);
        } else {
            for (FormattedCharSequence line : lines) {
                graphics.drawString(font, line, bubbleLeft + 12, y, DIALOGUE_TEXT, true);
                y += font.lineHeight + 2;
            }
        }

        if (!session.isAwaitingChoice()) {
            Component hint = rightClickHint(session);
            graphics.drawString(font, hint, bubbleLeft + bubbleWidth - font.width(hint) - 11, bottom - 12, DIALOGUE_MUTED, false);
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
        graphics.fill(left + 6, top, right - 6, top + 1, DarkFantasyTheme.EDGE_SOFT);
        graphics.fill(left + 6, bottom - 1, right - 6, bottom, DarkFantasyTheme.EDGE_FAINT);
        graphics.fill(left, top + 5, left + 2, bottom - 5, DIALOGUE_ACCENT);
        graphics.fill(right - 13, top + 3, right - 5, top + 4, DarkFantasyTheme.BRASS_DIM);
        graphics.fill(left - 4, tailY - 4, left + 2, tailY + 5, background);
        graphics.fill(left - 7, tailY - 2, left - 2, tailY + 3, background);
        graphics.fill(left - 9, tailY, left - 5, tailY + 1, background);
    }


    private static DialogueImage resolveDialogueImage(String imageName, int maxWidth, int maxHeight) {
        ContentTextureCache.LoadResult result = ContentTextureCache.load(imageName);
        ContentTextureCache.TextureEntry texture = result.texture();
        if (texture == null) {
            return new DialogueImage(null, Math.max(1, Math.min(160, maxWidth)), Math.max(1, Math.min(90, maxHeight)));
        }
        double scale = Math.min(1.0D, Math.min(maxWidth / (double) texture.width(), maxHeight / (double) texture.height()));
        int width = Math.max(1, (int) Math.round(texture.width() * scale));
        int height = Math.max(1, (int) Math.round(texture.height() * scale));
        return new DialogueImage(texture, width, height);
    }

    private static void drawDialogueImage(GuiGraphics graphics, DialogueImage image, int x, int y) {
        if (image.texture() == null) return;
        ContentTextureCache.TextureEntry texture = image.texture();
        PixelRenderUtil.prepareLinearTexture(texture.location());
        graphics.blit(texture.location(), x, y, image.width(), image.height(), 0.0F, 0.0F,
                texture.width(), texture.height(), texture.width(), texture.height());
    }

    private record DialogueImage(ContentTextureCache.TextureEntry texture, int width, int height) {}

    private static void renderMaidWorldControls(
            GuiGraphics graphics,
            Font font,
            ClientDialogueSession session,
            int screenWidth,
            int screenHeight
    ) {
        if (session.isAwaitingChoice()) {
            return;
        }

        Component hint = rightClickHint(session);
        int width = font.width(hint) + 14;
        int left = (screenWidth - width) / 2;
        int top = Math.min(screenHeight - 30, screenHeight / 2 + 28);
        graphics.fill(left + 3, top + 2, left + width + 2, top + 15, 0x30000000);
        graphics.fill(left, top, left + width, top + 13, DIALOGUE_BACKGROUND);
        graphics.fill(left, top, left + width, top + 1, DarkFantasyTheme.EDGE_SOFT);
        graphics.fill(left, top, left + 2, top + 13, DIALOGUE_ACCENT);
        graphics.drawString(font, hint, left + 7, top + 3, DIALOGUE_MUTED, false);
    }

    private static Component rightClickHint(ClientDialogueSession session) {
        return Component.translatable(session.isLineFullyVisible()
                ? "dialogue.phantasmbriefing.right_click_continue"
                : "dialogue.phantasmbriefing.right_click_reveal");
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
        graphics.fill(left + 5, top, right - 5, bottom, DarkFantasyTheme.DIALOGUE_BACKGROUND);
        graphics.fill(left, top + 5, right, bottom - 5, DarkFantasyTheme.DIALOGUE_BACKGROUND);

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
            graphics.drawCenteredString(font, initial, left + size / 2, top + (size - font.lineHeight) / 2, DarkFantasyTheme.TEXT_PRIMARY);
        }

        int border = DIALOGUE_ACCENT;
        graphics.fill(left + 5, top, right - 5, top + 1, border);
        graphics.fill(left + 5, bottom - 1, right - 5, bottom, border);
        graphics.fill(left, top + 5, left + 1, bottom - 5, border);
        graphics.fill(right - 1, top + 5, right, bottom - 5, border);
    }
}
