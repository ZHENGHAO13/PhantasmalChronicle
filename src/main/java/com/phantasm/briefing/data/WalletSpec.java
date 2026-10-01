package com.phantasm.briefing.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

import static com.phantasm.briefing.data.JsonDataUtil.readText;
import static com.phantasm.briefing.data.JsonDataUtil.sanitize;

/** Current wallet schema. Old wallet shapes are rewritten before loading. */
public record WalletSpec(String walletId, String title, String currencyName, String iconItemId, List<String> itemIds, int sortOrder) {
    public static WalletSpec fromJson(String sourceDescription, JsonObject json) {
        String walletId = sanitize(GsonHelper.getAsString(json, "walletId", ""));
        String iconItemId = sanitize(GsonHelper.getAsString(json, "iconItemId", ""));
        List<String> itemIds = parseItemIds(json);
        if (itemIds.isEmpty() && !iconItemId.isBlank()) itemIds = List.of(iconItemId);
        if (iconItemId.isBlank() && !itemIds.isEmpty()) iconItemId = itemIds.get(0);
        if (walletId.isBlank()) throw new IllegalArgumentException("Wallet id is blank: " + sourceDescription);
        if (iconItemId.isBlank()) throw new IllegalArgumentException("Wallet icon item is blank: " + sourceDescription);
        validateItemId(iconItemId, sourceDescription);
        for (String itemId : itemIds) validateItemId(itemId, sourceDescription);

        String title = readText(json, "title");
        if (title.isBlank()) title = resolveItemName(iconItemId);
        if (title.isBlank()) title = walletId;
        String currencyName = sanitize(GsonHelper.getAsString(json, "currencyName", ""));
        return new WalletSpec(walletId, title, currencyName.isBlank() ? title : currencyName,
                iconItemId, List.copyOf(itemIds), GsonHelper.getAsInt(json, "sortOrder", 0));
    }

    private static List<String> parseItemIds(JsonObject json) {
        if (!json.has("itemIds") || !json.get("itemIds").isJsonArray()) return List.of();
        JsonArray array = GsonHelper.getAsJsonArray(json, "itemIds");
        List<String> itemIds = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive()) continue;
            String itemId = sanitize(element.getAsString());
            if (!itemId.isBlank()) itemIds.add(itemId);
        }
        return itemIds;
    }

    private static void validateItemId(String itemId, String sourceDescription) {
        ResourceLocation itemKey = ResourceLocation.tryParse(itemId);
        if (itemKey == null || !ForgeRegistries.ITEMS.containsKey(itemKey)) {
            throw new IllegalArgumentException("Wallet item does not exist: " + itemId + " from " + sourceDescription);
        }
    }

    private static String resolveItemName(String itemId) {
        ResourceLocation itemKey = ResourceLocation.tryParse(itemId);
        if (itemKey == null) return "";
        Item item = ForgeRegistries.ITEMS.getValue(itemKey);
        return item == null ? "" : item.getDefaultInstance().getHoverName().getString();
    }
}
