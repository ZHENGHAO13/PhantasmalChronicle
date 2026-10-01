package com.phantasm.briefing.data;

import java.util.List;

public record QuestTreeSnapshot(
        List<QuestTreeNodeEntry> nodes,
        List<QuestTreeEdgeEntry> edges
) {
}
