package com.phantasm.briefing.data;

import java.util.List;

public record QuestTreeNodeEntry(
        String questId,
        String title,
        String description,
        QuestRuntimeStatus status,
        int x,
        int y,
        List<String> objectiveLines,
        List<String> parentQuestIds,
        List<String> nextQuestIds
) {
}
