package com.phantasm.briefing.service;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.Objects;

/** Atomically checks and removes an item payment from a player's inventory. */
public final class PlayerItemDeliveryService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private PlayerItemDeliveryService() {
    }

    public static boolean deliverItem(ServerPlayer player, String itemInput, int count) {
        ItemRewardSpec spec = ItemRewardSpec.parse(itemInput).orElse(null);
        if (spec == null || count <= 0) {
            LOGGER.warn("[PhantasmBriefing] Invalid item delivery input: '{}' x{}", itemInput, count);
            return false;
        }

        ResourceLocation id = ResourceLocation.tryParse(spec.itemId());
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == Items.AIR) {
            LOGGER.warn("[PhantasmBriefing] Item delivery target does not exist: '{}'", spec.itemId());
            return false;
        }

        CompoundTag requiredTag = null;
        if (!spec.nbt().isBlank()) {
            try {
                requiredTag = TagParser.parseTag(spec.nbt());
            } catch (CommandSyntaxException exception) {
                LOGGER.warn("[PhantasmBriefing] Invalid item delivery NBT '{}': {}", spec.nbt(), exception.getMessage());
                return false;
            }
        }

        int available = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (matches(stack, item, requiredTag)) {
                available += stack.getCount();
                if (available >= count) {
                    break;
                }
            }
        }

        if (available < count) {
            ItemStack displayStack = new ItemStack(item);
            if (requiredTag != null) {
                displayStack.setTag(requiredTag.copy());
            }
            player.sendSystemMessage(Component.translatable(
                    "msg.phantasmbriefing.item_delivery_missing",
                    displayStack.getHoverName(),
                    count,
                    available
            ));
            return false;
        }

        int remaining = count;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!matches(stack, item, requiredTag)) {
                continue;
            }
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            remaining -= removed;
        }

        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        return remaining == 0;
    }

    private static boolean matches(ItemStack stack, Item item, CompoundTag requiredTag) {
        if (stack.isEmpty() || !stack.is(item)) {
            return false;
        }
        return requiredTag == null || Objects.equals(stack.getTag(), requiredTag);
    }
}
