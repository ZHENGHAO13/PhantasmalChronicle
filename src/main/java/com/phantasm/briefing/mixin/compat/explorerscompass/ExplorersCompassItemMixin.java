package com.phantasm.briefing.mixin.compat.explorerscompass;

import com.phantasm.briefing.integration.ExplorersCompassAutoBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Pseudo
@Mixin(targets = "com.chaosthedude.explorerscompass.items.ExplorersCompassItem", remap = false)
public abstract class ExplorersCompassItemMixin {
    @Inject(
            method = "succeed(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/resources/ResourceLocation;ZIILjava/util/List;IZ)V",
            at = @At("TAIL"),
            remap = false
    )
    private void pb$bridgeSearchSuccess(
            ItemStack stack,
            ResourceLocation structureKey,
            boolean isGroup,
            int x,
            int z,
            List<BlockPos> prevPos,
            int samples,
            boolean displayCoordinates,
            CallbackInfo callbackInfo
    ) {
        ExplorersCompassAutoBridge.onSearchSucceeded(stack, structureKey, x, z);
    }
}
