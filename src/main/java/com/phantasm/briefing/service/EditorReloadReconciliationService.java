package com.phantasm.briefing.service;

import com.phantasm.briefing.data.DialogueDataManager;
import com.phantasm.briefing.data.DialogueNode;
import com.phantasm.briefing.data.NpcBindingDataManager;
import com.phantasm.briefing.data.QuestDataManager;
import com.phantasm.briefing.data.QuestSpec;
import com.phantasm.briefing.event.NpcStructureSpawnEvents;
import com.phantasm.briefing.util.QuestNpcHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Reconciles runtime state after a Phantasm Maker hot reload.
 *
 * <p>Deletion cleanup is intentionally driven by the explicit save manifest sent by the
 * editor. A registry entry that merely fails to parse must never be treated as a user
 * deletion; this keeps content safe while a schema migration is being validated.</p>
 */
public final class EditorReloadReconciliationService {
    private EditorReloadReconciliationService() {
    }

    public static Snapshot capture() {
        return new Snapshot(
                QuestDataManager.getInstance().getAllQuests().stream()
                        .map(QuestSpec::questId)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                DialogueDataManager.getInstance().getAllNodes().stream()
                        .map(DialogueNode::nodeId)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                Set.copyOf(NpcBindingDataManager.getInstance().getBindingIds())
        );
    }

    public static Result reconcile(
            MinecraftServer server,
            Snapshot before,
            @Nullable SaveManifest manifest,
            boolean runOrphanSweep
    ) {
        if (server == null || before == null || manifest == null) {
            return Result.EMPTY;
        }

        Set<String> currentQuestIds = QuestDataManager.getInstance().getAllQuests().stream()
                .map(QuestSpec::questId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<String> currentNodeIds = DialogueDataManager.getInstance().getAllNodes().stream()
                .map(DialogueNode::nodeId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<String> currentBindingIds = Set.copyOf(NpcBindingDataManager.getInstance().getBindingIds());

        Set<String> removedQuestIds = confirmedRemoved(before.questIds(), manifest.questIds(), currentQuestIds);
        Set<String> removedNodeIds = confirmedRemoved(before.dialogueNodeIds(), manifest.dialogueNodeIds(), currentNodeIds);
        Set<String> removedBindingIds = confirmedRemoved(before.npcBindingIds(), manifest.npcBindingIds(), currentBindingIds);

        NpcStructureSpawnService.RemovedBindingCleanup removedBindingCleanup =
                NpcStructureSpawnService.purgeRemovedBindings(server, removedBindingIds);
        NpcStructureSpawnService.RemovedBindingCleanup orphanCleanup = runOrphanSweep
                ? NpcStructureSpawnService.purgeOrphanedManagedNpcs(server, manifest.npcBindingIds())
                : new NpcStructureSpawnService.RemovedBindingCleanup(0, 0, 0);
        NpcStructureSpawnService.RemovedBindingCleanup npcCleanup = new NpcStructureSpawnService.RemovedBindingCleanup(
                removedBindingCleanup.discardedEntities() + orphanCleanup.discardedEntities(),
                removedBindingCleanup.removedSpawnRecords() + orphanCleanup.removedSpawnRecords(),
                removedBindingCleanup.removedCompletionRecords() + orphanCleanup.removedCompletionRecords()
        );

        int staleDirectQuestNpcAssignments = runOrphanSweep
                ? clearStaleDirectQuestNpcAssignments(server)
                : 0;
        int purgedQuestStates = 0;
        int closedDialogueSessions = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            purgedQuestStates += QuestRuntimeService.purgeRemovedQuestData(player, removedQuestIds);
            if (runOrphanSweep) {
                purgedQuestStates += QuestRuntimeService.purgeOrphanedQuestData(player, manifest.questIds());
            }
            if (DialogueFlowCoordinator.closeSessionForRemovedNodes(player, removedNodeIds)) {
                closedDialogueSessions++;
            }
            if (!removedBindingIds.isEmpty()
                    || npcCleanup.discardedEntities() > 0
                    || npcCleanup.removedSpawnRecords() > 0
                    || npcCleanup.removedCompletionRecords() > 0
                    || staleDirectQuestNpcAssignments > 0) {
                BriefingPlayerData.clearCompletedNpcStructures(player);
                NpcStructureSpawnEvents.invalidatePlayer(player);
            }
        }

        return new Result(
                removedQuestIds,
                removedNodeIds,
                removedBindingIds,
                purgedQuestStates,
                closedDialogueSessions,
                staleDirectQuestNpcAssignments,
                npcCleanup
        );
    }

    private static int clearStaleDirectQuestNpcAssignments(MinecraftServer server) {
        int cleared = 0;
        for (var level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof LivingEntity livingEntity
                        && QuestNpcHelper.clearStaleDirectQuestNode(livingEntity)) {
                    cleared++;
                }
            }
        }
        return cleared;
    }

    /**
     * An ID is destructive-cleanup eligible only when the editor explicitly omitted it from
     * the saved manifest AND the freshly loaded registry confirms that it is actually gone.
     * This double gate prevents parse or migration failures from being mistaken for deletions.
     */
    private static Set<String> confirmedRemoved(Set<String> before, Set<String> editorManifest, Set<String> after) {
        if (before == null || before.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> intended = new LinkedHashSet<>(before);
        intended.removeAll(editorManifest == null ? Set.of() : editorManifest);
        if (intended.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> actuallyMissing = new LinkedHashSet<>(before);
        actuallyMissing.removeAll(after == null ? Set.of() : after);
        intended.retainAll(actuallyMissing);
        return Set.copyOf(intended);
    }

    private static Set<String> normalizeIds(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                normalized.add(value.trim());
            }
        }
        return Set.copyOf(normalized);
    }

    public record Snapshot(
            Set<String> questIds,
            Set<String> dialogueNodeIds,
            Set<String> npcBindingIds
    ) {
    }

    /** Current logical IDs from one successful editor save. */
    public record SaveManifest(
            Set<String> questIds,
            Set<String> dialogueNodeIds,
            Set<String> npcBindingIds
    ) {
        public SaveManifest {
            questIds = normalizeIds(questIds);
            dialogueNodeIds = normalizeIds(dialogueNodeIds);
            npcBindingIds = normalizeIds(npcBindingIds);
        }
    }

    public record Result(
            Set<String> removedQuestIds,
            Set<String> removedDialogueNodeIds,
            Set<String> removedNpcBindingIds,
            int purgedQuestStates,
            int closedDialogueSessions,
            int staleDirectQuestNpcAssignments,
            NpcStructureSpawnService.RemovedBindingCleanup npcCleanup
    ) {
        private static final Result EMPTY = new Result(
                Set.of(), Set.of(), Set.of(), 0, 0, 0,
                new NpcStructureSpawnService.RemovedBindingCleanup(0, 0, 0)
        );

        public boolean hasRemovals() {
            return !this.removedQuestIds.isEmpty()
                    || !this.removedDialogueNodeIds.isEmpty()
                    || !this.removedNpcBindingIds.isEmpty()
                    || this.staleDirectQuestNpcAssignments > 0
                    || this.npcCleanup.discardedEntities() > 0
                    || this.npcCleanup.removedSpawnRecords() > 0
                    || this.npcCleanup.removedCompletionRecords() > 0;
        }
    }
}
