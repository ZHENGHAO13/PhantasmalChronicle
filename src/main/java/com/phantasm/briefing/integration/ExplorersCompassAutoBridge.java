package com.phantasm.briefing.integration;

import com.phantasm.briefing.api.StructureSearchBridgeApi;
import com.phantasm.briefing.data.QuestMarkerSpec;
import com.phantasm.briefing.service.StructureSearchCompatService;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.server.ServerLifecycleHooks;

public final class ExplorersCompassAutoBridge {
    private ExplorersCompassAutoBridge() {
    }

    public static void onSearchSucceeded(ItemStack stack, ResourceLocation structureKey, int x, int z) {
        if (stack == null || stack.isEmpty() || structureKey == null) {
            return;
        }

        ServerPlayer player = findOwner(stack);
        if (player == null) {
            return;
        }

        int y = resolveMarkerY(player, x, z);
        QuestMarkerSpec marker = new QuestMarkerSpec(
                structureKey.toString(),
                player.serverLevel().dimension().location().toString(),
                x,
                y,
                z
        );

        StructureSearchBridgeApi.postLocatedStructure(
                player,
                StructureSearchCompatService.normalizeStructureId(structureKey.toString()),
                marker,
                true,
                ""
        );
    }

    private static ServerPlayer findOwner(ItemStack stack) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return null;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getMainHandItem() == stack || player.getOffhandItem() == stack) {
                return player;
            }
        }
        return null;
    }

    private static int resolveMarkerY(ServerPlayer player, int x, int z) {
        BlockPos surface = player.serverLevel().getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(x, player.getBlockY(), z)
        );
        if (surface.getY() > player.serverLevel().getMinBuildHeight()) {
            return surface.getY();
        }
        return player.getBlockY();
    }
}
