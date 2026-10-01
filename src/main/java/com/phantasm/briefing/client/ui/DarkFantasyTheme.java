package com.phantasm.briefing.client.ui;

/**
 * Shared obsidian-black dark-fantasy palette for player-facing quest, handbook, tracker and dialogue UI.
 * The palette stays warm-dominant, with restrained mist-steel blue-gray reserved for completed,
 * inactive and secondary decoration so dense screens have cooler visual rest areas.
 */
public final class DarkFantasyTheme {
    public static final int BACKDROP = 0xC008090C;
    public static final int BACKGROUND = 0xF40A0B0E;
    public static final int PANEL = 0xEC0F1115;
    public static final int PANEL_RAISED = 0xF013151A;
    public static final int PANEL_HOVER = 0xE3191C22;
    public static final int PANEL_SELECTED = 0xF01B1E24;
    public static final int PANEL_COMPLETED = 0x9413171D;
    public static final int PANEL_INACTIVE = 0x70111419;

    public static final int EDGE = 0xFF60472D;
    public static final int EDGE_SOFT = 0x9A617184;
    public static final int EDGE_FAINT = 0x5E4B5969;
    public static final int BRASS = 0xFFB9894D;
    public static final int BRASS_DIM = 0xFF805F3B;
    public static final int GOLD = 0xFFC9A05F;
    public static final int GOLD_SOFT = 0xFFAB804A;
    public static final int AMBER = 0xFFD68D3E;
    public static final int AMBER_BRIGHT = 0xFFE2A553;
    public static final int AMBER_DARK = 0xFF76502F;
    public static final int WARM_RED = 0xFFB96652;

    // Restrained cool accents: use for completed/inactive states and secondary structure only.
    public static final int STEEL = 0xFF66788C;
    public static final int STEEL_SOFT = 0xAA617184;
    public static final int STEEL_FAINT = 0x66505E6E;
    public static final int STEEL_DARK = 0xFF3E4B5B;

    public static final int TEXT_PRIMARY = 0xFFF0E4D0;
    public static final int TEXT_SECONDARY = 0xFFC9C0B3;
    public static final int TEXT_MUTED = 0xFF87919D;
    public static final int TEXT_COMPLETED = 0xC0919EAC;
    public static final int TEXT_TITLE = 0xFFD2AA68;

    public static final int STATUS_ACTIVE = 0xFFC99A58;
    public static final int STATUS_AVAILABLE = 0xFFAE824B;
    public static final int STATUS_READY = 0xFFD08B4B;
    public static final int STATUS_COMPLETED = 0xFF66788C;
    public static final int STATUS_INACTIVE = 0xFF626C78;

    public static final int BUTTON = 0xFF15171C;
    public static final int BUTTON_HOVER = 0xFF20242B;
    public static final int BUTTON_BORDER = 0xFF71563A;
    public static final int BUTTON_TEXT = 0xFFBFC4C9;
    public static final int BUTTON_TEXT_HOVER = 0xFFE9DCC4;

    public static final int DIALOGUE_BACKGROUND = 0xED0D1015;
    public static final int DIALOGUE_HOVER = 0xEE1A1E26;
    public static final int DIALOGUE_ACCENT = GOLD_SOFT;
    public static final int DIALOGUE_TITLE = GOLD;
    public static final int DIALOGUE_TEXT = TEXT_PRIMARY;
    public static final int DIALOGUE_MUTED = TEXT_MUTED;

    public static final int TRACKER_BACKGROUND = 0xC8101216;
    public static final int TRACKER_TOP_EDGE = 0x88687889;
    public static final int TRACKER_ACCENT = 0xAFC2985B;
    public static final int PROMPT_BACKGROUND = 0xE5121418;
    public static final int WORLD_MARKER = 0xD0E79A3E;

    public static final int MANUAL_HEADER_LINE = 0x8FB9894D;
    public static final int MANUAL_DIVIDER = 0x8C627184;
    public static final int MANUAL_SELECTED = 0xBB1A1F27;
    public static final int MANUAL_HOVER = 0x77171B21;
    public static final int MANUAL_TEXT_PANEL = 0xCC121419;

    /** Subtle additive overlay used for the journal's lower-left magic-circle watermark. */
    public static final int MAGIC_CIRCLE_OVERLAY = 0x3E5C7188;

    private DarkFantasyTheme() {
    }
}
