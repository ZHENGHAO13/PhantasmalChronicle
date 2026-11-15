package com.phantasm.briefing.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.phantasm.briefing.data.DialogueAction;
import com.phantasm.briefing.data.DialogueActionType;
import com.phantasm.briefing.data.DialogueDataManager;
import com.phantasm.briefing.data.DialogueNode;
import com.phantasm.briefing.data.DialogueOption;
import com.phantasm.briefing.data.QuestDataManager;
import com.phantasm.briefing.data.QuestHintTarget;
import com.phantasm.briefing.data.QuestHintType;
import com.phantasm.briefing.data.QuestRuntimeStatus;
import com.phantasm.briefing.data.QuestSpec;
import com.phantasm.briefing.network.ModNetwork;
import com.phantasm.briefing.network.packet.SyncQuestHintTargetS2CPacket;
import com.phantasm.briefing.util.QuestNpcHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class QuestEntityHintService {
    private static final double HINT_RADIUS = 48.0D;
    private static final double HINT_VERTICAL_RADIUS = 16.0D;
    private static final int SYNC_INTERVAL_TICKS = 10;

    private static final Map<UUID, Integer> LAST_HINT_ENTITY_BY_PLAYER = new ConcurrentHashMap<>();

    private QuestEntityHintService() {
    }

    public static void tick(ServerPlayer player) {
        if (player.tickCount % SYNC_INTERVAL_TICKS != 0) {
            return;
        }
        syncToClient(player);
    }

    public static void syncToClient(ServerPlayer player) {
        QuestHintTarget target = resolveHintTarget(player).orElse(QuestHintTarget.NONE);
        int cacheValue = packCache(target);
        Integer previous = LAST_HINT_ENTITY_BY_PLAYER.put(player.getUUID(), cacheValue);
        if (previous != null && previous == cacheValue) {
            return;
        }

        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SyncQuestHintTargetS2CPacket(target.entityId(), target.type())
        );
    }

    public static void clear(ServerPlayer player) {
        LAST_HINT_ENTITY_BY_PLAYER.remove(player.getUUID());
    }

    public static Optional<QuestHintTarget> resolveHintTarget(ServerPlayer player) {
        AABB searchBox = new AABB(
                player.getX() - HINT_RADIUS,
                player.getY() - HINT_VERTICAL_RADIUS,
                player.getZ() - HINT_RADIUS,
                player.getX() + HINT_RADIUS,
                player.getY() + HINT_VERTICAL_RADIUS,
                player.getZ() + HINT_RADIUS
        );

        return player.serverLevel()
                .getEntities(player, searchBox, QuestNpcHelper::isQuestNPC)
                .stream()
                .filter(Entity::isAlive)
                .map(entity -> describeHintTarget(player, entity))
                .flatMap(Optional::stream)
                .sorted((left, right) -> {
                    int byPriority = Integer.compare(right.type().priority(), left.type().priority());
                    if (byPriority != 0) {
                        return byPriority;
                    }
                    int byRuntimeStatus = Integer.compare(right.runtimeStatus().priority(), left.runtimeStatus().priority());
                    if (byRuntimeStatus != 0) {
                        return byRuntimeStatus;
                    }
                    if (left.tracked() != right.tracked()) {
                        return left.tracked() ? -1 : 1;
                    }
                    Entity leftEntity = player.serverLevel().getEntity(left.entityId());
                    Entity rightEntity = player.serverLevel().getEntity(right.entityId());
                    double leftDistance = leftEntity == null ? Double.MAX_VALUE : leftEntity.distanceToSqr(player);
                    double rightDistance = rightEntity == null ? Double.MAX_VALUE : rightEntity.distanceToSqr(player);
                    return Double.compare(leftDistance, rightDistance);
                })
                .map(HintCandidate::toTarget)
                .findFirst();
    }

    private static Optional<HintCandidate> describeHintTarget(ServerPlayer player, Entity entity) {
        String nodeId = QuestNpcHelper.resolveQuestNodeId(entity);
        if (nodeId.isBlank()) {
            return Optional.empty();
        }

        DialogueNode node = DialogueDataManager.getInstance().getNode(nodeId).orElse(null);
        if (node == null || !DialogueFlowCoordinator.canOpenNodeForPlayer(player, node, entity)) {
            return Optional.empty();
        }

        HintSignal signal = resolveHintSignal(player, node, entity);
        if (signal.type() == QuestHintType.NONE) {
            return Optional.empty();
        }
        return Optional.of(new HintCandidate(entity.getId(), signal.type(), signal.runtimeStatus(), signal.tracked()));
    }

    private static HintSignal resolveHintSignal(ServerPlayer player, DialogueNode node, Entity entity) {
        List<String> trackedQuestIds = BriefingPlayerData.trackedQuestIds(player);
        List<DialogueOption> availableOptions = node.options().stream()
                .filter(option -> option.requiredQuestId().isBlank() || QuestRuntimeService.isQuestCompleted(player, option.requiredQuestId()))
                .filter(option -> BriefingConditionService.all(player, entity, option.conditions()))
                .toList();

        HintSignal bestSignal = resolveObjectiveOrPhaseHintSignal(player, node.nodeId(), trackedQuestIds);
        bestSignal = max(bestSignal, resolveConditionSignal(player, node.conditions(), trackedQuestIds));

        for (DialogueOption option : availableOptions) {
            bestSignal = max(bestSignal, resolveConditionSignal(player, option.conditions(), trackedQuestIds));
            bestSignal = max(bestSignal, resolveActionSignal(player, option, trackedQuestIds));
        }

        return bestSignal;
    }

    private static HintSignal resolveObjectiveOrPhaseHintSignal(ServerPlayer player, String nodeId, List<String> trackedQuestIds) {
        HintSignal bestSignal = HintSignal.NONE;
        for (QuestSpec quest : QuestDataManager.getInstance().getAllQuests()) {
            QuestRuntimeStatus runtimeStatus = QuestRuntimeService.status(player, quest);
            if (runtimeStatus != QuestRuntimeStatus.ACTIVE && runtimeStatus != QuestRuntimeStatus.READY_TO_TURN_IN) {
                continue;
            }

            var currentObjective = QuestRuntimeService.currentObjective(player, quest).orElse(null);
            if (currentObjective != null) {
                if (currentObjective.targetsNode(nodeId)) {
                    bestSignal = max(bestSignal, signalForTargetedQuest(runtimeStatus, currentObjective.hintType(), trackedQuestIds.contains(quest.questId())));
                }
                continue;
            }

            var currentPhase = QuestRuntimeService.currentPhase(player, quest).orElse(null);
            if (currentPhase != null && currentPhase.targetsNode(nodeId)) {
                bestSignal = max(bestSignal, signalForTargetedQuest(runtimeStatus, currentPhase.hintType(), trackedQuestIds.contains(quest.questId())));
            }
        }
        return bestSignal;
    }

    private static HintSignal resolveActionSignal(ServerPlayer player, DialogueOption option, List<String> trackedQuestIds) {
        HintSignal bestSignal = HintSignal.NONE;
        for (DialogueAction action : option.actions()) {
            bestSignal = max(bestSignal, resolveActionSignal(player, action, trackedQuestIds));
        }
        return bestSignal;
    }

    private static HintSignal resolveActionSignal(ServerPlayer player, DialogueAction action, List<String> trackedQuestIds) {
        return switch (action.type()) {
            case START_QUEST -> signalForQuestStatus(QuestRuntimeService.status(player, action.targetId()), trackedQuestIds.contains(action.targetId()));
            case COMPLETE_QUEST -> signalForQuestStatus(QuestRuntimeService.status(player, action.targetId()), trackedQuestIds.contains(action.targetId()));
            case COMPLETE_OBJECTIVE, SET_QUEST_PHASE ->
                    signalForQuestStatus(QuestRuntimeService.status(player, DialogueAction.decodeQuestId(action.targetId())), trackedQuestIds.contains(DialogueAction.decodeQuestId(action.targetId())));
            default -> HintSignal.NONE;
        };
    }

    private static HintSignal resolveConditionSignal(ServerPlayer player, List<JsonObject> conditions, List<String> trackedQuestIds) {
        HintSignal bestSignal = HintSignal.NONE;
        for (JsonObject condition : conditions) {
            bestSignal = max(bestSignal, resolveConditionSignal(player, condition, trackedQuestIds));
        }
        return bestSignal;
    }

    private static HintSignal resolveConditionSignal(ServerPlayer player, JsonObject condition, List<String> trackedQuestIds) {
        if (condition == null || condition.entrySet().isEmpty()) {
            return HintSignal.NONE;
        }

        String type = BriefingConditionService.normalizeConditionType(readString(condition, "condition"));
        if ((BriefingConditionService.CURRENT_NAMESPACE + "and").equals(type)
                || (BriefingConditionService.CURRENT_NAMESPACE + "or").equals(type)) {
            HintSignal bestSignal = HintSignal.NONE;
            JsonArray array = condition.has("conditions") && condition.get("conditions").isJsonArray()
                    ? condition.getAsJsonArray("conditions")
                    : new JsonArray();
            for (JsonElement element : array) {
                if (element.isJsonObject()) {
                    bestSignal = max(bestSignal, resolveConditionSignal(player, element.getAsJsonObject(), trackedQuestIds));
                }
            }
            return bestSignal;
        }
        if ((BriefingConditionService.CURRENT_NAMESPACE + "not").equals(type)
                && condition.has("inner") && condition.get("inner").isJsonObject()) {
            return resolveConditionSignal(player, condition.getAsJsonObject("inner"), trackedQuestIds);
        }

        String questId = readString(condition, "questId");
        if (questId.isBlank()) {
            return HintSignal.NONE;
        }

        QuestRuntimeStatus runtimeStatus = QuestRuntimeService.status(player, questId);
        if ((BriefingConditionService.CURRENT_NAMESPACE + "quest_available").equals(type)) {
            return runtimeStatus == QuestRuntimeStatus.AVAILABLE
                    ? signalForQuestStatus(runtimeStatus, trackedQuestIds.contains(questId))
                    : HintSignal.NONE;
        }
        if ((BriefingConditionService.CURRENT_NAMESPACE + "quest_ready_to_turn_in").equals(type)) {
            return runtimeStatus == QuestRuntimeStatus.READY_TO_TURN_IN
                    ? signalForQuestStatus(runtimeStatus, trackedQuestIds.contains(questId))
                    : HintSignal.NONE;
        }
        if ((BriefingConditionService.CURRENT_NAMESPACE + "quest_accepted").equals(type)
                || (BriefingConditionService.CURRENT_NAMESPACE + "quest_phase").equals(type)
                || (BriefingConditionService.CURRENT_NAMESPACE + "quest_objective_completed").equals(type)) {
            return (runtimeStatus == QuestRuntimeStatus.ACTIVE || runtimeStatus == QuestRuntimeStatus.READY_TO_TURN_IN)
                    ? signalForQuestStatus(runtimeStatus, trackedQuestIds.contains(questId))
                    : HintSignal.NONE;
        }
        return HintSignal.NONE;
    }

    private static String readString(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) {
            return "";
        }
        return json.get(key).getAsString();
    }

    private static int packCache(QuestHintTarget target) {
        return (target.entityId() << 2) ^ target.type().ordinal();
    }

    private static HintSignal signalForTargetedQuest(QuestRuntimeStatus runtimeStatus, QuestHintType configuredType, boolean tracked) {
        if (runtimeStatus == QuestRuntimeStatus.READY_TO_TURN_IN) {
            return new HintSignal(QuestHintType.COMPLETE, runtimeStatus, tracked);
        }
        if (runtimeStatus == QuestRuntimeStatus.ACTIVE) {
            QuestHintType effectiveType = configuredType == QuestHintType.COMPLETE ? QuestHintType.COMPLETE : QuestHintType.ADVANCE;
            return new HintSignal(effectiveType, runtimeStatus, tracked);
        }
        return signalForQuestStatus(runtimeStatus, tracked);
    }

    private static HintSignal signalForQuestStatus(QuestRuntimeStatus runtimeStatus, boolean tracked) {
        QuestHintType type = switch (runtimeStatus) {
            case AVAILABLE -> QuestHintType.AVAILABLE;
            case ACTIVE -> QuestHintType.ADVANCE;
            case READY_TO_TURN_IN -> QuestHintType.COMPLETE;
            case LOCKED, COMPLETED -> QuestHintType.NONE;
        };
        return type == QuestHintType.NONE ? HintSignal.NONE : new HintSignal(type, runtimeStatus, tracked);
    }

    private static HintSignal max(HintSignal left, HintSignal right) {
        if (right.type().priority() > left.type().priority()) {
            return right;
        }
        if (right.type().priority() < left.type().priority()) {
            return left;
        }
        if (right.runtimeStatus().priority() > left.runtimeStatus().priority()) {
            return right;
        }
        if (right.runtimeStatus().priority() < left.runtimeStatus().priority()) {
            return left;
        }
        if (right.tracked() && !left.tracked()) {
            return right;
        }
        return left;
    }

    private record HintSignal(
            QuestHintType type,
            QuestRuntimeStatus runtimeStatus,
            boolean tracked
    ) {
        private static final HintSignal NONE = new HintSignal(QuestHintType.NONE, QuestRuntimeStatus.LOCKED, false);
    }

    private record HintCandidate(
            int entityId,
            QuestHintType type,
            QuestRuntimeStatus runtimeStatus,
            boolean tracked
    ) {
        private QuestHintTarget toTarget() {
            return new QuestHintTarget(entityId, type);
        }
    }
}
