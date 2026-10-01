package com.phantasm.briefing.service;

import com.phantasm.briefing.PhantasmBriefing;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

public final class NpcStructureSpawnSavedData extends SavedData {
    private static final String FILE_ID = PhantasmBriefing.MOD_ID + "_npc_structure_spawns";
    private static final String SPAWNED_INSTANCES = "SpawnedInstances";
    private static final String COMPLETED_STRUCTURES = "CompletedStructures";

    private final Set<String> spawnedInstances = new HashSet<>();
    private final Set<String> completedStructures = new HashSet<>();

    public static NpcStructureSpawnSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                NpcStructureSpawnSavedData::load,
                NpcStructureSpawnSavedData::new,
                FILE_ID
        );
    }

    public static NpcStructureSpawnSavedData load(CompoundTag tag) {
        NpcStructureSpawnSavedData data = new NpcStructureSpawnSavedData();
        ListTag entries = tag.getList(SPAWNED_INSTANCES, Tag.TAG_STRING);
        for (int i = 0; i < entries.size(); i++) {
            String instanceId = entries.getString(i);
            if (!instanceId.isBlank()) {
                data.spawnedInstances.add(instanceId);
            }
        }
        ListTag completedEntries = tag.getList(COMPLETED_STRUCTURES, Tag.TAG_STRING);
        for (int i = 0; i < completedEntries.size(); i++) {
            String structureId = completedEntries.getString(i);
            if (!structureId.isBlank()) {
                data.completedStructures.add(structureId);
            }
        }
        return data;
    }

    public boolean hasSpawned(String instanceId) {
        return this.spawnedInstances.contains(instanceId);
    }

    public Set<String> getSpawnedInstances() {
        return Set.copyOf(this.spawnedInstances);
    }

    public void markSpawned(String instanceId) {
        if (instanceId != null && !instanceId.isBlank() && this.spawnedInstances.add(instanceId)) {
            this.setDirty();
        }
    }


    public boolean removeSpawned(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            return false;
        }
        boolean changed = this.spawnedInstances.remove(instanceId);
        if (changed) {
            this.setDirty();
        }
        return changed;
    }

    public int removeSpawnedInstancesForBinding(String bindingId) {
        if (bindingId == null || bindingId.isBlank()) {
            return 0;
        }
        String marker = "|" + bindingId.trim() + "|";
        int before = this.spawnedInstances.size();
        this.spawnedInstances.removeIf(id -> id.contains(marker));
        int removed = before - this.spawnedInstances.size();
        if (removed > 0) {
            this.setDirty();
        }
        return removed;
    }

    public int removeCompletedStructuresForBinding(String bindingId) {
        if (bindingId == null || bindingId.isBlank()) {
            return 0;
        }
        String marker = bindingId.trim() + "/";
        int before = this.completedStructures.size();
        this.completedStructures.removeIf(id -> id.contains(marker));
        int removed = before - this.completedStructures.size();
        if (removed > 0) {
            this.setDirty();
        }
        return removed;
    }

    public boolean removeCompletedStructure(String structureId) {
        if (structureId == null || structureId.isBlank()) {
            return false;
        }
        boolean changed = this.completedStructures.remove(structureId);
        if (changed) {
            this.setDirty();
        }
        return changed;
    }

    public boolean hasCompletedStructure(String structureId) {
        return this.completedStructures.contains(structureId);
    }

    public void markStructureCompleted(String structureId) {
        if (structureId != null && !structureId.isBlank() && this.completedStructures.add(structureId)) {
            this.setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        this.spawnedInstances.stream().sorted().forEach(instanceId -> entries.add(StringTag.valueOf(instanceId)));
        tag.put(SPAWNED_INSTANCES, entries);
        ListTag completedEntries = new ListTag();
        this.completedStructures.stream().sorted()
                .forEach(structureId -> completedEntries.add(StringTag.valueOf(structureId)));
        tag.put(COMPLETED_STRUCTURES, completedEntries);
        return tag;
    }
}
