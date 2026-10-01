package com.phantasm.briefing.data;

public enum QuestRuntimeStatus {
    LOCKED,
    AVAILABLE,
    ACTIVE,
    READY_TO_TURN_IN,
    COMPLETED;

    public int priority() {
        return switch (this) {
            case READY_TO_TURN_IN -> 4;
            case ACTIVE -> 3;
            case AVAILABLE -> 2;
            case COMPLETED -> 1;
            case LOCKED -> 0;
        };
    }

    public String label() {
        return switch (this) {
            case AVAILABLE -> "可接";
            case ACTIVE -> "进行中";
            case READY_TO_TURN_IN -> "可交";
            case COMPLETED -> "已完成";
            case LOCKED -> "未解锁";
        };
    }
}
