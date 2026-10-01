package com.phantasm.briefing.data;

public enum QuestHintType {
    NONE,
    AVAILABLE,
    ADVANCE,
    COMPLETE;

    public int priority() {
        return switch (this) {
            case COMPLETE -> 3;
            case AVAILABLE -> 2;
            case ADVANCE -> 1;
            case NONE -> 0;
        };
    }
}
