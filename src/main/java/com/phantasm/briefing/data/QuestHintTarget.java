package com.phantasm.briefing.data;

public record QuestHintTarget(
        int entityId,
        QuestHintType type
) {
    public static final QuestHintTarget NONE = new QuestHintTarget(-1, QuestHintType.NONE);
}
