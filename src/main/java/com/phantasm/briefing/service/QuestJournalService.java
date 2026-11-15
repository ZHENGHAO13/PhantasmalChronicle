package com.phantasm.briefing.service;

import com.phantasm.briefing.data.QuestDataManager;
import com.phantasm.briefing.data.QuestJournalEntry;
import com.phantasm.briefing.data.QuestRuntimeStatus;
import com.phantasm.briefing.data.QuestSpec;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class QuestJournalService {
    private QuestJournalService() {
    }

    public static List<QuestJournalEntry> buildEntries(ServerPlayer player) {
        List<QuestJournalEntry> entries = new ArrayList<>();
        for (QuestSpec quest : getVisibleQuests(player)) {
            QuestRuntimeStatus status = QuestRuntimeService.status(player, quest);
            entries.add(new QuestJournalEntry(
                    quest.questId(),
                    quest.title(),
                    quest.description(),
                    QuestRuntimeService.currentObjectiveLines(player, quest),
                    status
            ));
        }

        entries.sort(Comparator
                .comparingInt((QuestJournalEntry entry) -> -entry.status().priority())
                .thenComparing(QuestJournalEntry::title, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(entries);
    }

    public static List<QuestSpec> getVisibleQuests(ServerPlayer player) {
        List<QuestSpec> visibleQuests = new ArrayList<>();
        for (QuestSpec quest : QuestDataManager.getInstance().getAllQuests()) {
            if (!quest.requiredQuestId().isBlank() && !QuestRuntimeService.isQuestCompleted(player, quest.requiredQuestId())) {
                continue;
            }
            if (!BriefingConditionService.all(player, null, quest.unlockConditions())) {
                continue;
            }
            visibleQuests.add(quest);
        }
        return List.copyOf(visibleQuests);
    }
}
