package com.phantasm.briefing.capability;

import com.phantasm.briefing.api.IQuestNPC;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.util.INBTSerializable;

public final class QuestNpcData implements IQuestNPC, INBTSerializable<CompoundTag> {
    private static final String TAG_QUEST_NODE_ID = "QuestNodeId";
    private static final String TAG_HAS_STORED_NO_GRAVITY = "HasStoredNoGravity";
    private static final String TAG_STORED_NO_GRAVITY = "StoredNoGravity";

    private String questNodeId = "";
    private boolean hasStoredNoGravity;
    private boolean storedNoGravity;

    @Override
    public String getQuestNodeId() {
        return this.questNodeId;
    }

    @Override
    public void setQuestNodeId(String id) {
        this.questNodeId = normalize(id);
    }

    public boolean isQuestNpc() {
        return !this.questNodeId.isBlank();
    }

    public void promote(LivingEntity entity, String questNodeId) {
        if (!this.isQuestNpc()) {
            this.hasStoredNoGravity = true;
            this.storedNoGravity = entity.isNoGravity();
        }
        this.setQuestNodeId(questNodeId);
    }

    public void demote(LivingEntity entity) {
        if (this.hasStoredNoGravity) {
            entity.setNoGravity(this.storedNoGravity);
        }
        this.questNodeId = "";
        this.hasStoredNoGravity = false;
        this.storedNoGravity = false;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        if (this.isQuestNpc()) {
            tag.putString(TAG_QUEST_NODE_ID, this.questNodeId);
        }
        if (this.hasStoredNoGravity) {
            tag.putBoolean(TAG_HAS_STORED_NO_GRAVITY, true);
            tag.putBoolean(TAG_STORED_NO_GRAVITY, this.storedNoGravity);
        }
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        this.setQuestNodeId(nbt.getString(TAG_QUEST_NODE_ID));
        this.hasStoredNoGravity = nbt.getBoolean(TAG_HAS_STORED_NO_GRAVITY);
        this.storedNoGravity = nbt.getBoolean(TAG_STORED_NO_GRAVITY);
    }

    private static String normalize(String id) {
        return id == null ? "" : id.trim();
    }
}
