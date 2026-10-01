package com.phantasm.briefing.api;

import com.phantasm.briefing.api.event.StructureSearchLocatedEvent;
import com.phantasm.briefing.data.QuestMarkerSpec;
import com.phantasm.briefing.service.StructureSearchCompatService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;

public final class StructureSearchBridgeApi {
    private StructureSearchBridgeApi() {
    }

    public static int reportLocatedStructure(ServerPlayer player, String structureId, QuestMarkerSpec marker) {
        return StructureSearchCompatService.reportLocatedStructure(player, structureId, marker);
    }

    public static int reportLocatedStructureAndSpawnNpcs(
            ServerPlayer player,
            String structureId,
            QuestMarkerSpec marker,
            String questNodeIdOverride
    ) {
        int matched = StructureSearchCompatService.reportLocatedStructure(player, structureId, marker);
        StructureSearchCompatService.spawnResolvedStructureNpcsForStructure(player, structureId, questNodeIdOverride);
        return matched;
    }

    public static StructureSearchLocatedEvent postLocatedStructure(
            ServerPlayer player,
            String structureId,
            QuestMarkerSpec marker,
            boolean spawnResolvedNpcAnchors,
            String questNodeIdOverride
    ) {
        StructureSearchLocatedEvent event = new StructureSearchLocatedEvent(
                player,
                structureId,
                marker,
                spawnResolvedNpcAnchors,
                questNodeIdOverride
        );
        MinecraftForge.EVENT_BUS.post(event);
        return event;
    }
}
