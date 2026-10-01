package com.phantasm.briefing.client;

import com.phantasm.briefing.config.PhantasmBriefingClientConfig;
import com.phantasm.briefing.config.DialogueHudStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public final class DialogueFocusClient {
    private static final double MAX_FOCUS_DISTANCE = 6.0D;
    private static final double MAX_FOCUS_DISTANCE_SQUARED = MAX_FOCUS_DISTANCE * MAX_FOCUS_DISTANCE;
    // 与 DialogueFlowCoordinator 的有效会话距离保持一致；不改变首次对话的 6 格校验。
    private static final double MAX_ACTIVE_DIALOGUE_DISTANCE = 8.0D;
    private static final double MAX_ACTIVE_DIALOGUE_DISTANCE_SQUARED = MAX_ACTIVE_DIALOGUE_DISTANCE * MAX_ACTIVE_DIALOGUE_DISTANCE;
    private static final double TARGET_PADDING = 0.30D;

    private DialogueFocusClient() {
    }

    public static boolean usesGazeBubble(ClientDialogueSession session) {
        return PhantasmBriefingClientConfig.dialogueHudStyle() != DialogueHudStyle.DIALOGUE_BAR
                && session.hasSpeakerEntity();
    }

    public static boolean usesPortraitBubble(ClientDialogueSession session) {
        return PhantasmBriefingClientConfig.dialogueHudStyle() == DialogueHudStyle.PORTRAIT_BUBBLE
                && session.hasSpeakerEntity();
    }

    public static boolean usesMaidWorldBubble(ClientDialogueSession session) {
        return PhantasmBriefingClientConfig.dialogueHudStyle() == DialogueHudStyle.MAID_WORLD_BUBBLE
                && session.hasSpeakerEntity();
    }

    @Nullable
    public static Entity getSpeaker(Minecraft minecraft, ClientDialogueSession session) {
        if (!session.hasSpeakerEntity() || minecraft.level == null) {
            return null;
        }
        return minecraft.level.getEntity(session.getSpeakerEntityId());
    }

    /**
     * 对话开始前仍由准星命中 NPC 来触发；一旦会话已经建立，镜头可以自由移动。
     * 这样第一人称查看较高的图片气泡时不会因为准星离开 NPC 而让整段对话消失。
     * 活跃会话仍要求说话者存在、存活、处于同一世界且没有离玩家过远。
     */
    public static boolean canPresentActiveDialogue(Minecraft minecraft, ClientDialogueSession session) {
        if (!usesGazeBubble(session)) {
            return true;
        }
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return false;
        }

        Entity speaker = getSpeaker(minecraft, session);
        LocalPlayer player = minecraft.player;
        return speaker != null && speaker.isAlive() && speaker.level() == player.level()
                && player.distanceToSqr(speaker) <= MAX_ACTIVE_DIALOGUE_DISTANCE_SQUARED;
    }

    public static boolean isFocusedOnSpeaker(Minecraft minecraft, ClientDialogueSession session) {
        if (!session.hasSpeakerEntity() || minecraft.level == null || minecraft.player == null) {
            return false;
        }

        Entity speaker = getSpeaker(minecraft, session);
        LocalPlayer player = minecraft.player;
        if (speaker == null || !speaker.isAlive() || speaker.level() != player.level()) {
            return false;
        }
        if (player.distanceToSqr(speaker) > MAX_FOCUS_DISTANCE_SQUARED) {
            return false;
        }

        Vec3 eyePosition = player.getEyePosition();
        Vec3 lookEnd = eyePosition.add(player.getViewVector(1.0F).scale(MAX_FOCUS_DISTANCE));
        Optional<Vec3> entityHit = speaker.getBoundingBox()
                .inflate(TARGET_PADDING)
                .clip(eyePosition, lookEnd);
        if (entityHit.isEmpty()) {
            return false;
        }

        HitResult blockHit = minecraft.level.clip(new ClipContext(
                eyePosition,
                lookEnd,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));
        return blockHit.getType() == HitResult.Type.MISS
                || eyePosition.distanceToSqr(entityHit.get()) <= eyePosition.distanceToSqr(blockHit.getLocation()) + 0.0001D;
    }

    @Nullable
    public static Entity findFocusedLivingEntity(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            return null;
        }

        HitResult hitResult = ProjectileUtil.getHitResultOnViewVector(
                minecraft.player,
                entity -> entity instanceof LivingEntity && entity.isAlive() && entity.isPickable() && !entity.isSpectator(),
                MAX_FOCUS_DISTANCE
        );
        return hitResult instanceof EntityHitResult entityHitResult ? entityHitResult.getEntity() : null;
    }
}
