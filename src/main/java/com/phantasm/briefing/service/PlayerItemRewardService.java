package com.phantasm.briefing.service;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

public final class PlayerItemRewardService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private PlayerItemRewardService() {
    }

    public static boolean giveItem(ServerPlayer player, String itemInput, int count) {
        ItemRewardSpec spec = ItemRewardSpec.parse(itemInput).orElse(null);
        if (spec == null || count <= 0) {
            LOGGER.warn("[PhantasmBriefing] Invalid item reward input: '{}' x{}", itemInput, count);
            return false;
        }

        ResourceLocation id = ResourceLocation.tryParse(spec.itemId());
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == Items.AIR) {
            LOGGER.warn("[PhantasmBriefing] Item reward does not exist: '{}'", spec.itemId());
            return false;
        }

        CompoundTag itemTag = null;
        if (!spec.nbt().isBlank()) {
            try {
                itemTag = TagParser.parseTag(spec.nbt());
            } catch (CommandSyntaxException exception) {
                LOGGER.warn("[PhantasmBriefing] Invalid item reward NBT '{}': {}", spec.nbt(), exception.getMessage());
                return false;
            }
        }

        ItemStack template = new ItemStack(item);
        if (itemTag != null) {
            template.setTag(itemTag);
        }

        int remaining = count;
        int stackLimit = Math.max(1, template.getMaxStackSize());
        while (remaining > 0) {
            int batch = Math.min(stackLimit, remaining);
            ItemStack stack = template.copy();
            stack.setCount(batch);
            player.getInventory().add(stack);
            if (!stack.isEmpty()) {
                player.drop(stack, false);
            }
            remaining -= batch;
        }
        return true;
    }
}
