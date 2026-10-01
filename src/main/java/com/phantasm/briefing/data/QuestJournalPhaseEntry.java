package com.phantasm.briefing.data;

import java.util.List;

public record QuestJournalPhaseEntry(
        String phaseId,
        String title,
        List<QuestJournalObjectiveEntry> objectives,
        boolean completed,
        boolean current
) {
}
