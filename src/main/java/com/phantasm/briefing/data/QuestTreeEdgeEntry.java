package com.phantasm.briefing.data;

public record QuestTreeEdgeEntry(
        String fromQuestId,
        String toQuestId,
        QuestTreeEdgeType edgeType
) {
}
