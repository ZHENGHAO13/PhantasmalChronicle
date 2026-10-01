package com.phantasm.briefing.service;

import com.phantasm.briefing.data.QuestDataManager;
import com.phantasm.briefing.data.QuestMarkerSpec;
import com.phantasm.briefing.data.QuestObjectiveSpec;
import com.phantasm.briefing.data.QuestObjectiveType;
import com.phantasm.briefing.data.QuestSpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Inventory/location objective scheduler and evaluator. */
final class QuestAutomaticObjectiveService {
    private static final int FALLBACK_TICKS = 100;
    private static final int VISIT_MOVE_CHECK_TICKS = 20;
    private static final double VISIT_MOVE_DISTANCE_SQR = 1.0E-6D;
    private static final Map<UUID, AutomaticInterest> INTEREST_BY_PLAYER = new HashMap<>();
    private static final Set<UUID> PENDING_INVENTORY = new HashSet<>();
    private static final Set<UUID> PENDING_REFRESH = new HashSet<>();

    private QuestAutomaticObjectiveService() {
    }

    static void invalidate(ServerPlayer player) {
        if (player == null) return;
        INTEREST_BY_PLAYER.remove(player.getUUID());
        PENDING_REFRESH.add(player.getUUID());
        ManualVisibilityService.refreshUnlocks(player);
    }

    static void clear(ServerPlayer player) {
        if (player == null) return;
        UUID id = player.getUUID();
        INTEREST_BY_PLAYER.remove(id);
        PENDING_INVENTORY.remove(id);
        PENDING_REFRESH.remove(id);
    }

    static void clearAll() {
        INTEREST_BY_PLAYER.clear();
        PENDING_INVENTORY.clear();
        PENDING_REFRESH.clear();
    }

    static void markInventoryChanged(ServerPlayer player) {
        if (player != null && automaticInterest(player).hasCollect) {
            PENDING_INVENTORY.add(player.getUUID());
        }
    }

    static void tick(ServerPlayer player) {
        UUID id = player.getUUID();
        if (player.tickCount % VISIT_MOVE_CHECK_TICKS != 0
                && !PENDING_INVENTORY.contains(id)
                && !PENDING_REFRESH.contains(id)) {
            return;
        }
        boolean refreshRequested = PENDING_REFRESH.remove(id);
        boolean inventoryChanged = PENDING_INVENTORY.remove(id);
        AutomaticInterest old = INTEREST_BY_PLAYER.get(id);
        boolean fallbackDue = old != null && player.tickCount >= old.nextFallbackTick;
        AutomaticInterest interest = fallbackDue ? null : old;
        if (interest == null) {
            interest = resolveInterest(player);
            INTEREST_BY_PLAYER.put(id, interest);
        }
        if (!interest.hasCollect && !interest.hasVisit) return;

        boolean moved = interest.hasVisit && (interest.level != player.serverLevel()
                || horizontalDistanceSqr(player.getX(), player.getZ(), interest.lastX, interest.lastZ)
                >= VISIT_MOVE_DISTANCE_SQR);
        if (refreshRequested || AutomaticObjectiveCheckPolicy.shouldEvaluate(
                interest.hasCollect, interest.hasVisit, inventoryChanged, moved, fallbackDue)) {
            interest.lastX = player.getX();
            interest.lastZ = player.getZ();
            interest.level = player.serverLevel();
            evaluate(player);
        }
    }

    static void evaluate(ServerPlayer player) {
        Map<String, Integer> inventoryCounts = null;
        boolean changed;
        int safety = 16;
        do {
            changed = false;
            outer:
            for (String questId : List.copyOf(BriefingPlayerData.activeQuestIds(player))) {
                QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
                if (quest == null) continue;
                for (QuestObjectiveSpec objective : QuestRuntimeService.activeObjectives(player, quest)) {
                    if (objective.objectiveType() == QuestObjectiveType.COLLECT && !objective.targetId().isBlank()) {
                        if (inventoryCounts == null) inventoryCounts = buildInventoryCounts(player);
                        if (inventoryCounts.getOrDefault(objective.targetId(), 0) >= objective.requiredCount()
                                && QuestRuntimeService.completeObjective(player, quest.questId(), objective.objectiveId())) {
                            changed = true;
                            break outer;
                        }
                    }
                    if (objective.objectiveType() == QuestObjectiveType.VISIT
                            && hasReachedVisitTarget(player, quest, objective)
                            && QuestRuntimeService.completeObjective(player, quest.questId(), objective.objectiveId())) {
                        changed = true;
                        break outer;
                    }
                }
            }
        } while (changed && --safety > 0);
    }

    private static AutomaticInterest automaticInterest(ServerPlayer player) {
        return INTEREST_BY_PLAYER.computeIfAbsent(player.getUUID(), ignored -> resolveInterest(player));
    }

    private static AutomaticInterest resolveInterest(ServerPlayer player) {
        boolean collect = false;
        boolean visit = false;
        for (String questId : BriefingPlayerData.activeQuestIds(player)) {
            QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
            for (QuestObjectiveSpec objective : QuestRuntimeService.activeObjectives(player, quest)) {
                collect |= objective.objectiveType() == QuestObjectiveType.COLLECT && !objective.targetId().isBlank();
                visit |= objective.objectiveType() == QuestObjectiveType.VISIT;
                if (collect && visit) break;
            }
            if (collect && visit) break;
        }
        return new AutomaticInterest(collect, visit, player.serverLevel(),
                player.getX(), player.getZ(), player.tickCount + FALLBACK_TICKS);
    }

    private static double horizontalDistanceSqr(double x, double z, double lastX, double lastZ) {
        double dx = x - lastX;
        double dz = z - lastZ;
        return dx * dx + dz * dz;
    }

    private static Map<String, Integer> buildInventoryCounts(ServerPlayer player) {
        Map<String, Integer> counts = new HashMap<>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            ResourceLocation itemKey = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (itemKey != null) counts.merge(itemKey.toString(), stack.getCount(), Integer::sum);
        }
        return counts;
    }

    private static boolean hasReachedVisitTarget(ServerPlayer player, QuestSpec quest, QuestObjectiveSpec objective) {
        QuestMarkerSpec marker = StructureSearchCompatService.resolveMarker(player, quest, objective);
        if (marker == null) return false;
        if (!player.serverLevel().dimension().location().toString().equals(marker.dimension())) return false;
        double dx = player.getX() - marker.x();
        double dz = player.getZ() - marker.z();
        double radius = objective.radius();
        return (dx * dx) + (dz * dz) <= radius * radius;
    }

    private static final class AutomaticInterest {
        private final boolean hasCollect;
        private final boolean hasVisit;
        private ServerLevel level;
        private double lastX;
        private double lastZ;
        private final int nextFallbackTick;

        private AutomaticInterest(boolean hasCollect, boolean hasVisit, ServerLevel level,
                                  double lastX, double lastZ, int nextFallbackTick) {
            this.hasCollect = hasCollect;
            this.hasVisit = hasVisit;
            this.level = level;
            this.lastX = lastX;
            this.lastZ = lastZ;
            this.nextFallbackTick = nextFallbackTick;
        }
    }
}
