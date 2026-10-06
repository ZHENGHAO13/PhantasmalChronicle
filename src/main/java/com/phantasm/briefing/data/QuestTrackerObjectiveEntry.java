package com.phantasm.briefing.data;

import java.util.List;

public record QuestTrackerObjectiveEntry(
        String title,
        List<String> trackingLines,
        String waitingStructureLabel,
        boolean completed,
        boolean active
) {
}
