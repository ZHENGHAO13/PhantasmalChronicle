package com.phantasm.briefing.client;

import com.phantasm.briefing.PhantasmBriefing;
import com.phantasm.briefing.config.PhantasmBriefingClientConfig;
import com.phantasm.briefing.data.QuestHintType;
import com.phantasm.briefing.data.QuestMarkerSpec;
import com.phantasm.briefing.data.QuestTrackerEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = PhantasmBriefing.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class QuestTrackerClientEvents {
    private QuestTrackerClientEvents() {
    }

    @SubscribeEvent
    public static void onRenderHud(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null || minecraft.screen != null) {
            return;
        }
        if (!PhantasmBriefingClientConfig.questTrackingEnabled()) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        QuestTrackerEntry trackedEntry = QuestTrackerClientState.getTrackedEntry();
        if (QuestTrackerClientState.isTrackerHudEnabled() && trackedEntry != null) {
            if (PhantasmBriefingClientConfig.questTrackerPanelEnabled()) {
                renderTrackerPanel(guiGraphics, minecraft, trackedEntry, event.getWindow().getGuiScaledWidth());
            }
            if (PhantasmBriefingClientConfig.worldMarkerEnabled()) {
                renderMarker(guiGraphics, minecraft, trackedEntry.marker(), event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight());
            }
        }

        if (PhantasmBriefingClientConfig.npcIndicatorEnabled()) {
            renderQuestNpcIndicator(guiGraphics, minecraft, event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight());
        }
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        QuestTrackerClientState.clear();
    }

    private static void renderTrackerPanel(
            GuiGraphics guiGraphics,
            Minecraft minecraft,
            QuestTrackerEntry trackedEntry,
            int screenWidth
    ) {
        Font font = minecraft.font;
        int top = 10;
        int width = 170;
        int left = PhantasmBriefingClientConfig.questTrackerAnchorRight() ? screenWidth - width - 10 : 10;
        int contentWidth = width - 16;
        String trackerTitle = trackedEntry.title();
        List<FormattedCharSequence> titleLines = font.split(Component.literal(trackerTitle), contentWidth);
        List<FormattedCharSequence> objectiveLines = new ArrayList<>();
        if (trackedEntry.objectiveLines().isEmpty()) {
            objectiveLines.addAll(font.split(Component.literal("暂无任务目标。"), contentWidth));
        } else {
            int objectiveCount = PhantasmBriefingClientConfig.questTrackerShowAllObjectives()
                    ? trackedEntry.objectiveLines().size()
                    : 1;
            for (int i = 0; i < objectiveCount; i++) {
                String prefix = objectiveCount > 1 ? "• " : "";
                objectiveLines.addAll(font.split(Component.literal(prefix + trackedEntry.objectiveLines().get(i)), contentWidth));
            }
        }
        int lineHeight = font.lineHeight;
        int height = 8
                + (titleLines.size() * lineHeight)
                + 4
                + (objectiveLines.size() * lineHeight)
                + 8;
        guiGraphics.fill(left, top, left + width, top + height, 0xB012171F);
        guiGraphics.fill(left, top, left + width, top + 1, 0x6634465C);
        guiGraphics.fill(left, top, left + 2, top + height, 0x8A4D7CA9);
        int lineY = top + 7;
        for (FormattedCharSequence titleLine : titleLines) {
            guiGraphics.drawString(font, titleLine, left + 8, lineY, 0xDCECFF, true);
            lineY += lineHeight;
        }
        lineY += 4;
        for (FormattedCharSequence objectiveLine : objectiveLines) {
            guiGraphics.drawString(font, objectiveLine, left + 8, lineY, 0x9FB4D1, true);
            lineY += lineHeight;
        }
    }

    private static void renderMarker(
            GuiGraphics guiGraphics,
            Minecraft minecraft,
            QuestMarkerSpec marker,
            int screenWidth,
            int screenHeight
    ) {
        if (marker == null || minecraft.player == null || minecraft.level == null) {
            return;
        }
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        Font font = minecraft.font;
        ResourceLocation playerDimension = level.dimension().location();
        if (!playerDimension.toString().equals(marker.dimension())) {
            guiGraphics.drawString(font, "目标位于其他维度: " + marker.label(), screenWidth / 2 - 70, 12, 0xE2C27A, true);
            return;
        }

        double dx = marker.x() - player.getX();
        double dz = marker.z() - player.getZ();
        double distance = Math.sqrt((dx * dx) + (dz * dz));
        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float yawDelta = Mth.wrapDegrees(targetYaw - player.getYRot());
        int centerX = screenWidth / 2;
        int markerX = centerX + Mth.clamp((int) (yawDelta * 2.0F), -120, 120);
        int markerY = 12;

        guiGraphics.fill(markerX - 2, markerY, markerX + 2, markerY + 8, 0xCC6FB9FF);
        String markerText = marker.label().isBlank() ? "任务目标" : marker.label();
        if (PhantasmBriefingClientConfig.markerDistanceEnabled()) {
            markerText += " " + String.format("%.0fm", distance);
        }
        guiGraphics.drawString(
                font,
                markerText,
                markerX - 28,
                markerY + 10,
                0xDCECFF,
                true
        );
    }

    private static void renderQuestNpcIndicator(GuiGraphics guiGraphics, Minecraft minecraft, int screenWidth, int screenHeight) {
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        Font font = minecraft.font;
        Entity entity = level.getEntity(QuestTrackerClientState.getHintEntityId());
        QuestHintType hintType = QuestTrackerClientState.getHintType();
        if (entity == null || !entity.isAlive() || hintType == QuestHintType.NONE) {
            return;
        }
        double dx = entity.getX() - player.getX();
        double dz = entity.getZ() - player.getZ();
        double distance = Math.sqrt((dx * dx) + (dz * dz));
        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float yawDelta = Mth.wrapDegrees(targetYaw - player.getYRot());

        int centerX = screenWidth / 2;
        int iconX = centerX + Mth.clamp((int) (yawDelta * 2.4F), -150, 150);
        int iconY = 36;
        int accentColor = switch (hintType) {
            case AVAILABLE -> 0xFFF4C24E;
            case ADVANCE -> 0xFF7FD6FF;
            case COMPLETE -> 0xFF8FF59A;
            case NONE -> 0xFFF4C24E;
        };
        String label = switch (hintType) {
            case AVAILABLE -> "可接";
            case ADVANCE -> "推进";
            case COMPLETE -> "可交";
            case NONE -> "";
        };
        int shadowColor = 0x66000000;
        int textColor = (accentColor & 0x00FFFFFF) | 0xEE000000;
        int highlightColor = brighten(accentColor, 28);
        int iconTop = iconY - 2;

        renderHintIcon(guiGraphics, hintType, iconX, iconTop, accentColor, highlightColor, shadowColor);
        guiGraphics.fill(iconX - 7, iconY + 11, iconX + 7, iconY + 12, (accentColor & 0x00FFFFFF) | 0x66000000);
        String indicatorText = PhantasmBriefingClientConfig.markerDistanceEnabled()
                ? label + " " + String.format("%.0fm", distance)
                : label;
        guiGraphics.drawCenteredString(
                font,
                indicatorText,
                iconX,
                iconY + 12,
                textColor
        );
    }

    private static void renderHintIcon(
            GuiGraphics guiGraphics,
            QuestHintType hintType,
            int centerX,
            int topY,
            int accentColor,
            int highlightColor,
            int shadowColor
    ) {
        String[] pattern = switch (hintType) {
            case AVAILABLE -> new String[]{
                    "..+#+..",
                    ".+###+.",
                    "+#####+" ,
                    "#######",
                    "+#####+" ,
                    ".+###+.",
                    "..+#+.."
            };
            case ADVANCE -> new String[]{
                    "...#...",
                    "..##+..",
                    ".#####.",
                    "##+#+##",
                    "...#...",
                    "...#...",
                    "..+#+.."
            };
            case COMPLETE -> new String[]{
                    "......+",
                    ".....+#",
                    "#...+##",
                    "##.+##.",
                    ".####..",
                    "..##...",
                    "...#..."
            };
            case NONE -> new String[0];
        };
        drawPixelPattern(guiGraphics, pattern, centerX - 7, topY, accentColor, highlightColor, shadowColor, 2);
    }

    private static void drawPixelPattern(
            GuiGraphics guiGraphics,
            String[] rows,
            int startX,
            int startY,
            int accentColor,
            int highlightColor,
            int shadowColor,
            int scale
    ) {
        for (int row = 0; row < rows.length; row++) {
            String line = rows[row];
            for (int col = 0; col < line.length(); col++) {
                char pixel = line.charAt(col);
                if (pixel == '.') {
                    continue;
                }
                int color = pixel == '+' ? highlightColor : accentColor;
                int drawX = startX + (col * scale);
                int drawY = startY + (row * scale);
                guiGraphics.fill(drawX + 1, drawY + 1, drawX + scale + 1, drawY + scale + 1, shadowColor);
                guiGraphics.fill(drawX, drawY, drawX + scale, drawY + scale, color);
            }
        }
    }

    private static int brighten(int color, int amount) {
        int alpha = (color >>> 24) & 0xFF;
        int red = Math.min(255, ((color >>> 16) & 0xFF) + amount);
        int green = Math.min(255, ((color >>> 8) & 0xFF) + amount);
        int blue = Math.min(255, (color & 0xFF) + amount);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
}
