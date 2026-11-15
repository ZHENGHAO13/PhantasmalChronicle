package com.phantasm.briefing.event;

import com.phantasm.briefing.service.QuestRuntimeService;
import com.phantasm.briefing.util.QuestNpcHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

public final class QuestObjectiveProgressEvents {
    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        Entity sourceEntity = event.getSource().getEntity();
        ServerPlayer player = resolvePlayer(sourceEntity);
        if (player == null) {
            return;
        }

        ResourceLocation entityTypeKey = ForgeRegistries.ENTITY_TYPES.getKey(event.getEntity().getType());
        if (entityTypeKey != null) {
            QuestRuntimeService.onEntityKilled(player, entityTypeKey.toString());
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
        QuestRuntimeService.onEntityInteracted(
                player,
                entityTypeKey == null ? "" : entityTypeKey.toString(),
                questNodeId
        );
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        if (player.tickCount % 20 == 0) {
            QuestRuntimeService.evaluateAutomaticObjectives(player);
        }
    }

    private static ServerPlayer resolvePlayer(Entity sourceEntity) {
        if (sourceEntity instanceof ServerPlayer player) {
            return player;
        }
        if (sourceEntity instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer player) {
            return player;
        }
        return null;
    }
}
