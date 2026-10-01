package com.phantasm.briefing.data;

import java.util.ArrayList;
import java.util.List;

/** One node of the handbook outline. Groups contain children; lessons contain pages. */
public record ManualNodeSpec(
        String type,
        String nodeId,
        String title,
        List<ManualNodeSpec> children,
        List<ManualPageSpec> pages,
        ManualUnlockType unlockType,
        String unlockQuestId,
        String unlockPhaseId,
        String unlockObjectiveId
) {
    public ManualNodeSpec {
        children = children == null ? List.of() : List.copyOf(children);
        pages = pages == null ? List.of() : List.copyOf(pages);
        unlockType = unlockType == null ? ManualUnlockType.ALWAYS : unlockType;
        unlockQuestId = unlockQuestId == null ? "" : unlockQuestId.trim();
        unlockPhaseId = unlockPhaseId == null ? "" : unlockPhaseId.trim();
        unlockObjectiveId = unlockObjectiveId == null ? "" : unlockObjectiveId.trim();
    }

    public boolean isGroup() {
        return "group".equals(type);
    }

    public List<ManualNodeSpec> lessons() {
        List<ManualNodeSpec> result = new ArrayList<>();
        if (isGroup()) {
            for (ManualNodeSpec child : children) result.addAll(child.lessons());
        } else {
            result.add(this);
        }
        return List.copyOf(result);
    }
}
