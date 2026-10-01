package com.phantasm.briefing.event;

import com.phantasm.briefing.service.DialogueFlowCoordinator;
import com.phantasm.briefing.service.NpcPrerequisiteDialogueService;
import com.phantasm.briefing.service.QuestRuntimeService;
import com.phantasm.briefing.service.MobKillClassifier;
import com.phantasm.briefing.util.QuestNpcHelper;
import com.phantasm.briefing.util.EntitySourceUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

public final class QuestObjectiveProgressEvents {
    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        Entity sourceEntity = event.getSource().getEntity();
        ServerPlayer player = EntitySourceUtil.resolveServerPlayer(sourceEntity);
        if (player == null) {
            return;
        }

        ResourceLocation entityTypeKey = ForgeRegistries.ENTITY_TYPES.getKey(event.getEntity().getType());
        if (entityTypeKey != null) {
            QuestRuntimeService.onEntityKilled(player, entityTypeKey.toString(),
                    MobKillClassifier.classify(event.getEntity()));
        }
    }

    @SubscribeEvent(receiveCanceled = true)
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.level().isClientSide()
                || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        Entity target = event.getTarget();
        ResourceLocation entityTypeKey = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
        String questNodeId = QuestNpcHelper.resolveQuestNodeId(target);
        String npcIdentity = QuestNpcHelper.getNpcIdentity(target);

        boolean openedDialogue = false;
        if (!event.isCanceled() && QuestNpcHelper.isQuestNPC(target) && !questNodeId.isBlank()) {
            openedDialogue = NpcPrerequisiteDialogueService.openLockedNoticeIfNeeded(player, target)
                    || DialogueFlowCoordinator.openBestNodeForPlayer(player, questNodeId, target);
        }

        QuestRuntimeService.onEntityInteracted(
                player,
                entityTypeKey == null ? "" : entityTypeKey.toString(),
                questNodeId,
                npcIdentity
        );

        if (openedDialogue) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        ResourceLocation itemKey = ForgeRegistries.ITEMS.getKey(event.getCrafting().getItem());
        if (itemKey != null && !event.getCrafting().isEmpty()) {
            QuestRuntimeService.onItemCrafted(player, itemKey.toString(), event.getCrafting().getCount());
            QuestRuntimeService.markInventoryChanged(player);
        }
    }

    @SubscribeEvent
    public void onItemPickup(PlayerEvent.ItemPickupEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            QuestRuntimeService.markInventoryChanged(player);
        }
    }

    @SubscribeEvent
    public void onItemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            QuestRuntimeService.markInventoryChanged(player);
        }
    }

    @SubscribeEvent
    public void onContainerClosed(net.minecraftforge.event.entity.player.PlayerContainerEvent.Close event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            QuestRuntimeService.markInventoryChanged(player);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        QuestRuntimeService.tickAutomaticObjectives(player);
    }

}
