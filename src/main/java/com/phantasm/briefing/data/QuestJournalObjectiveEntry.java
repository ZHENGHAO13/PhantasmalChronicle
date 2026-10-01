package com.phantasm.briefing.data;

import java.util.List;

public record QuestJournalObjectiveEntry(
        String objectiveId,
        String title,
        QuestObjectiveType objectiveType,
        int progress,
        int requiredCount,
        boolean completed,
        boolean manualReadable,
        List<ManualReferenceSpec> manualRefs
) {
    public QuestJournalObjectiveEntry {
        manualRefs = manualRefs == null ? List.of() : List.copyOf(manualRefs);
    }
}
