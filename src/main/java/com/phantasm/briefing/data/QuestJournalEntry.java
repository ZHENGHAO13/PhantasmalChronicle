package com.phantasm.briefing.data;

import java.util.List;

public record QuestJournalEntry(
        String questId,
        String title,
        String description,
        List<String> objectiveLines,
        QuestRuntimeStatus status
) {
}
