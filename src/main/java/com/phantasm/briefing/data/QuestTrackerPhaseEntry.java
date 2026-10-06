package com.phantasm.briefing.data;

import java.util.List;

public record QuestTrackerPhaseEntry(
        String title,
        List<QuestTrackerObjectiveEntry> objectives
) {
}
