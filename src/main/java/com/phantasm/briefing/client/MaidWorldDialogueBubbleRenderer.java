package com.phantasm.briefing.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Divisor;
import com.phantasm.briefing.PhantasmBriefing;
import com.phantasm.briefing.client.screen.DialogueChoiceScreen;
import it.unimi.dsi.fastutil.ints.IntIterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.List;

@Mod.EventBusSubscriber(modid = PhantasmBriefing.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MaidWorldDialogueBubbleRenderer {
    private static final ResourceLocation BUBBLE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            PhantasmBriefing.MOD_ID,
            "textures/entity/chat_bubble/maid_type2.png"
    );

    private static final float WORLD_SCALE = 0.025F;
    private static final int MAX_TEXT_WIDTH = 200;
    private static final int CONTENT_OFFSET = 5;
    private static final int BACKGROUND_SLICE = 8;
    private static final int BACKGROUND_SOURCE_WIDTH = 48;
    private static final int BACKGROUND_SOURCE_HEIGHT = 24;
    private static final int TEXTURE_SIZE = 256;

    private MaidWorldDialogueBubbleRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientDialogueSession session = ClientDialogueSession.getInstance();
        LivingEntity speaker = event.getEntity();
        if (!session.isActive()
                || !DialogueFocusClient.usesMaidWorldBubble(session)
                || minecraft.options.hideGui
                || (minecraft.screen != null && !(minecraft.screen instanceof DialogueChoiceScreen))
                || speaker.getId() != session.getSpeakerEntityId()
                || !DialogueFocusClient.canPresentActiveDialogue(minecraft, session)) {
            return;
        }

        double distanceToCamera = minecraft.getEntityRenderDispatcher().distanceToSqr(speaker);
        if (!ForgeHooksClient.isNameplateInRenderDistance(speaker, distanceToCamera)) {
            return;
        }

        renderBubble(
                event.getPoseStack(),
                event.getMultiBufferSource(),
                minecraft.font,
                session,
                speaker,
                event.getPackedLight()
        );
    }

    private static void renderBubble(
            PoseStack poseStack,
            MultiBufferSource buffers,
            Font font,
            ClientDialogueSession session,
            LivingEntity speaker,
            int packedLight
    ) {
        ContentTextureCache.TextureEntry dialogueImage = null;
        int contentWidth;
        int contentHeight;
        List<FormattedCharSequence> lines;
        if (session.isImageContent()) {
            dialogueImage = ContentTextureCache.load(session.getCurrentImage()).texture();
            if (dialogueImage != null) {
                double scale = Math.min(1.0D, Math.min(MAX_TEXT_WIDTH / (double) dialogueImage.width(), 120.0D / dialogueImage.height()));
                contentWidth = Math.max(1, (int) Math.round(dialogueImage.width() * scale));
                contentHeight = Math.max(1, (int) Math.round(dialogueImage.height() * scale));
            } else {
                contentWidth = 120;
                contentHeight = 68;
            }
            lines = List.of();
        } else {
            lines = font.split(Component.literal(session.getVisibleLine()), MAX_TEXT_WIDTH);
            int textWidth = 0;
            for (FormattedCharSequence line : lines) {
                textWidth = Math.max(textWidth, font.width(line));
            }
            contentWidth = textWidth;
            contentHeight = Math.max(font.lineHeight, lines.size() * font.lineHeight);
        }
        int backgroundWidth = contentWidth + CONTENT_OFFSET * 2;
        int backgroundHeight = contentHeight + CONTENT_OFFSET * 2;

        poseStack.pushPose();
        poseStack.translate(0.0D, speaker.getNameTagOffsetY(), 0.0D);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        poseStack.translate(0.0D, 0.0D, -0.15D);
        poseStack.scale(-WORLD_SCALE, -WORLD_SCALE, WORLD_SCALE);

        WorldBubbleGraphics graphics = new WorldBubbleGraphics(poseStack, packedLight);
        RenderSystem.enableDepthTest();
        graphics.blitNineSliced(
                BUBBLE_TEXTURE,
                -backgroundWidth / 2,
                -backgroundHeight,
                backgroundWidth,
                backgroundHeight,
                BACKGROUND_SLICE,
                BACKGROUND_SLICE,
                BACKGROUND_SOURCE_WIDTH,
                BACKGROUND_SOURCE_HEIGHT,
                0,
                0
        );

        // 气泡尾使用边框贴图正下方的 16×16 区域。
        poseStack.translate(0.0D, 0.0D, -0.01D);
        graphics.blit(BUBBLE_TEXTURE, -8, -8, 32, 24, 16, 16);
        poseStack.translate(-backgroundWidth / 2.0D + CONTENT_OFFSET, -backgroundHeight + CONTENT_OFFSET, -0.01D);

        if (dialogueImage != null) {
            graphics.blit(dialogueImage.location(), 0, 0, contentWidth, contentHeight,
                    0.0F, 0.0F, dialogueImage.width(), dialogueImage.height(),
                    dialogueImage.width(), dialogueImage.height());
        } else {
            int y = 0;
            for (FormattedCharSequence line : lines) {
                font.drawInBatch(
                        line,
                        0,
                        y,
                        0x000000,
                        false,
                        poseStack.last().pose(),
                        buffers,
                        Font.DisplayMode.NORMAL,
                        0,
                        LightTexture.FULL_BRIGHT
                );
                y += font.lineHeight;
            }
        }
        poseStack.popPose();
    }

    private static final class WorldBubbleGraphics {
        private final PoseStack poseStack;
        private final int packedLight;

        private WorldBubbleGraphics(PoseStack poseStack, int packedLight) {
            this.poseStack = poseStack;
            this.packedLight = packedLight;
        }

        private void blitNineSliced(
                ResourceLocation texture,
                int x,
                int y,
                int width,
                int height,
                int sliceWidth,
                int sliceHeight,
                int sourceWidth,
                int sourceHeight,
                int textureX,
                int textureY
        ) {
            blitNineSliced(
                    texture,
                    x,
                    y,
                    width,
                    height,
                    sliceWidth,
                    sliceHeight,
                    sliceWidth,
                    sliceHeight,
                    sourceWidth,
                    sourceHeight,
                    textureX,
                    textureY
            );
        }

        private void blitNineSliced(
                ResourceLocation texture,
                int x,
                int y,
                int width,
                int height,
                int leftSliceWidth,
                int topSliceHeight,
                int rightSliceWidth,
                int bottomSliceHeight,
                int sourceWidth,
                int sourceHeight,
                int textureX,
                int textureY
        ) {
            leftSliceWidth = Math.min(leftSliceWidth, width / 2);
            rightSliceWidth = Math.min(rightSliceWidth, width / 2);
            topSliceHeight = Math.min(topSliceHeight, height / 2);
            bottomSliceHeight = Math.min(bottomSliceHeight, height / 2);

            if (width == sourceWidth && height == sourceHeight) {
                blit(texture, x, y, textureX, textureY, width, height);
            } else if (height == sourceHeight) {
                blit(texture, x, y, textureX, textureY, leftSliceWidth, height);
                blitRepeating(texture, x + leftSliceWidth, y, width - rightSliceWidth - leftSliceWidth, height,
                        textureX + leftSliceWidth, textureY, sourceWidth - rightSliceWidth - leftSliceWidth, sourceHeight);
                blit(texture, x + width - rightSliceWidth, y, textureX + sourceWidth - rightSliceWidth, textureY,
                        rightSliceWidth, height);
            } else if (width == sourceWidth) {
                blit(texture, x, y, textureX, textureY, width, topSliceHeight);
                blitRepeating(texture, x, y + topSliceHeight, width, height - bottomSliceHeight - topSliceHeight,
                        textureX, textureY + topSliceHeight, sourceWidth, sourceHeight - bottomSliceHeight - topSliceHeight);
                blit(texture, x, y + height - bottomSliceHeight, textureX,
                        textureY + sourceHeight - bottomSliceHeight, width, bottomSliceHeight);
            } else {
                blit(texture, x, y, textureX, textureY, leftSliceWidth, topSliceHeight);
                blitRepeating(texture, x + leftSliceWidth, y, width - rightSliceWidth - leftSliceWidth, topSliceHeight,
                        textureX + leftSliceWidth, textureY, sourceWidth - rightSliceWidth - leftSliceWidth, topSliceHeight);
                blit(texture, x + width - rightSliceWidth, y, textureX + sourceWidth - rightSliceWidth, textureY,
                        rightSliceWidth, topSliceHeight);

                blit(texture, x, y + height - bottomSliceHeight, textureX,
                        textureY + sourceHeight - bottomSliceHeight, leftSliceWidth, bottomSliceHeight);
                blitRepeating(texture, x + leftSliceWidth, y + height - bottomSliceHeight,
                        width - rightSliceWidth - leftSliceWidth, bottomSliceHeight,
                        textureX + leftSliceWidth, textureY + sourceHeight - bottomSliceHeight,
                        sourceWidth - rightSliceWidth - leftSliceWidth, bottomSliceHeight);
                blit(texture, x + width - rightSliceWidth, y + height - bottomSliceHeight,
                        textureX + sourceWidth - rightSliceWidth, textureY + sourceHeight - bottomSliceHeight,
                        rightSliceWidth, bottomSliceHeight);

                blitRepeating(texture, x, y + topSliceHeight, leftSliceWidth,
                        height - bottomSliceHeight - topSliceHeight,
                        textureX, textureY + topSliceHeight, leftSliceWidth,
                        sourceHeight - bottomSliceHeight - topSliceHeight);
                blitRepeating(texture, x + leftSliceWidth, y + topSliceHeight,
                        width - rightSliceWidth - leftSliceWidth, height - bottomSliceHeight - topSliceHeight,
                        textureX + leftSliceWidth, textureY + topSliceHeight,
                        sourceWidth - rightSliceWidth - leftSliceWidth,
                        sourceHeight - bottomSliceHeight - topSliceHeight);
                blitRepeating(texture, x + width - rightSliceWidth, y + topSliceHeight, rightSliceWidth,
                        height - bottomSliceHeight - topSliceHeight,
                        textureX + sourceWidth - rightSliceWidth, textureY + topSliceHeight,
                        rightSliceWidth, sourceHeight - bottomSliceHeight - topSliceHeight);
            }
        }

        private void blitRepeating(
                ResourceLocation texture,
                int startX,
                int startY,
                int areaWidth,
                int areaHeight,
                int textureX,
                int textureY,
                int sourceWidth,
                int sourceHeight
        ) {
            if (areaWidth <= 0 || areaHeight <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
                return;
            }
            int currentX = startX;
            int sliceWidth;
            for (IntIterator widthIterator = slices(areaWidth, sourceWidth); widthIterator.hasNext(); currentX += sliceWidth) {
                sliceWidth = widthIterator.nextInt();
                int uPadding = (sourceWidth - sliceWidth) / 2;
                int currentY = startY;
                int sliceHeight;
                for (IntIterator heightIterator = slices(areaHeight, sourceHeight);
                     heightIterator.hasNext();
                     currentY += sliceHeight) {
                    sliceHeight = heightIterator.nextInt();
                    int vPadding = (sourceHeight - sliceHeight) / 2;
                    blit(texture, currentX, currentY, sliceWidth, sliceHeight,
                            textureX + uPadding, textureY + vPadding,
                            sliceWidth, sliceHeight, TEXTURE_SIZE, TEXTURE_SIZE);
                }
            }
        }

        private static IntIterator slices(int totalLength, int sliceLength) {
            int count = (totalLength + sliceLength - 1) / sliceLength;
            return new Divisor(totalLength, count);
        }

        private void blit(
                ResourceLocation texture,
                int x,
                int y,
                int textureX,
                int textureY,
                int width,
                int height
        ) {
            blit(texture, x, y, width, height, textureX, textureY, width, height, TEXTURE_SIZE, TEXTURE_SIZE);
        }

        private void blit(
                ResourceLocation texture,
                int x,
                int y,
                int width,
                int height,
                float textureX,
                float textureY,
                int sourceWidth,
                int sourceHeight,
                int textureWidth,
                int textureHeight
        ) {
            innerBlit(
                    texture,
                    x,
                    x + width,
                    y,
                    y + height,
                    sourceWidth,
                    sourceHeight,
                    textureX,
                    textureY,
                    textureWidth,
                    textureHeight
            );
        }

        private void innerBlit(
                ResourceLocation texture,
                int x1,
                int x2,
                int y1,
                int y2,
                int sourceWidth,
                int sourceHeight,
                float textureX,
                float textureY,
                int textureWidth,
                int textureHeight
        ) {
            RenderSystem.setShaderTexture(0, texture);
            RenderSystem.setShader(GameRenderer::getPositionColorTexLightmapShader);
            Matrix4f matrix = poseStack.last().pose();
            float minU = textureX / textureWidth;
            float maxU = (textureX + sourceWidth) / textureWidth;
            float minV = textureY / textureHeight;
            float maxV = (textureY + sourceHeight) / textureHeight;

            BufferBuilder buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
            buffer.vertex(matrix, x1, y1, 0).color(0xFFFFFFFF).uv(minU, minV).uv2(packedLight).endVertex();
            buffer.vertex(matrix, x1, y2, 0).color(0xFFFFFFFF).uv(minU, maxV).uv2(packedLight).endVertex();
            buffer.vertex(matrix, x2, y2, 0).color(0xFFFFFFFF).uv(maxU, maxV).uv2(packedLight).endVertex();
            buffer.vertex(matrix, x2, y1, 0).color(0xFFFFFFFF).uv(maxU, minV).uv2(packedLight).endVertex();
            BufferUploader.drawWithShader(buffer.end());
        }
    }
}
