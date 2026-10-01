package com.phantasm.briefing.service;

import com.phantasm.briefing.data.ManualDataManager;
import com.phantasm.briefing.data.ManualNodeSpec;
import com.phantasm.briefing.data.ManualReferenceSpec;
import com.phantasm.briefing.data.ManualSpec;
import com.phantasm.briefing.data.ManualUnlockType;
import com.phantasm.briefing.data.QuestDataManager;
import com.phantasm.briefing.data.QuestObjectiveSpec;
import com.phantasm.briefing.data.QuestPhaseSpec;
import com.phantasm.briefing.data.QuestSpec;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Server-side handbook unlocks. Once a lesson unlocks it stays unlocked for that player. */
public final class ManualVisibilityService {
    private ManualVisibilityService() {}

    public static void refreshUnlocks(ServerPlayer player) {
        if (player == null) return;
        for (ManualSpec manual : ManualDataManager.getInstance().getAllManuals()) {
            for (ManualNodeSpec lesson : manual.lessons()) {
                if (lesson.unlockType() == ManualUnlockType.ALWAYS) continue;
                if (BriefingPlayerData.isManualLessonUnlocked(player, manual.manualId(), lesson.nodeId())) continue;
                if (unlockConditionSatisfied(player, lesson)) {
                    BriefingPlayerData.unlockManualLesson(player, manual.manualId(), lesson.nodeId());
                }
            }
        }
    }

    public static boolean isLessonVisible(ServerPlayer player, String manualId, ManualNodeSpec lesson) {
        if (lesson == null || lesson.isGroup()) return false;
        if (lesson.unlockType() == ManualUnlockType.ALWAYS) return true;
        if (BriefingPlayerData.isManualLessonUnlocked(player, manualId, lesson.nodeId())) return true;
        if (!unlockConditionSatisfied(player, lesson)) return false;
        BriefingPlayerData.unlockManualLesson(player, manualId, lesson.nodeId());
        return true;
    }

    private static boolean unlockConditionSatisfied(ServerPlayer player, ManualNodeSpec lesson) {
        if (lesson.unlockType() == ManualUnlockType.ALWAYS) return true;
        QuestSpec quest = QuestDataManager.getInstance().getQuest(lesson.unlockQuestId()).orElse(null);
        QuestPhaseSpec phase = quest == null ? null : quest.phase(lesson.unlockPhaseId());
        if (quest == null || phase == null) return false;

        return switch (lesson.unlockType()) {
            case ALWAYS -> true;
            case PHASE_COMPLETED -> phaseCompleted(player, quest, phase.phaseId());
            case OBJECTIVE_COMPLETED -> {
                QuestObjectiveSpec objective = phase.objective(lesson.unlockObjectiveId());
                yield objective != null && BriefingPlayerData.isObjectiveCompleted(
                        player, quest.questId(), phase.phaseId(), objective.objectiveId());
            }
            case OBJECTIVE_ACTIVE -> {
                QuestObjectiveSpec objective = phase.objective(lesson.unlockObjectiveId());
                yield objective != null
                        && QuestRuntimeService.isPhaseActive(player, quest, phase.phaseId())
                        && !BriefingPlayerData.isObjectiveCompleted(
                                player, quest.questId(), phase.phaseId(), objective.objectiveId())
                        && QuestRuntimeService.isObjectiveActive(player, quest, objective);
            }
        };
    }

    private static boolean phaseCompleted(ServerPlayer player, QuestSpec quest, String phaseId) {
        return QuestRuntimeService.isQuestCompleted(player, quest.questId())
                || BriefingPlayerData.isPhaseCompleted(player, quest.questId(), phaseId);
    }

    private static ManualNodeSpec visibleNode(ServerPlayer player, String manualId, ManualNodeSpec node) {
        if (!node.isGroup()) return isLessonVisible(player, manualId, node) ? node : null;
        List<ManualNodeSpec> children = new ArrayList<>();
        for (ManualNodeSpec child : node.children()) {
            ManualNodeSpec filtered = visibleNode(player, manualId, child);
            if (filtered != null) children.add(filtered);
        }
        return children.isEmpty() ? null : new ManualNodeSpec(
                "group", node.nodeId(), node.title(), children, List.of(), ManualUnlockType.ALWAYS, "", "", "");
    }

    public static List<ManualSpec> visibleManuals(ServerPlayer player) {
        refreshUnlocks(player);
        List<ManualSpec> result = new ArrayList<>();
        for (ManualSpec manual : ManualDataManager.getInstance().getAllManuals()) {
            List<ManualNodeSpec> visibleSections = new ArrayList<>();
            for (ManualNodeSpec node : manual.sections()) {
                ManualNodeSpec filtered = visibleNode(player, manual.manualId(), node);
                if (filtered != null) visibleSections.add(filtered);
            }
            if (!visibleSections.isEmpty()) result.add(new ManualSpec(manual.manualId(), manual.title(),
                    manual.description(), manual.category(), manual.cover(), manual.sortOrder(), visibleSections));
        }
        result.sort(Comparator.comparingInt(ManualSpec::sortOrder).thenComparing(ManualSpec::title));
        return List.copyOf(result);
    }

    public static List<ManualReferenceSpec> visibleRefs(ServerPlayer player, List<ManualReferenceSpec> links) {
        refreshUnlocks(player);
        List<ManualReferenceSpec> result = new ArrayList<>();
        for (ManualReferenceSpec link : links) {
            ManualSpec manual = ManualDataManager.getInstance().getManual(link.manualId()).orElse(null);
            if (manual == null) continue;
            ManualNodeSpec lesson = manual.findLesson(link.lessonId(), link.pageId());
            if (lesson == null || !isLessonVisible(player, manual.manualId(), lesson)) continue;
            if (!link.pageId().isBlank() && lesson.pages().stream().noneMatch(p -> p.pageId().equals(link.pageId()))) continue;
            result.add(new ManualReferenceSpec(manual.manualId(), lesson.nodeId(), link.pageId()));
        }
        return List.copyOf(result);
    }
}
