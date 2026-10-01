package com.phantasm.briefing.capability;

import com.phantasm.briefing.PhantasmBriefing;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.Nullable;

public final class QuestNpcProvider implements ICapabilitySerializable<CompoundTag> {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(PhantasmBriefing.MOD_ID, "quest_npc");

    private final QuestNpcData backend = new QuestNpcData();
    private final LazyOptional<QuestNpcData> optional = LazyOptional.of(() -> this.backend);

    @Override
    public <T> LazyOptional<T> getCapability(net.minecraftforge.common.capabilities.Capability<T> cap, @Nullable Direction side) {
        return ModCapabilities.QUEST_NPC.orEmpty(cap, this.optional.cast());
    }

    @Override
    public CompoundTag serializeNBT() {
        return this.backend.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        this.backend.deserializeNBT(nbt);
    }

    public void invalidate() {
        this.optional.invalidate();
    }
}
