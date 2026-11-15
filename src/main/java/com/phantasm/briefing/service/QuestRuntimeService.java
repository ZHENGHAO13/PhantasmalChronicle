package com.phantasm.briefing.service;

import com.mojang.logging.LogUtils;
import com.phantasm.briefing.data.DialogueAction;
import com.phantasm.briefing.data.DialogueActionType;
import com.phantasm.briefing.data.QuestDataManager;
import com.phantasm.briefing.data.QuestObjectiveSpec;
import com.phantasm.briefing.data.QuestObjectiveType;
import com.phantasm.briefing.data.QuestPhaseSpec;
import com.phantasm.briefing.data.QuestRuntimeStatus;
import com.phantasm.briefing.data.QuestSpec;
import com.phantasm.briefing.integration.FTBIntegrationHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class QuestRuntimeService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private QuestRuntimeService() {
    }

    public static boolean canAccept(ServerPlayer player, QuestSpec quest) {
        if (quest == null) {
            return false;
        }
        if (BriefingPlayerData.isQuestActive(player, quest.questId())) {
            return false;
        }
        if (!quest.repeatable() && BriefingPlayerData.isQuestCompleted(player, quest.questId())) {
            return false;
        }
        for (String parentQuestId : quest.allParentQuestIds()) {
            if (!isQuestCompleted(player, parentQuestId)) {
                return false;
            }
        }
        return BriefingConditionService.all(player, null, quest.unlockConditions());
    }

    public static boolean accept(ServerPlayer player, String questId) {
        QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
        if (!canAccept(player, quest)) {
            return false;
        }

        activateQuest(player, quest);
        return true;
    }

    public static boolean give(ServerPlayer player, String questId) {
        QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
        if (quest == null) {
            return false;
        }

        activateQuest(player, quest);
        return true;
    }

    public static boolean complete(ServerPlayer player, String questId) {
        QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
        if (quest == null) {
            return false;
        }

        var state = BriefingPlayerData.quest(player, questId);
        state.putString("status", "completed");
        if (!quest.ftbQuestId().isBlank()) {
            FTBIntegrationHelper.completeQuestSilently(player, quest.ftbQuestId());
        }
        BriefingPlayerData.untrackQuest(player, questId);
        StructureSearchCompatService.clearQuestRuntimeData(player, questId);
        executeQuestActions(player, quest.questId(), quest.completeActions(), "quest_complete", quest.questId(), false);
        trackNextQuests(player, quest);
        QuestTrackerService.syncToClient(player);
        QuestEntityHintService.syncToClient(player);
        return true;
    }

    public static boolean reset(ServerPlayer player, String questId) {
        QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
        if (quest == null) {
            return false;
        }

        StructureSearchCompatService.clearQuestRuntimeData(player, questId);
        BriefingPlayerData.removeQuest(player, questId);
        BriefingPlayerData.untrackQuest(player, questId);
        QuestTrackerService.syncToClient(player);
        QuestEntityHintService.syncToClient(player);
        return true;
    }

    public static int resetAll(ServerPlayer player) {
        List<String> questIds = BriefingPlayerData.questIds(player);
        if (questIds.isEmpty()) {
            QuestTrackerService.syncToClient(player);
            QuestEntityHintService.syncToClient(player);
            return 0;
        }

        for (String questId : questIds) {
            StructureSearchCompatService.clearQuestRuntimeData(player, questId);
            BriefingPlayerData.removeQuest(player, questId);
        }
        BriefingPlayerData.setTrackedQuestIds(player, List.of());
        QuestTrackerService.syncToClient(player);
        QuestEntityHintService.syncToClient(player);
        return questIds.size();
    }

    private static void activateQuest(ServerPlayer player, QuestSpec quest) {
        String questId = quest.questId();
        var state = BriefingPlayerData.quest(player, questId);
        state.putString("status", "active");
        BriefingPlayerData.clearCompletedObjectives(player, questId);
        BriefingPlayerData.clearObjectiveProgresses(player, questId);
        StructureSearchCompatService.clearQuestRuntimeData(player, questId);

        List<String> tracked = new ArrayList<>(BriefingPlayerData.trackedQuestIds(player));
        if (!tracked.contains(questId) && tracked.size() < 2) {
            tracked.add(questId);
            BriefingPlayerData.setTrackedQuestIds(player, tracked);
        }
        for (String flag : quest.flagsToSetOnAccept()) {
            PlayerFlagService.setFlag(player, flag);
        }
        executeQuestActions(player, quest.questId(), quest.acceptActions(), "quest_accept", quest.questId(), true);

        QuestPhaseSpec initialPhase = quest.phase(quest.initialPhaseId());
        if (initialPhase != null) {
            applyPhaseChange(player, quest, initialPhase);
        } else {
            BriefingPlayerData.setPhase(player, questId, quest.initialPhaseId());
            QuestTrackerService.syncToClient(player);
            QuestEntityHintService.syncToClient(player);
        }
        evaluateAutomaticObjectives(player);
    }

    public static boolean isQuestActive(ServerPlayer player, String questId) {
        return BriefingPlayerData.isQuestActive(player, questId);
    }

    public static QuestRuntimeStatus status(ServerPlayer player, String questId) {
        QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
        return status(player, quest);
    }

    public static QuestRuntimeStatus status(ServerPlayer player, QuestSpec quest) {
        if (quest == null) {
            return QuestRuntimeStatus.LOCKED;
        }
        if (isQuestCompleted(player, quest.questId())) {
            return QuestRuntimeStatus.COMPLETED;
        }
        if (isQuestReadyToTurnIn(player, quest)) {
            return QuestRuntimeStatus.READY_TO_TURN_IN;
        }
        if (isQuestActive(player, quest.questId())) {
            return QuestRuntimeStatus.ACTIVE;
        }
        if (canAccept(player, quest)) {
            return QuestRuntimeStatus.AVAILABLE;
        }
        return QuestRuntimeStatus.LOCKED;
    }

    public static boolean isQuestAvailable(ServerPlayer player, String questId) {
        QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
        return canAccept(player, quest);
    }

    public static boolean isQuestReadyToTurnIn(ServerPlayer player, String questId) {
        QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
        return isQuestReadyToTurnIn(player, quest);
    }

    public static boolean isQuestReadyToTurnIn(ServerPlayer player, QuestSpec quest) {
        if (quest == null || !isQuestActive(player, quest.questId())) {
            return false;
        }

        QuestObjectiveSpec currentObjective = currentObjective(player, quest).orElse(null);
        if (currentObjective != null) {
            return currentObjective.hintType() == com.phantasm.briefing.data.QuestHintType.COMPLETE;
        }

        QuestPhaseSpec currentPhase = currentPhase(player, quest).orElse(null);
        return currentPhase != null && currentPhase.hintType() == com.phantasm.briefing.data.QuestHintType.COMPLETE;
    }

    public static boolean isQuestCompleted(ServerPlayer player, String questId) {
        QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
        if (quest != null) {
            boolean internalCompleted = BriefingPlayerData.isQuestCompleted(player, questId);
            boolean ftbCompleted = !quest.ftbQuestId().isBlank() && FTBIntegrationHelper.hasCompletedQuest(player, quest.ftbQuestId());
            return internalCompleted || ftbCompleted;
        }

        return !questId.isBlank() && FTBIntegrationHelper.hasCompletedQuest(player, questId);
    }

    public static String phase(ServerPlayer player, String questId) {
        return BriefingPlayerData.phase(player, questId);
    }

    public static Optional<QuestPhaseSpec> currentPhase(ServerPlayer player, QuestSpec quest) {
        if (quest == null || !isQuestActive(player, quest.questId())) {
            return Optional.empty();
        }
        return Optional.ofNullable(quest.phase(phase(player, quest.questId())));
    }

    public static List<String> currentObjectiveLines(ServerPlayer player, QuestSpec quest) {
        Optional<QuestObjectiveSpec> currentObjective = currentObjective(player, quest);
        if (currentObjective.isPresent()) {
            List<String> objectiveLines = buildObjectiveLines(player, quest, currentObjective.get());
            if (!objectiveLines.isEmpty()) {
                return objectiveLines;
            }
        }
        return currentPhase(player, quest)
                .map(QuestPhaseSpec::objectiveLines)
                .filter(lines -> !lines.isEmpty())
                .orElse(quest.objectiveLines());
    }

    public static boolean setPhase(ServerPlayer player, String questId, String phaseId) {
        QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
        if (quest == null || !isQuestActive(player, questId)) {
            return false;
        }
        QuestPhaseSpec phase = quest.phase(phaseId);
        if (phase == null) {
            return false;
        }

        applyPhaseChange(player, quest, phase);
        return true;
    }

    public static Optional<QuestObjectiveSpec> currentObjective(ServerPlayer player, QuestSpec quest) {
        QuestPhaseSpec phase = currentPhase(player, quest).orElse(null);
        if (phase == null || phase.objectives().isEmpty()) {
            return Optional.empty();
        }

        for (QuestObjectiveSpec objective : phase.objectives()) {
            if (!BriefingPlayerData.isObjectiveCompleted(player, quest.questId(), objective.objectiveId())
                    && BriefingConditionService.all(player, null, objective.conditions())) {
                return Optional.of(objective);
            }
        }
        return Optional.empty();
    }

    public static boolean isObjectiveCompleted(ServerPlayer player, String questId, String objectiveId) {
        return isQuestActive(player, questId) && BriefingPlayerData.isObjectiveCompleted(player, questId, objectiveId);
    }

    public static boolean completeObjective(ServerPlayer player, String questId, String objectiveId) {
        QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
        QuestPhaseSpec phase = currentPhase(player, quest).orElse(null);
        QuestObjectiveSpec objective = phase == null ? null : phase.objective(objectiveId);
        if (quest == null || phase == null || objective == null) {
            return false;
        }
        if (BriefingPlayerData.isObjectiveCompleted(player, questId, objectiveId)) {
            return true;
        }
        if (!BriefingConditionService.all(player, null, objective.conditions())) {
            return false;
        }

        BriefingPlayerData.completeObjective(player, questId, objectiveId);
        BriefingPlayerData.setObjectiveProgress(player, questId, objectiveId, objective.requiredCount());
        executeObjectiveActions(player, questId, objective);
        if (!handlePostObjectiveCompletion(player, quest, phase)) {
            QuestTrackerService.syncToClient(player);
            QuestEntityHintService.syncToClient(player);
        }
        return true;
    }

    public static void onEntityKilled(ServerPlayer player, String entityTypeId) {
        progressEventObjectives(player, QuestObjectiveType.KILL, objective -> objective.targetId().equals(entityTypeId));
    }

    public static void onEntityInteracted(ServerPlayer player, String entityTypeId, String questNodeId) {
        progressEventObjectives(player, QuestObjectiveType.INTERACT, objective ->
                objective.targetId().equals(entityTypeId) || (!questNodeId.isBlank() && objective.targetId().equals(questNodeId))
        );
    }

    public static void evaluateAutomaticObjectives(ServerPlayer player) {
        boolean changed;
        int safety = 16;
        do {
            changed = false;
            for (String questId : BriefingPlayerData.activeQuestIds(player)) {
                QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
                QuestObjectiveSpec objective = currentObjective(player, quest).orElse(null);
                if (quest == null || objective == null) {
                    continue;
                }
                if (objective.objectiveType() == QuestObjectiveType.COLLECT
                        && !objective.targetId().isBlank()
                        && countMatchingItems(player, objective.targetId()) >= objective.requiredCount()) {
                    changed |= completeObjective(player, quest.questId(), objective.objectiveId());
                    continue;
                }
                if (objective.objectiveType() == QuestObjectiveType.VISIT
                        && hasReachedVisitTarget(player, quest, objective)) {
                    changed |= completeObjective(player, quest.questId(), objective.objectiveId());
                }
            }
        } while (changed && --safety > 0);
    }

    private static void progressEventObjectives(ServerPlayer player, QuestObjectiveType objectiveType, ObjectiveMatcher matcher) {
        for (String questId : BriefingPlayerData.activeQuestIds(player)) {
            QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
            QuestObjectiveSpec objective = currentObjective(player, quest).orElse(null);
            if (quest == null || objective == null || objective.objectiveType() != objectiveType) {
                continue;
            }
            if (objective.targetId().isBlank() || !matcher.matches(objective)) {
                continue;
            }

            int progress = BriefingPlayerData.incrementObjectiveProgress(player, quest.questId(), objective.objectiveId(), 1);
            if (progress >= objective.requiredCount()) {
                completeObjective(player, quest.questId(), objective.objectiveId());
            } else {
                QuestTrackerService.syncToClient(player);
                QuestEntityHintService.syncToClient(player);
            }
        }
    }

    private static int countMatchingItems(ServerPlayer player, String itemId) {
        ResourceLocation itemKey = ResourceLocation.tryParse(itemId);
        if (itemKey == null || !ForgeRegistries.ITEMS.containsKey(itemKey)) {
            return 0;
        }

        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && itemKey.equals(ForgeRegistries.ITEMS.getKey(stack.getItem()))) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static boolean hasReachedVisitTarget(ServerPlayer player, QuestSpec quest, QuestObjectiveSpec objective) {
        com.phantasm.briefing.data.QuestMarkerSpec marker = resolveObjectiveMarker(player, quest, objective);
        if (marker == null) {
            return false;
        }
        if (!player.serverLevel().dimension().location().toString().equals(marker.dimension())) {
            return false;
        }

        double dx = player.getX() - marker.x();
        double dz = player.getZ() - marker.z();
        double radius = objective.radius();
        // 到达目标只比较水平距离，避免地形高度造成误判。
        return (dx * dx) + (dz * dz) <= radius * radius;
    }

    private static com.phantasm.briefing.data.QuestMarkerSpec resolveObjectiveMarker(ServerPlayer player, QuestSpec quest, QuestObjectiveSpec objective) {
        return StructureSearchCompatService.resolveMarker(player, quest, objective);
    }

    private static List<String> buildObjectiveLines(ServerPlayer player, QuestSpec quest, QuestObjectiveSpec objective) {
        List<String> lines = objective.objectiveLines().isEmpty()
                ? (objective.title().isBlank() ? List.of() : List.of(objective.title()))
                : objective.objectiveLines();
        if (objective.usesStructureSearch() && !StructureSearchCompatService.hasResolvedMarker(player, quest, objective)) {
            List<String> result = new ArrayList<>(lines);
            String waitLine = "等待定位结构: " + objective.resolvedStructureLabel();
            if (result.isEmpty()) {
                result.add(waitLine);
            } else if (!result.contains(waitLine)) {
                result.add(waitLine);
            }
            return List.copyOf(result);
        }
        if (!objective.isEventDriven()) {
            return lines;
        }

        int progress = Math.min(
                objective.requiredCount(),
                Math.max(0, BriefingPlayerData.objectiveProgress(player, quest.questId(), objective.objectiveId()))
        );
        String suffix = " (" + progress + "/" + objective.requiredCount() + ")";
        if (lines.isEmpty()) {
            return List.of(objective.objectiveType().name() + suffix);
        }

        List<String> result = new ArrayList<>(lines);
        result.set(0, result.get(0) + suffix);
        return List.copyOf(result);
    }

    private static boolean handlePostObjectiveCompletion(ServerPlayer player, QuestSpec quest, QuestPhaseSpec phase) {
        if (phase.objectives().isEmpty() || phase.nextPhaseId().isBlank()) {
            return phase.objectives().isEmpty()
                    ? false
                    : tryCompleteQuestAfterObjectivesComplete(player, quest, phase);
        }

        boolean allCompleted = phase.objectives().stream()
                .allMatch(objective -> BriefingPlayerData.isObjectiveCompleted(player, quest.questId(), objective.objectiveId()));
        if (!allCompleted) {
            return false;
        }

        QuestPhaseSpec nextPhase = quest.phase(phase.nextPhaseId());
        if (nextPhase != null) {
            applyPhaseChange(player, quest, nextPhase);
            return true;
        }
        return tryCompleteQuestAfterObjectivesComplete(player, quest, phase);
    }

    private static boolean tryCompleteQuestAfterObjectivesComplete(ServerPlayer player, QuestSpec quest, QuestPhaseSpec phase) {
        if (!phase.completeQuestOnObjectivesCompleted() || phase.objectives().isEmpty()) {
            return false;
        }

        boolean allCompleted = phase.objectives().stream()
                .allMatch(objective -> BriefingPlayerData.isObjectiveCompleted(player, quest.questId(), objective.objectiveId()));
        if (!allCompleted) {
            return false;
        }
        return complete(player, quest.questId());
    }

    private static void applyPhaseChange(ServerPlayer player, QuestSpec quest, QuestPhaseSpec phase) {
        BriefingPlayerData.setPhase(player, quest.questId(), phase.phaseId());
        executeQuestActions(player, quest.questId(), phase.actions(), "phase", phase.phaseId(), true);
        QuestTrackerService.syncToClient(player);
        QuestEntityHintService.syncToClient(player);
    }

    private static void executeObjectiveActions(ServerPlayer player, String questId, QuestObjectiveSpec objective) {
        executeQuestActions(player, questId, objective.completionActions(), "objective", objective.objectiveId(), true);
    }

    private static void trackNextQuests(ServerPlayer player, QuestSpec completedQuest) {
        if (completedQuest.nextQuestIds().isEmpty()) {
            return;
        }

        List<String> tracked = new ArrayList<>(BriefingPlayerData.trackedQuestIds(player));
        boolean changed = false;
        for (String nextQuestId : completedQuest.nextQuestIds()) {
            QuestSpec nextQuest = QuestDataManager.getInstance().getQuest(nextQuestId).orElse(null);
            if (nextQuest == null) {
                LOGGER.warn("[PhantasmBriefing] Quest '{}' references missing next quest '{}'", completedQuest.questId(), nextQuestId);
                continue;
            }
            if (tracked.contains(nextQuestId)) {
                continue;
            }
            if (QuestRuntimeService.isQuestActive(player, nextQuestId) || canAccept(player, nextQuest)) {
                tracked.add(nextQuestId);
                changed = true;
            }
        }

        if (changed) {
            BriefingPlayerData.setTrackedQuestIds(player, tracked);
        }
    }

    private static void executeQuestActions(ServerPlayer player, String questId, List<DialogueAction> actions, String ownerType, String ownerId, boolean allowSelfCompleteQuestAction) {
        for (DialogueAction action : actions) {
            if (action.type() == DialogueActionType.SET_FLAG) {
                PlayerFlagService.setFlag(player, action.targetId());
                continue;
            }

            if (action.type() == DialogueActionType.START_QUEST) {
                accept(player, action.targetId());
                continue;
            }

            if (action.type() == DialogueActionType.COMPLETE_QUEST) {
                if (!allowSelfCompleteQuestAction && questId.equals(action.targetId())) {
                    LOGGER.warn("[PhantasmBriefing] Ignored recursive complete_quest on {} '{}' of quest '{}'", ownerType, ownerId, questId);
                    continue;
                }
                complete(player, action.targetId());
                continue;
            }

            if (action.type() == DialogueActionType.OPEN_SHOP) {
                if (!DialogueShopService.openShop(player, action.targetId())) {
                    LOGGER.warn("[PhantasmBriefing] {} '{}' of quest '{}' failed to open shop '{}'", ownerType, ownerId, questId, action.targetId());
                }
                continue;
            }

            LOGGER.warn("[PhantasmBriefing] Unsupported {} action '{}' on {} '{}' of quest '{}'", ownerType, action.type(), ownerType, ownerId, questId);
        }
    }

    @FunctionalInterface
    private interface ObjectiveMatcher {
        boolean matches(QuestObjectiveSpec objective);
    }
}
