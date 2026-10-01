package com.phantasm.briefing.data;

import java.util.List;

public record QuestJournalEntry(
        String questId,
        String title,
        String description,
        List<QuestJournalPhaseEntry> phases,
        QuestRuntimeStatus status,
        boolean tracked,
        boolean recommended,
        List<ManualReferenceSpec> manualRefs
) {
}
