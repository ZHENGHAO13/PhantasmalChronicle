package com.phantasm.briefing.client.ui;

import net.minecraft.client.gui.GuiGraphics;

/** Shared, text-free pixel ornaments for quest pages and handbook pages. */
public final class JournalOrnaments {
    public static final int ACCENT_BRASS = DarkFantasyTheme.BRASS;
    public static final int ACCENT_GOLD = DarkFantasyTheme.GOLD;
    public static final int ACCENT_FINISHED = DarkFantasyTheme.STATUS_COMPLETED;
    public static final int ACCENT_ORANGE = DarkFantasyTheme.AMBER;
    public static final int ACCENT_ACTIVE = DarkFantasyTheme.STATUS_ACTIVE;
    public static final int ACCENT_COMPLETED = DarkFantasyTheme.STATUS_COMPLETED;
    public static final int ACCENT_MUTED = DarkFantasyTheme.STATUS_INACTIVE;
    public static final int TEXT_ACTIVE = DarkFantasyTheme.TEXT_PRIMARY;
    public static final int TEXT_COMPLETED = DarkFantasyTheme.TEXT_COMPLETED;
    public static final int TEXT_MUTED = DarkFantasyTheme.TEXT_MUTED;
    private static final int EDGE = DarkFantasyTheme.EDGE;
    private static final int PANEL = DarkFantasyTheme.PANEL;

    private JournalOrnaments() {}

    public enum StatusTone { ACTIVE, AVAILABLE, COMPLETED, INACTIVE }

    public static int statusAccent(StatusTone tone) {
        return switch (tone) {
            case ACTIVE -> ACCENT_ACTIVE;
            case AVAILABLE -> ACCENT_GOLD;
            case COMPLETED -> ACCENT_COMPLETED;
            case INACTIVE -> ACCENT_MUTED;
        };
    }

    public static int statusText(StatusTone tone) {
        return switch (tone) {
            case ACTIVE -> TEXT_ACTIVE;
            case AVAILABLE -> DarkFantasyTheme.TEXT_SECONDARY;
            case COMPLETED -> TEXT_COMPLETED;
            case INACTIVE -> TEXT_MUTED;
        };
    }

    /** Status-aware header used by the quest journal. Completed sections are intentionally quiet. */
    public static void statusHeader(GuiGraphics g, int x, int y, int w, int h, StatusTone tone) {
        if (w < 9 || h < 8) return;
        int accent = statusAccent(tone);
        int fill = switch (tone) {
            case ACTIVE -> 0xF01C1E24;
            case AVAILABLE -> 0xD0181A1F;
            case COMPLETED -> DarkFantasyTheme.PANEL_COMPLETED;
            case INACTIVE -> DarkFantasyTheme.PANEL_INACTIVE;
        };
        int line = switch (tone) {
            case ACTIVE -> 0xCCD79B46;
            case AVAILABLE -> 0x99C58E43;
            case COMPLETED -> DarkFantasyTheme.STEEL_SOFT;
            case INACTIVE -> DarkFantasyTheme.STEEL_FAINT;
        };
        g.fill(x + 4, y, x + w - 4, y + h, fill);
        g.fill(x + 5, y + 2, x + w - 5, y + 3, line);
        g.fill(x + 5, y + h - 2, x + w - 5, y + h - 1, line);
        diamond(g, x + 4, y + h / 2, tone == StatusTone.ACTIVE ? 4 : 3, accent);
        diamond(g, x + w - 4, y + h / 2, tone == StatusTone.ACTIVE ? 4 : 3, accent);
        if (tone == StatusTone.ACTIVE) {
            g.fill(x + 11, y + h - 1, x + w - 11, y + h, 0xAAD79B46);
        }
    }

    /** Quest-list card with stronger active emphasis and subdued completed styling. */
    public static void statusCard(GuiGraphics g, int x, int y, int w, int h, StatusTone tone,
                                  boolean selected, boolean hovered, boolean endDiamond) {
        if (w < 5 || h < 5) return;
        int accent = statusAccent(tone);
        int fill = switch (tone) {
            case ACTIVE -> selected ? DarkFantasyTheme.PANEL_SELECTED : hovered ? DarkFantasyTheme.PANEL_HOVER : 0xC016181D;
            case AVAILABLE -> selected ? 0xDC202329 : hovered ? 0xCC1B1E24 : 0x9817191E;
            case COMPLETED -> hovered ? 0xA11B1D22 : selected ? 0x8D191B20 : DarkFantasyTheme.PANEL_COMPLETED;
            case INACTIVE -> hovered ? 0x8617191D : DarkFantasyTheme.PANEL_INACTIVE;
        };
        g.fill(x, y, x + w, y + h, fill);
        int barWidth = tone == StatusTone.ACTIVE && selected ? 3 : 2;
        g.fill(x + 2, y + 2, x + 2 + barWidth, y + h - 2, accent);
        if (selected || hovered) {
            int borderColor = tone == StatusTone.COMPLETED ? DarkFantasyTheme.STEEL_SOFT : accent;
            border(g, x, y, w, h, borderColor);
            if (tone != StatusTone.COMPLETED) corners(g, x, y, w, h, accent);
        }
        if (tone == StatusTone.ACTIVE && selected) {
            g.fill(x + 6, y + h - 2, x + w - 17, y + h - 1, 0x88D79B46);
        }
        if (endDiamond) {
            diamond(g, x + w - 9, y + h / 2, tone == StatusTone.ACTIVE ? 3 : 2,
                    selected && tone == StatusTone.ACTIVE ? accent : tone == StatusTone.COMPLETED ? DarkFantasyTheme.STEEL_DARK : accent);
        }
    }

    public static void frame(GuiGraphics g, int x, int y, int w, int h) {
        if (w < 4 || h < 4) return;
        g.fill(x, y, x + w, y + h, DarkFantasyTheme.BACKGROUND);
        g.fill(x + 2, y + 2, x + w - 2, y + 33, 0xD015181E);
        border(g, x, y, w, h, EDGE);
        border(g, x + 3, y + 3, w - 6, h - 6, DarkFantasyTheme.EDGE_FAINT);
        corners(g, x + 1, y + 1, w - 2, h - 2, ACCENT_BRASS);
        g.fill(x + 14, y + 35, x + w - 14, y + 36, DarkFantasyTheme.BRASS_DIM);
    }

    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        if (w < 4 || h < 4) return;
        g.fill(x, y, x + w, y + h, PANEL);
        border(g, x, y, w, h, DarkFantasyTheme.EDGE_SOFT);
        corners(g, x, y, w, h, DarkFantasyTheme.BRASS_DIM);
    }

    public static void headerPlaque(GuiGraphics g, int x, int y, int w, int h, int accent) {
        headerPlaque(g, x, y, w, h, accent, false);
    }

    /** Dim the entire plaque, not just its diamonds, for phases that have not begun. */
    public static void headerPlaque(GuiGraphics g, int x, int y, int w, int h, int accent, boolean inactive) {
        if (w < 9 || h < 8) return;
        g.fill(x + 4, y, x + w - 4, y + h, inactive ? 0xD916181C : 0xEC1B1D22);
        g.fill(x + 5, y + 2, x + w - 5, y + 3, inactive ? DarkFantasyTheme.STEEL_FAINT : 0x9A9B7448);
        g.fill(x + 5, y + h - 2, x + w - 5, y + h - 1, inactive ? DarkFantasyTheme.STEEL_FAINT : 0x8A70513A);
        diamond(g, x + 4, y + h / 2, 3, inactive ? ACCENT_MUTED : accent);
        diamond(g, x + w - 4, y + h / 2, 3, inactive ? ACCENT_MUTED : accent);
    }

    public static void questCard(GuiGraphics g, int x, int y, int w, int h,
                                 boolean selected, boolean hovered, boolean completed) {
        questCard(g, x, y, w, h, selected, hovered, completed, false);
    }

    public static void questCard(GuiGraphics g, int x, int y, int w, int h,
                                 boolean selected, boolean hovered, boolean completed, boolean inactive) {
        questCard(g, x, y, w, h, selected, hovered, completed, inactive, true);
    }

    public static void questCard(GuiGraphics g, int x, int y, int w, int h,
                                 boolean selected, boolean hovered, boolean completed, boolean inactive,
                                 boolean endDiamond) {
        if (w < 5 || h < 5) return;
        int accent = inactive ? ACCENT_MUTED : completed ? ACCENT_FINISHED : ACCENT_BRASS;
        g.fill(x, y, x + w, y + h, inactive ? (hovered ? 0xD0191B20 : 0x77131519)
                : selected ? DarkFantasyTheme.PANEL_SELECTED : hovered ? DarkFantasyTheme.PANEL_HOVER : 0x8A15171B);
        g.fill(x + 2, y + 2, x + 4, y + h - 2, accent);
        if (selected || hovered || inactive) {
            border(g, x, y, w, h, inactive ? ACCENT_MUTED : selected ? accent : DarkFantasyTheme.BRASS_DIM);
            corners(g, x, y, w, h, accent);
        }
        if (endDiamond) {
            diamond(g, x + w - 9, y + h / 2, 2, inactive ? ACCENT_MUTED : selected ? accent : DarkFantasyTheme.BRASS_DIM);
        }
    }

    public static void focusDiamond(GuiGraphics g, int x, int y, boolean focused, boolean hovered) {
        if (focused) {
            diamond(g, x, y, 6, ACCENT_ORANGE);
            diamond(g, x, y, 3, 0xFF101114);
            diamond(g, x, y, 1, DarkFantasyTheme.AMBER_BRIGHT);
            int deco = hovered ? DarkFantasyTheme.AMBER_BRIGHT : DarkFantasyTheme.AMBER_DARK;
            g.fill(x - 9, y, x - 7, y + 1, deco);
            g.fill(x + 8, y, x + 10, y + 1, deco);
            g.fill(x, y - 9, x + 1, y - 7, deco);
            g.fill(x, y + 8, x + 1, y + 10, deco);
        } else {
            diamond(g, x, y, hovered ? 4 : 3, hovered ? DarkFantasyTheme.GOLD : ACCENT_BRASS);
            diamond(g, x, y, 1, 0xFF0D0F12);
        }
    }

    public static void divider(GuiGraphics g, int x, int y1, int y2) {
        if (y2 <= y1) return;
        g.fill(x, y1, x + 1, y2, DarkFantasyTheme.EDGE);
        g.fill(x + 2, y1 + 3, x + 3, y2 - 3, DarkFantasyTheme.STEEL_FAINT);
    }

    public static void phaseNode(GuiGraphics g, int x, int y, boolean completed, boolean current) {
        int color = current ? ACCENT_ACTIVE : completed ? ACCENT_COMPLETED : ACCENT_MUTED;
        diamond(g, x, y, current ? 5 : 4, color);
        diamond(g, x, y, 2, !completed && !current ? 0xFF181A1F : 0xFF101215);
        if (completed) g.fill(x - 1, y, x + 2, y + 1, 0xFF9AA7B5);
    }

    public static void bookIcon(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y + 1, x + 6, y + 9, color);
        g.fill(x + 8, y + 1, x + 14, y + 9, color);
        g.fill(x + 6, y + 2, x + 8, y + 11, color);
        g.fill(x + 1, y + 2, x + 5, y + 8, 0xFF101215);
        g.fill(x + 9, y + 2, x + 13, y + 8, 0xFF101215);
        g.fill(x + 1, y + 10, x + 6, y + 11, color);
        g.fill(x + 8, y + 10, x + 13, y + 11, color);
    }

    public static void corners(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w < 8 || h < 8) return;
        int arm = Math.min(8, Math.min(w, h) / 3);
        g.fill(x, y, x + arm, y + 1, color);
        g.fill(x, y, x + 1, y + arm, color);
        g.fill(x + w - arm, y, x + w, y + 1, color);
        g.fill(x + w - 1, y, x + w, y + arm, color);
        g.fill(x, y + h - 1, x + arm, y + h, color);
        g.fill(x, y + h - arm, x + 1, y + h, color);
        g.fill(x + w - arm, y + h - 1, x + w, y + h, color);
        g.fill(x + w - 1, y + h - arm, x + w, y + h, color);
    }

    public static void diamond(GuiGraphics g, int x, int y, int r, int color) {
        for (int row = -r; row <= r; row++) {
            int half = r - Math.abs(row);
            g.fill(x - half, y + row, x + half + 1, y + row + 1, color);
        }
    }

    private static void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }
}
