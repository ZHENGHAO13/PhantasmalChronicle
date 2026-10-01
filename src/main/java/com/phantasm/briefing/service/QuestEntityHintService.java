package com.phantasm.briefing.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.phantasm.briefing.data.DialogueAction;
import com.phantasm.briefing.data.DialogueNode;
import com.phantasm.briefing.data.DialogueOption;
import com.phantasm.briefing.data.NpcBindingDataManager;
import com.phantasm.briefing.data.QuestDataManager;
import com.phantasm.briefing.data.QuestHintTarget;
import com.phantasm.briefing.data.QuestHintType;
import com.phantasm.briefing.data.QuestObjectiveSpec;
import com.phantasm.briefing.data.QuestPhaseSpec;
import com.phantasm.briefing.data.QuestRuntimeStatus;
import com.phantasm.briefing.data.QuestSpec;
import com.phantasm.briefing.network.ModNetwork;
import com.phantasm.briefing.network.packet.SyncQuestHintTargetS2CPacket;
import com.phantasm.briefing.util.QuestNpcHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class QuestEntityHintService {
    private static final double HINT_RADIUS = 48.0D;
    private static final double HINT_VERTICAL_RADIUS = 16.0D;
    private static final double PLAYER_RECHECK_DISTANCE_SQR = 16.0D;
    private static final double NPC_RECHECK_DISTANCE_SQR = 1.0D;
    private static final int MAX_HINT_TARGETS = 16;
    private static final Map<UUID, CandidateState> CANDIDATES_BY_PLAYER = new HashMap<>();

    private static final Map<UUID, List<QuestHintTarget>> LAST_HINT_TARGETS_BY_PLAYER = new HashMap<>();

    private QuestEntityHintService() {
    }

    public static void tick(ServerPlayer player) {
        CandidateState state = candidateState(player);
        long bindingRevision = NpcBindingDataManager.getInstance().getReloadRevision();
        long chunkPosition = player.chunkPosition().toLong();

        if (state.bindingRevision != bindingRevision || state.lastChunkPosition != chunkPosition) {
            state.dirty = true;
            state.nearbyScanRequired = true;
        }
        if (!state.hasEvaluationPosition || state.playerMovedEnough(player)) {
            state.dirty = true;
        }
        if (!state.dirty && state.probeOneCandidateMovement(player)) {
            state.dirty = true;
        }
        if (!state.dirty) {
            return;
        }

        flushToClient(player, state, bindingRevision, chunkPosition);
    }

    /**
     * Marks this player's hint state dirty. Actual resolution is coalesced into the next
     * player tick so several quest/dialogue changes in the same server tick cost one pass.
     */
    public static void requestSync(ServerPlayer player) {
        if (player != null) {
            candidateState(player).dirty = true;
        }
    }

    private static void flushToClient(
            ServerPlayer player,
            CandidateState state,
            long bindingRevision,
            long chunkPosition
    ) {
        List<QuestHintTarget> targets = resolveHintTargets(player, state);
        List<QuestHintTarget> previous = LAST_HINT_TARGETS_BY_PLAYER.put(player.getUUID(), targets);

        state.dirty = false;
        state.bindingRevision = bindingRevision;
        state.lastChunkPosition = chunkPosition;
        state.captureEvaluationPosition(player);

        if (targets.equals(previous)) {
            return;
        }

        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SyncQuestHintTargetS2CPacket(targets)
        );
    }

    public static void clear(ServerPlayer player) {
        CANDIDATES_BY_PLAYER.remove(player.getUUID());
        LAST_HINT_TARGETS_BY_PLAYER.remove(player.getUUID());
    }

    public static void clearAll() {
        CANDIDATES_BY_PLAYER.clear();
        LAST_HINT_TARGETS_BY_PLAYER.clear();
    }

    public static void onStartTracking(ServerPlayer player, Entity entity) {
        if (!QuestNpcHelper.isPotentialQuestNpc(entity) || !QuestNpcHelper.isQuestNPC(entity)) {
            return;
        }
        CandidateState state = candidateState(player);
        if (entity.level() == player.serverLevel() && entity.isAlive()) {
            if (state.put(entity)) {
                state.dirty = true;
            }
        }
    }

    public static void onStopTracking(ServerPlayer player, Entity entity) {
        CandidateState state = CANDIDATES_BY_PLAYER.get(player.getUUID());
        if (state != null && state.remove(entity.getId())) {
            state.dirty = true;
        }
    }

    private static CandidateState candidateState(ServerPlayer player) {
        CandidateState state = CANDIDATES_BY_PLAYER.get(player.getUUID());
        if (state == null || state.level != player.serverLevel()) {
            state = new CandidateState(player.serverLevel());
            CANDIDATES_BY_PLAYER.put(player.getUUID(), state);
        }
        return state;
    }

    private static void refreshNearbyCandidates(ServerPlayer player, CandidateState state, AABB searchBox) {
        // Tracking events are the steady-state source. This bounded scan is only used when
        // entering a new chunk/dimension or after NPC bindings are reloaded.
        state.removeInvalid(player);
        for (Entity entity : player.serverLevel().getEntities(player, searchBox,
                candidate -> QuestNpcHelper.isPotentialQuestNpc(candidate) && QuestNpcHelper.isQuestNPC(candidate))) {
            if (entity.isAlive()) {
                state.put(entity);
            }
        }
        state.nearbyScanRequired = false;
    }

    private static List<QuestHintTarget> resolveHintTargets(ServerPlayer player, CandidateState state) {
        AABB searchBox = new AABB(
                player.getX() - HINT_RADIUS,
                player.getY() - HINT_VERTICAL_RADIUS,
                player.getZ() - HINT_RADIUS,
                player.getX() + HINT_RADIUS,
                player.getY() + HINT_VERTICAL_RADIUS,
                player.getZ() + HINT_RADIUS
        );

        if (state.nearbyScanRequired) {
            refreshNearbyCandidates(player, state, searchBox);
        } else {
            state.removeInvalid(player);
        }

        // Read player tracking data once, not once per nearby NPC (or twice per NPC).
        List<String> trackedQuestIds = BriefingPlayerData.trackedQuestIds(player);

        // Resolve every candidate through the single current hint path, then apply the display-only
        // tracked-quest filter as the final stage so filtering stays out of the data/configuration layer.
        List<HintCandidate> resolvedCandidates = new ArrayList<>();
        for (CandidateEntry entry : new ArrayList<>(state.entities.values())) {
            Entity entity = entry.entity();
            if (entity.isRemoved() || !entity.isAlive()
                    || entity.level() != player.serverLevel()
                    || !searchBox.intersects(entity.getBoundingBox())
                    || !QuestNpcHelper.isPotentialQuestNpc(entity)
                    || !QuestNpcHelper.isQuestNPC(entity)) {
                continue;
            }
            describeHintTarget(player, entity, trackedQuestIds).ifPresent(resolvedCandidates::add);
        }

        // Available quests remain discoverable. Once a quest is active (including turn-in),
        // only the currently tracked quest is visualised in-world.
        resolvedCandidates.removeIf(candidate -> !shouldDisplayCandidate(candidate));

        resolvedCandidates.sort((left, right) -> {
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
            return Double.compare(left.distanceToSqr(), right.distanceToSqr());
        });

        int targetCount = Math.min(MAX_HINT_TARGETS, resolvedCandidates.size());
        List<QuestHintTarget> targets = new ArrayList<>(targetCount);
        for (int index = 0; index < targetCount; index++) {
            targets.add(resolvedCandidates.get(index).toTarget());
        }
        return List.copyOf(targets);
    }

    private static boolean shouldDisplayCandidate(HintCandidate candidate) {
        return candidate.runtimeStatus() == QuestRuntimeStatus.AVAILABLE || candidate.tracked();
    }

    private static Optional<HintCandidate> describeHintTarget(ServerPlayer player, Entity entity,
                                                               List<String> trackedQuestIds) {
        String nodeId = QuestNpcHelper.resolveQuestNodeId(entity);
        String npcIdentity = QuestNpcHelper.getNpcIdentity(entity);
        HintSignal signal = resolveNpcObjectiveHintSignal(player, npcIdentity, trackedQuestIds);

        if (!nodeId.isBlank()) {
            DialogueNode node = DialogueFlowCoordinator.resolveBestNodeForPlayer(player, nodeId, entity).orElse(null);
            if (node != null) {
                signal = max(signal, resolveHintSignal(player, node, entity, trackedQuestIds));
            }
        }

        if (signal.type() == QuestHintType.NONE) {
            return Optional.empty();
        }
        return Optional.of(new HintCandidate(entity.getId(), signal.type(), signal.runtimeStatus(), signal.tracked(), entity.distanceToSqr(player)));
    }

    private static HintSignal resolveNpcObjectiveHintSignal(ServerPlayer player, String npcIdentity, List<String> trackedQuestIds) {
        if (npcIdentity == null || npcIdentity.isBlank()) {
            return HintSignal.NONE;
        }

        HintSignal bestSignal = HintSignal.NONE;
        for (QuestSpec quest : QuestDataManager.getInstance().getQuestsTargetingNpc(npcIdentity)) {
            QuestRuntimeStatus runtimeStatus = QuestRuntimeService.status(player, quest);
            if (runtimeStatus != QuestRuntimeStatus.ACTIVE && runtimeStatus != QuestRuntimeStatus.READY_TO_TURN_IN) {
                continue;
            }

            for (QuestObjectiveSpec objective : QuestRuntimeService.activeObjectives(player, quest)) {
                if (objective.targetsNpc(npcIdentity)) {
                    bestSignal = max(bestSignal, signalForTargetedQuest(
                            runtimeStatus,
                            objective.hintType(),
                            trackedQuestIds.contains(quest.questId())
                    ));
                }
            }
        }
        return bestSignal;
    }

    private static HintSignal resolveHintSignal(ServerPlayer player, DialogueNode node, Entity entity,
                                                 List<String> trackedQuestIds) {
        List<DialogueOption> availableOptions = node.options().stream()
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
        for (QuestSpec quest : QuestDataManager.getInstance().getQuestsTargetingNode(nodeId)) {
            QuestRuntimeStatus runtimeStatus = QuestRuntimeService.status(player, quest);
            if (runtimeStatus != QuestRuntimeStatus.ACTIVE && runtimeStatus != QuestRuntimeStatus.READY_TO_TURN_IN) {
                continue;
            }

            List<QuestObjectiveSpec> activeObjectives = QuestRuntimeService.activeObjectives(player, quest);
            for (QuestObjectiveSpec objective : activeObjectives) {
                if (objective.targetsNode(nodeId)) {
                    bestSignal = max(bestSignal, signalForTargetedQuest(
                            runtimeStatus, objective.hintType(), trackedQuestIds.contains(quest.questId())));
                }
            }

            for (QuestPhaseSpec activePhase : QuestRuntimeService.activePhases(player, quest)) {
                if (activePhase.targetsNode(nodeId)) {
                    bestSignal = max(bestSignal, signalForTargetedQuest(
                            runtimeStatus, activePhase.hintType(), trackedQuestIds.contains(quest.questId())));
                }
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
            case START_QUEST -> {
                QuestRuntimeStatus runtimeStatus = QuestRuntimeService.status(player, action.targetId());
                yield runtimeStatus == QuestRuntimeStatus.AVAILABLE
                        ? signalForQuestStatus(runtimeStatus, trackedQuestIds.contains(action.targetId()))
                        : HintSignal.NONE;
            }
            case COMPLETE_QUEST -> {
                QuestRuntimeStatus runtimeStatus = QuestRuntimeService.status(player, action.targetId());
                yield runtimeStatus == QuestRuntimeStatus.READY_TO_TURN_IN
                        ? signalForQuestStatus(runtimeStatus, trackedQuestIds.contains(action.targetId()))
                        : HintSignal.NONE;
            }
            case COMPLETE_OBJECTIVE -> resolveObjectiveActionSignal(player, action, trackedQuestIds);
            case SET_QUEST_PHASE -> resolvePhaseActionSignal(player, action, trackedQuestIds);
            default -> HintSignal.NONE;
        };
    }

    private static HintSignal resolveObjectiveActionSignal(ServerPlayer player, DialogueAction action, List<String> trackedQuestIds) {
        String questId = DialogueAction.decodeQuestId(action.targetId());
        String objectiveId = DialogueAction.decodeObjectiveId(action.targetId());
        QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
        if (quest == null) {
            return HintSignal.NONE;
        }
        QuestRuntimeStatus runtimeStatus = QuestRuntimeService.status(player, quest);
        if (runtimeStatus != QuestRuntimeStatus.ACTIVE && runtimeStatus != QuestRuntimeStatus.READY_TO_TURN_IN) {
            return HintSignal.NONE;
        }
        QuestPhaseSpec phase = QuestRuntimeService.activePhaseForObjective(player, quest, objectiveId).orElse(null);
        QuestObjectiveSpec objective = phase == null ? null : phase.objective(objectiveId);
        if (objective == null || !QuestRuntimeService.isObjectiveActive(player, quest, objective)) {
            return HintSignal.NONE;
        }
        return signalForTargetedQuest(runtimeStatus, objective.hintType(), trackedQuestIds.contains(questId));
    }

    private static HintSignal resolvePhaseActionSignal(ServerPlayer player, DialogueAction action, List<String> trackedQuestIds) {
        String questId = DialogueAction.decodeQuestId(action.targetId());
        String targetPhaseId = DialogueAction.decodePhaseId(action.targetId());
        QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
        if (quest == null || QuestRuntimeService.status(player, quest) != QuestRuntimeStatus.ACTIVE) {
            return HintSignal.NONE;
        }
        if (quest.phaseMode() == com.phantasm.briefing.data.QuestPhaseMode.FREE) {
            return HintSignal.NONE;
        }
        QuestPhaseSpec currentPhase = QuestRuntimeService.currentPhase(player, quest).orElse(null);
        if (currentPhase == null || currentPhase.phaseId().equals(targetPhaseId) || !isForwardPhaseTransition(quest, currentPhase, targetPhaseId)) {
            return HintSignal.NONE;
        }
        return new HintSignal(QuestHintType.ADVANCE, QuestRuntimeStatus.ACTIVE, trackedQuestIds.contains(questId));
    }

    private static boolean isForwardPhaseTransition(QuestSpec quest, QuestPhaseSpec currentPhase, String targetPhaseId) {
        if (targetPhaseId == null || targetPhaseId.isBlank() || quest.phase(targetPhaseId) == null) {
            return false;
        }
        if (!currentPhase.nextPhaseId().isBlank()) {
            return currentPhase.nextPhaseId().equals(targetPhaseId);
        }
        int currentIndex = quest.phases().indexOf(currentPhase);
        return currentIndex >= 0
                && currentIndex + 1 < quest.phases().size()
                && quest.phases().get(currentIndex + 1).phaseId().equals(targetPhaseId);
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
            return HintSignal.NONE;
        }

        String questId = readString(condition, "questId");
        if (questId.isBlank()) {
            return HintSignal.NONE;
        }

        QuestRuntimeStatus runtimeStatus = QuestRuntimeService.status(player, questId);
        if (isPbConditionType(type, "quest_available")) {
            return runtimeStatus == QuestRuntimeStatus.AVAILABLE
                    ? signalForQuestStatus(runtimeStatus, trackedQuestIds.contains(questId))
                    : HintSignal.NONE;
        }
        if (isPbConditionType(type, "quest_ready_to_turn_in")) {
            return runtimeStatus == QuestRuntimeStatus.READY_TO_TURN_IN
                    ? signalForQuestStatus(runtimeStatus, trackedQuestIds.contains(questId))
                    : HintSignal.NONE;
        }
        if (isPbConditionType(type, "quest_accepted")
                || isPbConditionType(type, "quest_phase")
                || isPbConditionType(type, "quest_objective_completed")) {
            return (runtimeStatus == QuestRuntimeStatus.ACTIVE || runtimeStatus == QuestRuntimeStatus.READY_TO_TURN_IN)
                    ? signalForQuestStatus(runtimeStatus, trackedQuestIds.contains(questId))
                    : HintSignal.NONE;
        }
        return HintSignal.NONE;
    }

    private static boolean isPbConditionType(String type, String suffix) {
        return (BriefingConditionService.CURRENT_NAMESPACE + "pb_" + suffix).equals(type);
    }

    private static String readString(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) {
            return "";
        }
        return json.get(key).getAsString();
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
        // If one NPC participates in several quests, preserve the tracked quest's signal while
        // still resolving every quest normally. Otherwise an untracked COMPLETE signal could
        // replace a tracked ACTIVE signal before the final display filter and make the NPC vanish.
        if (right.tracked() != left.tracked()) {
            return right.tracked() ? right : left;
        }
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
        return left;
    }

    private static final class CandidateState {
        private final net.minecraft.server.level.ServerLevel level;
        private final Map<Integer, CandidateEntry> entities = new HashMap<>();
        private final List<CandidateEntry> probeOrder = new ArrayList<>();
        private boolean dirty = true;
        private boolean nearbyScanRequired = true;
        private boolean hasEvaluationPosition;
        private double lastPlayerX;
        private double lastPlayerY;
        private double lastPlayerZ;
        private long lastChunkPosition = Long.MIN_VALUE;
        private long bindingRevision = -1L;
        private int probeIndex;

        private CandidateState(net.minecraft.server.level.ServerLevel level) {
            this.level = level;
        }

        private boolean put(Entity entity) {
            CandidateEntry existing = this.entities.get(entity.getId());
            if (existing != null) {
                return existing.updateEntity(entity);
            }
            CandidateEntry entry = new CandidateEntry(entity);
            this.entities.put(entity.getId(), entry);
            this.probeOrder.add(entry);
            return true;
        }

        private boolean remove(int entityId) {
            CandidateEntry removed = this.entities.remove(entityId);
            if (removed == null) {
                return false;
            }
            this.probeOrder.remove(removed);
            if (this.probeIndex > this.probeOrder.size()) {
                this.probeIndex = 0;
            }
            return true;
        }

        private void removeInvalid(ServerPlayer player) {
            for (int index = this.probeOrder.size() - 1; index >= 0; index--) {
                CandidateEntry entry = this.probeOrder.get(index);
                Entity entity = entry.entity();
                if (entity.isRemoved() || !entity.isAlive() || entity.level() != player.serverLevel()) {
                    this.entities.remove(entity.getId());
                    this.probeOrder.remove(index);
                }
            }
            if (this.probeIndex >= this.probeOrder.size()) {
                this.probeIndex = 0;
            }
        }

        private boolean playerMovedEnough(ServerPlayer player) {
            double dx = player.getX() - this.lastPlayerX;
            double dy = player.getY() - this.lastPlayerY;
            double dz = player.getZ() - this.lastPlayerZ;
            return dx * dx + dy * dy + dz * dz >= PLAYER_RECHECK_DISTANCE_SQR;
        }

        private void captureEvaluationPosition(ServerPlayer player) {
            this.lastPlayerX = player.getX();
            this.lastPlayerY = player.getY();
            this.lastPlayerZ = player.getZ();
            this.hasEvaluationPosition = true;
        }

        /**
         * Checks at most one tracked quest NPC per player tick. This keeps moving NPC hints
         * responsive without a periodic full hint recomputation or an entity-range scan.
         */
        private boolean probeOneCandidateMovement(ServerPlayer player) {
            if (this.probeOrder.isEmpty()) {
                return false;
            }
            if (this.probeIndex >= this.probeOrder.size()) {
                this.probeIndex = 0;
            }
            CandidateEntry entry = this.probeOrder.get(this.probeIndex++);
            Entity entity = entry.entity();
            if (entity.isRemoved() || !entity.isAlive() || entity.level() != player.serverLevel()) {
                remove(entity.getId());
                return true;
            }
            return entry.captureIfMoved();
        }
    }

    private static final class CandidateEntry {
        private Entity entity;
        private double lastX;
        private double lastY;
        private double lastZ;

        private CandidateEntry(Entity entity) {
            this.entity = entity;
            capturePosition();
        }

        private Entity entity() {
            return this.entity;
        }

        private boolean updateEntity(Entity entity) {
            if (this.entity == entity) {
                return false;
            }
            this.entity = entity;
            capturePosition();
            return true;
        }

        private void capturePosition() {
            this.lastX = this.entity.getX();
            this.lastY = this.entity.getY();
            this.lastZ = this.entity.getZ();
        }

        private boolean captureIfMoved() {
            double dx = this.entity.getX() - this.lastX;
            double dy = this.entity.getY() - this.lastY;
            double dz = this.entity.getZ() - this.lastZ;
            if (dx * dx + dy * dy + dz * dz < NPC_RECHECK_DISTANCE_SQR) {
                return false;
            }
            capturePosition();
            return true;
        }
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
            boolean tracked,
            double distanceToSqr
    ) {
        private QuestHintTarget toTarget() {
            return new QuestHintTarget(entityId, type);
        }
    }
}
