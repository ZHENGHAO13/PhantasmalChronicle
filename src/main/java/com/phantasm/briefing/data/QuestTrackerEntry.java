package com.phantasm.briefing.data;

import java.util.List;

public record QuestTrackerEntry(
        String questId,
        String title,
        List<QuestTrackerPhaseEntry> phases,
        QuestMarkerSpec marker
) {
}
