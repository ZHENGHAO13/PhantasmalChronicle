package com.phantasm.briefing.data;

import java.util.List;

public record QuestTrackerEntry(
        String questId,
        String title,
        List<String> objectiveLines,
        QuestMarkerSpec marker
) {
}
