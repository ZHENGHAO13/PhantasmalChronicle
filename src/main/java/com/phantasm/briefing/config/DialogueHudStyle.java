package com.phantasm.briefing.config;

public enum DialogueHudStyle {
    DIALOGUE_BAR,
    PORTRAIT_BUBBLE,
    MAID_WORLD_BUBBLE;

    public DialogueHudStyle next() {
        DialogueHudStyle[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }
}
