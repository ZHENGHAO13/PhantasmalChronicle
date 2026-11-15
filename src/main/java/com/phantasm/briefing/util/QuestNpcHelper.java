package com.phantasm.briefing.util;

import com.phantasm.briefing.api.IQuestNPC;
import com.phantasm.briefing.capability.ModCapabilities;
import com.phantasm.briefing.capability.QuestNpcData;
import com.phantasm.briefing.data.NpcBindingDataManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public final class QuestNpcHelper {
    private static final String TAG_NPC_IDENTITY = "PhantasmBriefingNpcIdentity";
    private static final String TAG_USE_NATIVE_AI = "PhantasmBriefingNpcUseNativeAi";

    private QuestNpcHelper() {
    }

    public static boolean isQuestNPC(@Nullable Entity entity) {
        return !resolveQuestNodeId(entity).isBlank();
    }

    public static boolean isCapabilityQuestNPC(@Nullable Entity entity) {
        return entity instanceof LivingEntity livingEntity
                && !(entity instanceof IQuestNPC)
                && getQuestNpcData(livingEntity).map(QuestNpcData::isQuestNpc).orElse(false);
    }

    public static String getQuestNodeId(@Nullable Entity entity) {
        if (entity instanceof IQuestNPC questNpc) {
            return sanitizeQuestNodeId(questNpc.getQuestNodeId());
        }

        if (entity instanceof LivingEntity livingEntity) {
            return getQuestNpcData(livingEntity).map(QuestNpcData::getQuestNodeId).orElse("");
        }

        return "";
    }

    public static String resolveQuestNodeId(@Nullable Entity entity) {
        String directNodeId = getQuestNodeId(entity);
        if (!directNodeId.isBlank()) {
            return directNodeId;
        }

        if (entity == null) {
            return "";
        }

        return NpcBindingDataManager.getInstance()
                .findBinding(entity)
                .map(binding -> sanitizeQuestNodeId(binding.questNodeId()))
                .orElse("");
    }

    public static String getNpcIdentity(@Nullable Entity entity) {
        if (entity == null) {
            return "";
        }
        String directIdentity = entity.getPersistentData().getString(TAG_NPC_IDENTITY).trim();
        if (!directIdentity.isBlank()) {
            return directIdentity;
        }
        return NpcBindingDataManager.getInstance()
                .findBinding(entity)
                .map(binding -> binding.bindingId().trim())
                .orElse("");
    }

    public static void assignNpcIdentity(Entity entity, String npcIdentity) {
        if (entity == null) {
            return;
        }
        String normalized = npcIdentity == null ? "" : npcIdentity.trim();
        if (normalized.isBlank()) {
            entity.getPersistentData().remove(TAG_NPC_IDENTITY);
        } else {
            entity.getPersistentData().putString(TAG_NPC_IDENTITY, normalized);
        }
    }

    public static void setUseNativeAi(Entity entity, boolean useNativeAi) {
        if (entity != null) {
            entity.getPersistentData().putBoolean(TAG_USE_NATIVE_AI, useNativeAi);
        }
    }

    public static boolean usesNativeAi(@Nullable Entity entity) {
        return entity != null && entity.getPersistentData().getBoolean(TAG_USE_NATIVE_AI);
    }

    public static boolean assignQuestNode(Entity entity, String questNodeId) {
        String normalizedId = sanitizeQuestNodeId(questNodeId);
        if (normalizedId.isBlank()) {
            return false;
        }

        if (entity instanceof IQuestNPC questNpc) {
            questNpc.setQuestNodeId(normalizedId);
            return true;
        }

        if (entity instanceof LivingEntity livingEntity) {
            return getQuestNpcData(livingEntity).map(data -> {
                data.promote(livingEntity, normalizedId);
                return true;
            }).orElse(false);
        }

        return false;
    }

    public static boolean removeQuestNode(Entity entity) {
        if (!(entity instanceof LivingEntity livingEntity) || entity instanceof IQuestNPC) {
            return false;
        }
        return getQuestNpcData(livingEntity).map(data -> {
            if (!data.isQuestNpc()) {
                return false;
            }
            data.demote(livingEntity);
            return true;
        }).orElse(false);
    }

    public static Optional<QuestNpcData> getQuestNpcData(LivingEntity entity) {
        return entity.getCapability(ModCapabilities.QUEST_NPC).resolve();
    }

    public static String sanitizeQuestNodeId(@Nullable String questNodeId) {
        return questNodeId == null ? "" : questNodeId.trim();
    }
}
