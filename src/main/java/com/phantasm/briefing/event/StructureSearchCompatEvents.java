package com.phantasm.briefing.event;

import com.phantasm.briefing.api.event.StructureSearchLocatedEvent;
import com.phantasm.briefing.service.StructureSearchCompatService;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class StructureSearchCompatEvents {
    @SubscribeEvent
    public void onStructureLocated(StructureSearchLocatedEvent event) {
        int matched = StructureSearchCompatService.reportLocatedStructure(
                event.player(),
                event.structureId(),
                event.marker()
        );
        event.setMatchedObjectives(matched);

        if (!event.spawnResolvedNpcAnchors()) {
            return;
        }
        int spawned = StructureSearchCompatService.spawnResolvedStructureNpcsForStructure(
                event.player(),
                event.structureId(),
                event.questNodeIdOverride()
        );
        event.setSpawnedNpcCount(spawned);
    }
}
