package com.phantasm.briefing.integration;

import com.mojang.logging.LogUtils;
import com.phantasm.briefing.api.StructureSearchBridgeApi;
import com.phantasm.briefing.api.event.StructureSearchLocatedEvent;
import com.phantasm.briefing.data.QuestMarkerSpec;
import com.phantasm.briefing.service.StructureSearchCompatService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

public final class ExplorersCompassIntegrationHelper {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String EXPLORERS_COMPASS_MOD_ID = "explorerscompass";
    private static final String EXPLORERS_COMPASS_ROOT_CLASS =
            "com.chaosthedude.explorerscompass.ExplorersCompass";

    private static volatile Boolean classpathAvailable;
    private static volatile boolean missingBridgeLogged;

    private ExplorersCompassIntegrationHelper() {
    }

    public static boolean isExplorersCompassLoaded() {
        return ModList.get().isLoaded(EXPLORERS_COMPASS_MOD_ID);
    }

    public static boolean isKnownClasspathAvailable() {
        Boolean known = classpathAvailable;
        if (known != null) {
            return known;
        }

        synchronized (ExplorersCompassIntegrationHelper.class) {
            if (classpathAvailable != null) {
                return classpathAvailable;
            }
            try {
                Class.forName(EXPLORERS_COMPASS_ROOT_CLASS);
                classpathAvailable = true;
            } catch (ClassNotFoundException exception) {
                classpathAvailable = false;
            }
            return classpathAvailable;
        }
    }

    public static String normalizeExplorersCompassStructureId(String rawStructureId) {
        return StructureSearchCompatService.normalizeStructureId(rawStructureId);
    }

    public static int reportLocatedStructure(
            ServerPlayer player,
            String rawStructureId,
            QuestMarkerSpec marker
    ) {
        warnIfBridgeUnavailable();
        return StructureSearchBridgeApi.reportLocatedStructure(
                player,
                normalizeExplorersCompassStructureId(rawStructureId),
                marker
        );
    }

    public static StructureSearchLocatedEvent postLocatedStructure(
            ServerPlayer player,
            String rawStructureId,
            QuestMarkerSpec marker,
            boolean spawnResolvedNpcAnchors,
            String questNodeIdOverride
    ) {
        warnIfBridgeUnavailable();
        return StructureSearchBridgeApi.postLocatedStructure(
                player,
                normalizeExplorersCompassStructureId(rawStructureId),
                marker,
                spawnResolvedNpcAnchors,
                questNodeIdOverride
        );
    }

    private static void warnIfBridgeUnavailable() {
        if (!isExplorersCompassLoaded() || isKnownClasspathAvailable() || missingBridgeLogged) {
            return;
        }
        missingBridgeLogged = true;
        LOGGER.warn("[PhantasmBriefing] Explorer's Compass is loaded, but its expected entry point is unavailable. Compatibility may need an update.");
    }
}
