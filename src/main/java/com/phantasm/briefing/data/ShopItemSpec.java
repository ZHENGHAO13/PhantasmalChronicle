package com.phantasm.briefing.data;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.Objects;
import javax.annotation.Nonnull;

public record ShopItemSpec(
        String itemId,
        int count
) {

    public static ShopItemSpec fromJson(@Nonnull String sourceDescription, @Nonnull JsonObject json) {
        String itemId = firstNonBlank(
                sanitize(GsonHelper.getAsString(json, "item", "")),
                sanitize(GsonHelper.getAsString(json, "itemId", ""))
        );
        int count = Math.max(1, GsonHelper.getAsInt(json, "count", 1));
        if (itemId.isBlank()) {
            throw new IllegalArgumentException("Shop item id is blank: " + sourceDescription);
        }

        ResourceLocation itemKey = ResourceLocation.tryParse(itemId);
        if (itemKey == null || !ForgeRegistries.ITEMS.containsKey(itemKey)) {
            throw new IllegalArgumentException("Shop item does not exist: " + itemId + " from " + sourceDescription);
        }

        return new ShopItemSpec(itemId, count);
    }

    @Nonnull
    public ItemStack toItemStack() {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(this.itemId));
        if (item == null) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item, this.count);
    }

    @Nonnull
    private static String sanitize(String value) {
        return value == null ? "" : value.trim();
    }

    @Nonnull
    private static String firstNonBlank(@Nonnull String first, @Nonnull String second) {
        return first.isBlank() ? second : first;
    }
}
