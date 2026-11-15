package com.phantasm.briefing.service;

import com.phantasm.briefing.data.QuestRuntimeStatus;
import com.phantasm.briefing.data.QuestSpec;
import com.phantasm.briefing.data.QuestTreeEdgeEntry;
import com.phantasm.briefing.data.QuestTreeEdgeType;
import com.phantasm.briefing.data.QuestTreeLayoutSpec;
import com.phantasm.briefing.data.QuestTreeNodeEntry;
import com.phantasm.briefing.data.QuestTreeSnapshot;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class QuestTreeService {
    private static final int AUTO_COLUMN_SPACING = 172;
    private static final int AUTO_ROW_SPACING = 92;

    private QuestTreeService() {
    }

    public static QuestTreeSnapshot buildSnapshot(ServerPlayer player) {
        Collection<QuestSpec> questSpecs = com.phantasm.briefing.data.QuestDataManager.getInstance().getAllQuests();
        Map<String, QuestSpec> questsById = new LinkedHashMap<>();
        for (QuestSpec quest : questSpecs) {
            questsById.put(quest.questId(), quest);
        }

        Map<String, Integer> autoRows = new LinkedHashMap<>();
        List<QuestTreeNodeEntry> nodes = new ArrayList<>();
        List<QuestSpec> sortedQuests = questSpecs.stream()
                .sorted(Comparator.comparing(QuestSpec::title, String.CASE_INSENSITIVE_ORDER))
                .toList();

        for (QuestSpec quest : sortedQuests) {
            List<String> parents = quest.allParentQuestIds();
            int depth = computeDepth(quest, questsById, new LinkedHashSet<>());
            QuestTreeLayoutSpec layout = quest.treeLayout();
            String treeId = quest.resolvedTreeId();
            int x = layout != null && (layout.x() != 0 || layout.y() != 0)
                    ? layout.x()
                    : 48 + (depth * AUTO_COLUMN_SPACING);
            int y = layout != null && (layout.x() != 0 || layout.y() != 0)
                    ? layout.y()
                    : 48 + (autoRows.merge(treeId + "#" + depth, 1, Integer::sum) - 1) * AUTO_ROW_SPACING;
            nodes.add(new QuestTreeNodeEntry(
                    quest.questId(),
                    treeId,
                    quest.title(),
                    quest.description(),
                    QuestRuntimeService.status(player, quest),
                    x,
                    y,
                    List.copyOf(QuestRuntimeService.currentObjectiveLines(player, quest)),
                    parents,
                    List.copyOf(quest.nextQuestIds())
            ));
        }

        List<QuestTreeEdgeEntry> edges = new ArrayList<>();
        for (QuestSpec quest : sortedQuests) {
            for (String parentQuestId : quest.allParentQuestIds()) {
                if (questsById.containsKey(parentQuestId)) {
                    edges.add(new QuestTreeEdgeEntry(parentQuestId, quest.questId(), QuestTreeEdgeType.PREREQUISITE));
                }
            }
            for (String nextQuestId : quest.nextQuestIds()) {
                if (questsById.containsKey(nextQuestId)) {
                    edges.add(new QuestTreeEdgeEntry(quest.questId(), nextQuestId, QuestTreeEdgeType.RECOMMENDED));
                }
            }
        }

        return new QuestTreeSnapshot(List.copyOf(nodes), List.copyOf(edges));
    }

    private static int computeDepth(QuestSpec quest, Map<String, QuestSpec> questsById, LinkedHashSet<String> visiting) {
        if (!visiting.add(quest.questId())) {
            return 0;
        }
        int maxParentDepth = -1;
        for (String parentQuestId : quest.allParentQuestIds()) {
            QuestSpec parent = questsById.get(parentQuestId);
            if (parent == null) {
                continue;
            }
            maxParentDepth = Math.max(maxParentDepth, computeDepth(parent, questsById, visiting));
        }
        visiting.remove(quest.questId());
        return maxParentDepth + 1;
    }
}
