package com.phantasm.briefing.service;

import com.mojang.logging.LogUtils;
import com.phantasm.briefing.data.DialogueAction;
import com.phantasm.briefing.data.DialogueActionType;
import com.phantasm.briefing.data.DialogueDataManager;
import com.phantasm.briefing.data.DialogueNode;
import com.phantasm.briefing.data.DialogueOption;
import com.phantasm.briefing.integration.FTBIntegrationHelper;
import com.phantasm.briefing.network.ModNetwork;
import com.phantasm.briefing.network.packet.OpenDialogueNodeS2CPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DialogueFlowCoordinator {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<UUID, String> ACTIVE_NODE_BY_PLAYER = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> ACTIVE_ENTITY_BY_PLAYER = new ConcurrentHashMap<>();

    private DialogueFlowCoordinator() {
    }

    public static boolean openNodeForPlayer(ServerPlayer player, String nodeId) {
        return openNodeForPlayer(player, nodeId, null);
    }

    public static boolean openNodeForPlayer(ServerPlayer player, String nodeId, @Nullable Entity contextEntity) {
        return DialogueDataManager.getInstance().getNode(nodeId)
                .map(node -> openNodeForPlayer(player, node, contextEntity))
                .orElse(false);
    }

    public static boolean canOpenNodeForPlayer(ServerPlayer player, String nodeId, @Nullable Entity contextEntity) {
        return DialogueDataManager.getInstance().getNode(nodeId)
                .map(node -> canOpenNodeForPlayer(player, node, contextEntity))
                .orElse(false);
    }

    public static boolean openDialogueForPlayer(ServerPlayer player, String dialogueId) {
        return openDialogueForPlayer(player, dialogueId, null);
    }

    public static boolean openDialogueForPlayer(ServerPlayer player, String dialogueId, @Nullable Entity contextEntity) {
        return DialogueDataManager.getInstance().getDialogue(dialogueId)
                .map(dialogue -> openNodeForPlayer(player, dialogue.getScopedStartNodeId(), contextEntity))
                .orElse(false);
    }

    public static boolean openNodeForPlayer(ServerPlayer player, DialogueNode node, @Nullable Entity contextEntity) {
        Entity resolvedContextEntity = contextEntity != null ? contextEntity : resolveContextEntity(player);
        if (!canOpenNodeForPlayer(player, node, resolvedContextEntity)) {
            return false;
        }

        updateContextEntity(player, contextEntity);
        List<DialogueOption> availableOptions = getAvailableOptions(player, node, resolvedContextEntity);
        ACTIVE_NODE_BY_PLAYER.put(player.getUUID(), node.nodeId());
        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                OpenDialogueNodeS2CPacket.from(
                        node,
                        availableOptions,
                        resolvedContextEntity == null ? -1 : resolvedContextEntity.getId()
                )
        );
        return true;
    }

    public static boolean canOpenNodeForPlayer(ServerPlayer player, DialogueNode node, @Nullable Entity contextEntity) {
        if (!node.requiredQuestId().isBlank() && !QuestRuntimeService.isQuestCompleted(player, node.requiredQuestId())) {
            LOGGER.info("[PhantasmBriefing] Player {} does not meet prerequisite quest '{}' for node '{}'", player.getGameProfile().getName(), node.requiredQuestId(), node.nodeId());
            return false;
        }
        if (!BriefingConditionService.all(player, contextEntity, node.conditions())) {
            LOGGER.info("[PhantasmBriefing] Player {} does not meet conditions for node '{}'", player.getGameProfile().getName(), node.nodeId());
            return false;
        }
        return true;
    }

    public static void handleNodeFinished(ServerPlayer player, String nodeId) {
        String expectedNodeId = ACTIVE_NODE_BY_PLAYER.get(player.getUUID());
        if (expectedNodeId == null || !expectedNodeId.equals(nodeId)) {
            LOGGER.warn("[PhantasmBriefing] Ignoring out-of-sync dialogue completion from {}: expected '{}', got '{}'", player.getGameProfile().getName(), expectedNodeId, nodeId);
            return;
        }

        DialogueDataManager.getInstance().getNode(nodeId).ifPresentOrElse(node -> {
            handleDefaultNodeCompletion(player, node);
        }, () -> {
            ACTIVE_NODE_BY_PLAYER.remove(player.getUUID());
            LOGGER.warn("[PhantasmBriefing] Dialogue node '{}' finished by player {} but no longer exists", nodeId, player.getGameProfile().getName());
        });
    }

    public static void handleOptionSelected(ServerPlayer player, String nodeId, String optionId) {
        String expectedNodeId = ACTIVE_NODE_BY_PLAYER.get(player.getUUID());
        if (expectedNodeId == null || !expectedNodeId.equals(nodeId)) {
            LOGGER.warn("[PhantasmBriefing] Ignoring out-of-sync dialogue option selection from {}: expected '{}', got '{}'", player.getGameProfile().getName(), expectedNodeId, nodeId);
            return;
        }

        DialogueDataManager.getInstance().getNode(nodeId).ifPresentOrElse(node -> {
            Optional<DialogueOption> selectedOption = node.options().stream()
                    .filter(option -> option.optionId().equals(optionId))
                    .findFirst();
            if (selectedOption.isEmpty()) {
                LOGGER.warn("[PhantasmBriefing] Dialogue option '{}' not found on node '{}'", optionId, nodeId);
                ACTIVE_NODE_BY_PLAYER.remove(player.getUUID());
                return;
            }

            DialogueOption option = selectedOption.get();
            Entity contextEntity = resolveContextEntity(player);
            if (!option.requiredQuestId().isBlank() && !QuestRuntimeService.isQuestCompleted(player, option.requiredQuestId())) {
                LOGGER.warn("[PhantasmBriefing] Player {} selected locked option '{}' on node '{}'", player.getGameProfile().getName(), optionId, nodeId);
                ACTIVE_NODE_BY_PLAYER.remove(player.getUUID());
                ACTIVE_ENTITY_BY_PLAYER.remove(player.getUUID());
                return;
            }
            if (!BriefingConditionService.all(player, contextEntity, option.conditions())) {
                LOGGER.warn("[PhantasmBriefing] Player {} selected condition-locked option '{}' on node '{}'", player.getGameProfile().getName(), optionId, nodeId);
                ACTIVE_NODE_BY_PLAYER.remove(player.getUUID());
                ACTIVE_ENTITY_BY_PLAYER.remove(player.getUUID());
                return;
            }

            executeActions(player, option.actions(), nodeId);
        }, () -> {
            ACTIVE_NODE_BY_PLAYER.remove(player.getUUID());
            LOGGER.warn("[PhantasmBriefing] Dialogue node '{}' received option '{}' from {} but no longer exists", nodeId, optionId, player.getGameProfile().getName());
        });
    }

    public static void clearPlayerSession(UUID playerId) {
        ACTIVE_NODE_BY_PLAYER.remove(playerId);
        ACTIVE_ENTITY_BY_PLAYER.remove(playerId);
    }

    private static List<DialogueOption> getAvailableOptions(ServerPlayer player, DialogueNode node, @Nullable Entity contextEntity) {
        if (node.options().isEmpty()) {
            return List.of();
        }

        return node.options().stream()
                .filter(option -> option.requiredQuestId().isBlank() || QuestRuntimeService.isQuestCompleted(player, option.requiredQuestId()))
                .filter(option -> BriefingConditionService.all(player, contextEntity, option.conditions()))
                .toList();
    }

    private static void handleDefaultNodeCompletion(ServerPlayer player, DialogueNode node) {
        Entity contextEntity = resolveContextEntity(player);
        if (!node.ftbQuestIdToComplete().isBlank()) {
            FTBIntegrationHelper.completeQuestSilently(player, node.ftbQuestIdToComplete());
        }

        if (!node.nextNodeId().isBlank()) {
            DialogueDataManager.getInstance().getNode(node.nextNodeId()).ifPresentOrElse(nextNode -> openNodeForPlayer(player, nextNode, contextEntity), () -> {
                ACTIVE_NODE_BY_PLAYER.remove(player.getUUID());
                LOGGER.warn("[PhantasmBriefing] Next dialogue node '{}' not found for '{}'", node.nextNodeId(), node.nodeId());
            });
        } else {
            ACTIVE_NODE_BY_PLAYER.remove(player.getUUID());
            ACTIVE_ENTITY_BY_PLAYER.remove(player.getUUID());
        }

        QuestTrackerService.syncToClient(player);
        QuestEntityHintService.syncToClient(player);
    }

    private static void executeActions(ServerPlayer player, List<DialogueAction> actions, String sourceNodeId) {
        boolean openedNextNode = false;
        Entity contextEntity = resolveContextEntity(player);

        for (DialogueAction action : actions) {
            if (action.type() == DialogueActionType.COMPLETE_QUEST) {
                if (!QuestRuntimeService.complete(player, action.targetId())) {
                    FTBIntegrationHelper.completeQuestSilently(player, action.targetId());
                }
                continue;
            }

            if (action.type() == DialogueActionType.COMPLETE_OBJECTIVE) {
                QuestRuntimeService.completeObjective(
                        player,
                        DialogueAction.decodeQuestId(action.targetId()),
                        DialogueAction.decodeObjectiveId(action.targetId())
                );
                continue;
            }

            if (action.type() == DialogueActionType.START_QUEST) {
                QuestRuntimeService.accept(player, action.targetId());
                continue;
            }

            if (action.type() == DialogueActionType.SET_QUEST_PHASE) {
                QuestRuntimeService.setPhase(
                        player,
                        DialogueAction.decodeQuestId(action.targetId()),
                        DialogueAction.decodePhaseId(action.targetId())
                );
                continue;
            }

            if (action.type() == DialogueActionType.OPEN_NODE) {
                DialogueDataManager.getInstance().getNode(action.targetId()).ifPresentOrElse(nextNode -> openNodeForPlayer(player, nextNode, contextEntity), () ->
                        LOGGER.warn("[PhantasmBriefing] Action target node '{}' not found for '{}'", action.targetId(), sourceNodeId)
                );
                openedNextNode = true;
                continue;
            }

            if (action.type() == DialogueActionType.OPEN_SHOP) {
                handleOpenShop(player, action.targetId(), sourceNodeId);
                continue;
            }

            if (action.type() == DialogueActionType.SET_FLAG) {
                PlayerFlagService.setFlag(player, action.targetId());
                continue;
            }

            if (action.type() == DialogueActionType.CLOSE_DIALOGUE) {
                continue;
            }
        }

        if (!openedNextNode) {
            ACTIVE_NODE_BY_PLAYER.remove(player.getUUID());
            ACTIVE_ENTITY_BY_PLAYER.remove(player.getUUID());
        }
        QuestTrackerService.syncToClient(player);
        QuestEntityHintService.syncToClient(player);
    }

    private static void updateContextEntity(ServerPlayer player, @Nullable Entity contextEntity) {
        if (contextEntity == null) {
            ACTIVE_ENTITY_BY_PLAYER.remove(player.getUUID());
            return;
        }
        ACTIVE_ENTITY_BY_PLAYER.put(player.getUUID(), contextEntity.getUUID());
    }

    @Nullable
    private static Entity resolveContextEntity(ServerPlayer player) {
        UUID entityId = ACTIVE_ENTITY_BY_PLAYER.get(player.getUUID());
        if (entityId == null) {
            return null;
        }

        for (ServerLevel level : player.getServer().getAllLevels()) {
            Entity entity = level.getEntity(entityId);
            if (entity != null) {
                return entity;
            }
        }
        ACTIVE_ENTITY_BY_PLAYER.remove(player.getUUID());
        return null;
    }

    private static void handleOpenShop(ServerPlayer player, String shopId, String sourceNodeId) {
        if (DialogueShopService.openShop(player, shopId)) {
            LOGGER.info("[PhantasmBriefing] open_shop executed for player {} on node '{}' with targetId '{}'", player.getGameProfile().getName(), sourceNodeId, shopId);
            return;
        }

        LOGGER.warn("[PhantasmBriefing] Failed to open shop/trade '{}' for player {} on node '{}'", shopId, player.getGameProfile().getName(), sourceNodeId);
        player.sendSystemMessage(Component.literal("[PhantasmBriefing] 无法打开商店或交易：id=" + shopId));
    }
}
