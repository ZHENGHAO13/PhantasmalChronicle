package com.phantasm.briefing.client;

import com.phantasm.briefing.client.ui.DarkFantasyTheme;

import com.phantasm.briefing.PhantasmBriefing;
import com.phantasm.briefing.config.PhantasmBriefingClientConfig;
import com.phantasm.briefing.data.QuestHintTarget;
import com.phantasm.briefing.data.QuestHintType;
import com.phantasm.briefing.data.QuestMarkerSpec;
import com.phantasm.briefing.data.QuestTrackerEntry;
import com.phantasm.briefing.data.QuestTrackerObjectiveEntry;
import com.phantasm.briefing.data.QuestTrackerPhaseEntry;
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
        GuiGraphics guiGraphics = event.getGuiGraphics();
        if (QuestTrackerClientState.getManualReadPrompt() != null) {
            renderManualReadPrompt(guiGraphics, minecraft, event.getWindow().getGuiScaledWidth());
        }
        if (!PhantasmBriefingClientConfig.questTrackingEnabled()) {
            return;
        }

        QuestTrackerEntry trackedEntry = QuestTrackerClientState.getTrackedEntry();
        if (QuestTrackerClientState.isTrackerHudEnabled() && trackedEntry != null) {
            renderTrackerPanel(guiGraphics, minecraft, trackedEntry, event.getWindow().getGuiScaledWidth());
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
        ContentTextureCache.clear();
        QuestTrackerClientState.clear();
    }

    private static void renderTrackerPanel(
            GuiGraphics guiGraphics,
            Minecraft minecraft,
            QuestTrackerEntry trackedEntry,
            int screenWidth
    ) {
        Font font = minecraft.font;
        int top = 8;
        int width = 150;
        int horizontalPadding = 6;
        int contentWidth = width - (horizontalPadding * 2);
        int left = PhantasmBriefingClientConfig.questTrackerAnchorRight() ? screenWidth - width - 8 : 8;
        boolean showTrackingText = PhantasmBriefingClientConfig.questTrackerShowObjectiveTrackingText();
        List<TrackerRenderLine> lines = new ArrayList<>();

        addTrackerLines(
                lines,
                font,
                Component.literal(trackedEntry.title()),
                contentWidth,
                DarkFantasyTheme.TEXT_TITLE,
                0,
                0
        );

        boolean hasObjectives = false;
        for (QuestTrackerPhaseEntry phase : trackedEntry.phases()) {
            if (!phase.objectives().isEmpty()) {
                hasObjectives = true;
            }
            addTrackerLines(
                    lines,
                    font,
                    Component.literal(phase.title()),
                    contentWidth,
                    DarkFantasyTheme.TEXT_PRIMARY,
                    0,
                    1
            );

            for (QuestTrackerObjectiveEntry objective : phase.objectives()) {
                int objectiveColor = objective.completed()
                        ? DarkFantasyTheme.TEXT_COMPLETED
                        : objective.active() ? DarkFantasyTheme.TEXT_SECONDARY : DarkFantasyTheme.TEXT_MUTED;
                addObjectiveTrackerLines(
                        lines,
                        font,
                        objective.completed() ? "✓" : "○",
                        objective.title(),
                        contentWidth,
                        objectiveColor,
                        0
                );

                if (!showTrackingText) {
                    continue;
                }
                int trackingColor = objective.active()
                        ? DarkFantasyTheme.TEXT_MUTED
                        : objectiveColor;
                for (String objectiveTrackingLine : objective.trackingLines()) {
                    addTrackerLines(
                            lines,
                            font,
                            Component.literal(objectiveTrackingLine),
                            contentWidth,
                            trackingColor,
                            8,
                            0
                    );
                }
                if (!objective.waitingStructureLabel().isBlank()) {
                    addTrackerLines(
                            lines,
                            font,
                            Component.translatable(
                                    "hud.phantasmbriefing.waiting_structure",
                                    objective.waitingStructureLabel()
                            ),
                            contentWidth,
                            trackingColor,
                            8,
                            0
                    );
                }
            }
        }

        if (!hasObjectives) {
            addTrackerLines(
                    lines,
                    font,
                    Component.translatable("hud.phantasmbriefing.no_objectives"),
                    contentWidth,
                    DarkFantasyTheme.TEXT_MUTED,
                    0,
                    2
            );
        }

        int lineHeight = font.lineHeight;
        int height = 4 + lines.stream().mapToInt(line -> lineHeight + line.gapBefore()).sum() + 4;
        guiGraphics.fill(left, top, left + width, top + height, DarkFantasyTheme.TRACKER_BACKGROUND);
        guiGraphics.fill(left, top, left + width, top + 1, DarkFantasyTheme.TRACKER_TOP_EDGE);
        guiGraphics.fill(left, top, left + 1, top + height, DarkFantasyTheme.TRACKER_ACCENT);

        int lineY = top + 4;
        for (TrackerRenderLine line : lines) {
            lineY += line.gapBefore();
            int lineX = left + horizontalPadding + line.indent();
            if (!line.prefix().isEmpty()) {
                guiGraphics.drawString(font, line.prefix(), lineX, lineY, line.color(), true);
            }
            guiGraphics.drawString(
                    font,
                    line.text(),
                    lineX + line.textOffset(),
                    lineY,
                    line.color(),
                    true
            );
            lineY += lineHeight;
        }
    }

    private static void addTrackerLines(
            List<TrackerRenderLine> target,
            Font font,
            Component text,
            int contentWidth,
            int color,
            int indent,
            int gapBefore
    ) {
        int wrappedWidth = Math.max(24, contentWidth - indent);
        List<FormattedCharSequence> wrapped = font.split(text, wrappedWidth);
        if (wrapped.isEmpty()) {
            return;
        }
        for (int index = 0; index < wrapped.size(); index++) {
            target.add(new TrackerRenderLine(
                    wrapped.get(index),
                    "",
                    color,
                    indent,
                    0,
                    index == 0 ? gapBefore : 0
            ));
        }
    }

    private static void addObjectiveTrackerLines(
            List<TrackerRenderLine> target,
            Font font,
            String prefix,
            String title,
            int contentWidth,
            int color,
            int gapBefore
    ) {
        String renderedPrefix = prefix + " ";
        int prefixWidth = font.width(renderedPrefix);
        int wrappedWidth = Math.max(24, contentWidth - prefixWidth);
        List<FormattedCharSequence> wrapped = font.split(Component.literal(title), wrappedWidth);
        if (wrapped.isEmpty()) {
            return;
        }
        for (int index = 0; index < wrapped.size(); index++) {
            boolean firstLine = index == 0;
            target.add(new TrackerRenderLine(
                    wrapped.get(index),
                    firstLine ? renderedPrefix : "",
                    color,
                    firstLine ? 0 : prefixWidth,
                    firstLine ? prefixWidth : 0,
                    firstLine ? gapBefore : 0
            ));
        }
    }

    private static void renderManualReadPrompt(GuiGraphics guiGraphics, Minecraft minecraft, int screenWidth) {
        Component prompt = Component.translatable("hud.phantasmbriefing.manual_read_prompt");
        int width = Math.min(screenWidth - 16, minecraft.font.width(prompt) + 20);
        int left = (screenWidth - width) / 2;
        int top = 6;
        guiGraphics.fill(left, top, left + width, top + 20, DarkFantasyTheme.PROMPT_BACKGROUND);
        guiGraphics.fill(left, top, left + width, top + 1, DarkFantasyTheme.GOLD);
        guiGraphics.fill(left, top + 19, left + width, top + 20, DarkFantasyTheme.BRASS_DIM);
        guiGraphics.drawCenteredString(minecraft.font, prompt, screenWidth / 2, top + 6, DarkFantasyTheme.TEXT_PRIMARY);
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
            guiGraphics.drawString(font, Component.translatable("hud.phantasmbriefing.marker_other_dimension", marker.label()),
                    screenWidth / 2 - 70, manualReadHudOffset() + 12, DarkFantasyTheme.GOLD, true);
            return;
        }

        double dx = marker.x() - player.getX();
        double dz = marker.z() - player.getZ();
        double distance = Math.sqrt((dx * dx) + (dz * dz));
        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float yawDelta = Mth.wrapDegrees(targetYaw - player.getYRot());
        int centerX = screenWidth / 2;
        int markerX = centerX + Mth.clamp((int) (yawDelta * 2.0F), -120, 120);
        int markerY = manualReadHudOffset() + 12;

        guiGraphics.fill(markerX - 2, markerY, markerX + 2, markerY + 8, DarkFantasyTheme.WORLD_MARKER);
        Component markerText = marker.label().isBlank()
                ? Component.translatable("hud.phantasmbriefing.marker_default")
                : Component.literal(marker.label());
        if (PhantasmBriefingClientConfig.markerDistanceEnabled()) {
            markerText = markerText.copy().append(Component.literal(" " + String.format("%.0fm", distance)));
        }
        guiGraphics.drawString(
                font,
                markerText,
                markerX - 28,
                markerY + 10,
                DarkFantasyTheme.TEXT_SECONDARY,
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
        int centerX = screenWidth / 2;
        List<HintRenderTarget> renderTargets = new ArrayList<>();
        for (QuestHintTarget target : QuestTrackerClientState.getHintTargets()) {
            Entity entity = level.getEntity(target.entityId());
            if (entity == null || !entity.isAlive() || target.type() == QuestHintType.NONE) {
                continue;
            }
            double dx = entity.getX() - player.getX();
            double dz = entity.getZ() - player.getZ();
            double distance = Math.sqrt((dx * dx) + (dz * dz));
            float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
            float yawDelta = Mth.wrapDegrees(targetYaw - player.getYRot());
            int iconX = centerX + Mth.clamp((int) (yawDelta * 2.4F), -150, 150);
            renderTargets.add(new HintRenderTarget(target.entityId(), target.type(), iconX, distance));
        }
        renderTargets.sort((left, right) -> {
            int byX = Integer.compare(left.x(), right.x());
            return byX != 0 ? byX : Integer.compare(left.entityId(), right.entityId());
        });

        List<PlacedHint> placed = new ArrayList<>();
        for (HintRenderTarget target : renderTargets) {
            int iconY = manualReadHudOffset() + 34;
            while (overlapsPlacedHint(placed, target.x(), iconY)) {
                iconY += 28;
            }
            int accentColor = switch (target.type()) {
                case AVAILABLE -> DarkFantasyTheme.STATUS_AVAILABLE;
                case ADVANCE -> DarkFantasyTheme.STATUS_ACTIVE;
                case COMPLETE -> DarkFantasyTheme.STATUS_READY;
                case NONE -> DarkFantasyTheme.STATUS_AVAILABLE;
            };
            int highlightColor = brighten(accentColor, 28);
            renderHintIcon(guiGraphics, target.type(), target.x(), iconY, accentColor, highlightColor, 0x66000000);
            if (PhantasmBriefingClientConfig.markerDistanceEnabled()) {
                Component distanceText = Component.translatable(
                        "hud.phantasmbriefing.npc_distance",
                        String.format(java.util.Locale.ROOT, "%.0f", target.distance())
                );
                guiGraphics.drawCenteredString(font, distanceText, target.x(), iconY + 16, (accentColor & 0x00FFFFFF) | 0xEE000000);
            }
            placed.add(new PlacedHint(target.x(), iconY));
        }
    }

    private static boolean overlapsPlacedHint(List<PlacedHint> placed, int x, int y) {
        for (PlacedHint existing : placed) {
            if (Math.abs(existing.x() - x) < 28 && Math.abs(existing.y() - y) < 28) {
                return true;
            }
        }
        return false;
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

    private static int manualReadHudOffset() {
        return QuestTrackerClientState.getManualReadPrompt() == null ? 0 : 24;
    }
    private record HintRenderTarget(int entityId, QuestHintType type, int x, double distance) {
    }

    private record PlacedHint(int x, int y) {
    }

    private record TrackerRenderLine(
            FormattedCharSequence text,
            String prefix,
            int color,
            int indent,
            int textOffset,
            int gapBefore
    ) {
    }

}
