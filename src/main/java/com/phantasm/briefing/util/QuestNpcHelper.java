package com.phantasm.briefing.util;

import com.phantasm.briefing.api.IQuestNPC;
import com.phantasm.briefing.capability.ModCapabilities;
import com.phantasm.briefing.capability.QuestNpcData;
import com.phantasm.briefing.data.DialogueDataManager;
import com.phantasm.briefing.data.NpcBindingDataManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public final class QuestNpcHelper {
    private static final String TAG_NPC_IDENTITY = "PhantasmBriefingNpcIdentity";
    private static final String TAG_USE_NATIVE_AI = "PhantasmBriefingNpcUseNativeAi";
    private static final String TAG_DIRECT_QUEST_NPC = "PhantasmBriefingDirectQuestNpc";

    private QuestNpcHelper() {
    }

    public static boolean isQuestNPC(@Nullable Entity entity) {
        return !getNpcIdentity(entity).isBlank() || !resolveQuestNodeId(entity).isBlank();
    }

    public static boolean isPotentialQuestNpc(@Nullable Entity entity) {
        if (entity instanceof IQuestNPC) {
            return true;
        }
        return entity instanceof LivingEntity
                && (hasDirectQuestNpcMarker(entity) || NpcBindingDataManager.getInstance().mayMatchEntityType(entity));
    }

    public static boolean hasDirectQuestNpcMarker(@Nullable Entity entity) {
        return entity != null && entity.getPersistentData().getBoolean(TAG_DIRECT_QUEST_NPC);
    }

    public static void syncDirectQuestNpcMarker(LivingEntity entity) {
        boolean directQuestNpc = getQuestNpcData(entity).map(QuestNpcData::isQuestNpc).orElse(false);
        if (directQuestNpc) {
            entity.getPersistentData().putBoolean(TAG_DIRECT_QUEST_NPC, true);
        } else {
            entity.getPersistentData().remove(TAG_DIRECT_QUEST_NPC);
        }
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

        NpcBindingDataManager manager = NpcBindingDataManager.getInstance();
        String directIdentity = entity.getPersistentData().getString(TAG_NPC_IDENTITY).trim();
        if (!directIdentity.isBlank()) {
            String boundNodeId = manager.getBinding(directIdentity)
                    .map(binding -> sanitizeQuestNodeId(binding.questNodeId()))
                    .orElse("");
            if (!boundNodeId.isBlank()) {
                return boundNodeId;
            }
        }

        return manager.findBinding(entity)
                .map(binding -> sanitizeQuestNodeId(binding.questNodeId()))
                .orElse("");
    }

    /** Returns only the explicit identity written by PhantasmalChronicle, without rule matching. */
    public static String getAssignedNpcIdentity(@Nullable Entity entity) {
        if (entity == null) {
            return "";
        }
        return entity.getPersistentData().getString(TAG_NPC_IDENTITY).trim();
    }

    public static String getNpcIdentity(@Nullable Entity entity) {
        if (entity == null) {
            return "";
        }
        String directIdentity = getAssignedNpcIdentity(entity);
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
                livingEntity.getPersistentData().putBoolean(TAG_DIRECT_QUEST_NPC, true);
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
            livingEntity.getPersistentData().remove(TAG_DIRECT_QUEST_NPC);
            return true;
        }).orElse(false);
    }

    /**
     * Removes only a direct capability-based quest-node assignment when its dialogue node no
     * longer exists. Managed spawned NPCs are handled by NpcStructureSpawnService instead.
     */
    public static boolean clearStaleDirectQuestNode(LivingEntity entity) {
        if (entity == null || entity instanceof IQuestNPC || !getAssignedNpcIdentity(entity).isBlank()) {
            return false;
        }
        String directNodeId = getQuestNodeId(entity);
        if (directNodeId.isBlank() || DialogueDataManager.getInstance().getNode(directNodeId).isPresent()) {
            return false;
        }
        return removeQuestNode(entity);
    }

    public static Optional<QuestNpcData> getQuestNpcData(LivingEntity entity) {
        return entity.getCapability(ModCapabilities.QUEST_NPC).resolve();
    }

    public static String sanitizeQuestNodeId(@Nullable String questNodeId) {
        return questNodeId == null ? "" : questNodeId.trim();
    }
}
