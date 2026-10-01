package com.phantasm.briefing.api.event;

import com.phantasm.briefing.data.QuestMarkerSpec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

public class StructureSearchLocatedEvent extends Event {
    private final ServerPlayer player;
    private final String structureId;
    private final QuestMarkerSpec marker;
    private final boolean spawnResolvedNpcAnchors;
    private final String questNodeIdOverride;

    private int matchedObjectives;
    private int spawnedNpcCount;

    public StructureSearchLocatedEvent(
            ServerPlayer player,
            String structureId,
            QuestMarkerSpec marker,
            boolean spawnResolvedNpcAnchors,
            String questNodeIdOverride
    ) {
        this.player = player;
        this.structureId = structureId == null ? "" : structureId.trim();
        this.marker = marker;
        this.spawnResolvedNpcAnchors = spawnResolvedNpcAnchors;
        this.questNodeIdOverride = questNodeIdOverride == null ? "" : questNodeIdOverride.trim();
    }

    public ServerPlayer player() {
        return player;
    }

    public String structureId() {
        return structureId;
    }

    public QuestMarkerSpec marker() {
        return marker;
    }

    public boolean spawnResolvedNpcAnchors() {
        return spawnResolvedNpcAnchors;
    }

    public String questNodeIdOverride() {
        return questNodeIdOverride;
    }

    public int matchedObjectives() {
        return matchedObjectives;
    }

    public void setMatchedObjectives(int matchedObjectives) {
        this.matchedObjectives = Math.max(0, matchedObjectives);
    }

    public int spawnedNpcCount() {
        return spawnedNpcCount;
    }

    public void setSpawnedNpcCount(int spawnedNpcCount) {
        this.spawnedNpcCount = Math.max(0, spawnedNpcCount);
    }
}
