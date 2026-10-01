package com.phantasm.briefing.event;

import com.phantasm.briefing.api.IQuestNPC;
import com.phantasm.briefing.data.NpcBindingDataManager;
import com.phantasm.briefing.util.QuestNpcHelper;
import com.phantasm.briefing.util.EntitySourceUtil;
import com.phantasm.briefing.service.DialogueFlowCoordinator;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class QuestNpcProtectionEvents {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (!QuestNpcHelper.isQuestNPC(target)) {
            return;
        }

        ServerPlayer attacker = EntitySourceUtil.resolveServerPlayer(event.getSource().getEntity());
        if (attacker != null) {
            String questNodeId = QuestNpcHelper.resolveQuestNodeId(target);
            if (!questNodeId.isBlank()) {
                DialogueFlowCoordinator.openTriggeredNodeForPlayer(
                        attacker,
                        questNodeId,
                        target,
                        "player_attack"
                );
            }
        }
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingDamage(LivingDamageEvent event) {
        if (QuestNpcHelper.isQuestNPC(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof IQuestNPC) {
            return;
        }

        if (!QuestNpcHelper.hasDirectQuestNpcMarker(entity)
                && !NpcBindingDataManager.getInstance().mayMatchEntityType(entity)) {
            return;
        }

        if (!QuestNpcHelper.isQuestNPC(entity)) {
            return;
        }

        // 职业村民保留原版 AI，以维持职业与第三方交易逻辑。
        if (entity instanceof Villager || QuestNpcHelper.usesNativeAi(entity)) {
            return;
        }

        entity.setNoGravity(true);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.hasImpulse = false;
        entity.fallDistance = 0.0F;

        if (entity instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.setTarget(null);
        }
    }


}
