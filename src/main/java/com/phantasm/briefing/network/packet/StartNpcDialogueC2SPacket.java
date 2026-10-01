package com.phantasm.briefing.network.packet;

import com.phantasm.briefing.service.DialogueFlowCoordinator;
import com.phantasm.briefing.service.NpcPrerequisiteDialogueService;
import com.phantasm.briefing.service.QuestRuntimeService;
import com.phantasm.briefing.util.QuestNpcHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

public record StartNpcDialogueC2SPacket(int entityId) {
    private static final double MAX_DIALOGUE_DISTANCE = 6.0D;
    private static final double MAX_DIALOGUE_DISTANCE_SQUARED = MAX_DIALOGUE_DISTANCE * MAX_DIALOGUE_DISTANCE;

    public static void encode(StartNpcDialogueC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entityId());
    }

    public static StartNpcDialogueC2SPacket decode(FriendlyByteBuf buffer) {
        return new StartNpcDialogueC2SPacket(buffer.readVarInt());
    }

    public static void handle(StartNpcDialogueC2SPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player == null) {
                return;
            }

            Entity target = player.serverLevel().getEntity(packet.entityId());
            if (!(target instanceof LivingEntity)
                    || !target.isAlive()
                    || player.distanceToSqr(target) > MAX_DIALOGUE_DISTANCE_SQUARED
                    || !QuestNpcHelper.isQuestNPC(target)
                    || !isTargetUnderCrosshair(player, target)) {
                return;
            }

            String questNodeId = QuestNpcHelper.resolveQuestNodeId(target);
            String npcIdentity = QuestNpcHelper.getNpcIdentity(target);
            ResourceLocation entityTypeKey = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
            QuestRuntimeService.onEntityInteracted(
                    player,
                    entityTypeKey == null ? "" : entityTypeKey.toString(),
                    questNodeId,
                    npcIdentity
            );
            if (questNodeId.isBlank()) {
                return;
            }
            if (NpcPrerequisiteDialogueService.openLockedNoticeIfNeeded(player, target)) {
                return;
            }
            DialogueFlowCoordinator.openBestNodeForPlayer(player, questNodeId, target);
        });
        context.setPacketHandled(true);
    }

    private static boolean isTargetUnderCrosshair(ServerPlayer player, Entity requestedTarget) {
        HitResult hitResult = ProjectileUtil.getHitResultOnViewVector(
                player,
                entity -> entity instanceof LivingEntity && entity.isAlive() && entity.isPickable() && !entity.isSpectator(),
                MAX_DIALOGUE_DISTANCE
        );
        return hitResult instanceof EntityHitResult entityHitResult
                && entityHitResult.getEntity().getId() == requestedTarget.getId();
    }
}
