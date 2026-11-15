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

public record WalletSpec(
        String walletId,
        String title,
        String currencyName,
        String iconItemId,
        List<String> itemIds,
        int sortOrder
) {

    public static WalletSpec fromJson(String sourceDescription, JsonObject json) {
        String walletId = firstNonBlank(
                sanitize(GsonHelper.getAsString(json, "walletId", "")),
                sanitize(GsonHelper.getAsString(json, "currencyId", ""))
        );
        String iconItemId = firstNonBlank(
                sanitize(GsonHelper.getAsString(json, "iconItemId", "")),
                sanitize(GsonHelper.getAsString(json, "itemId", ""))
        );
        List<String> itemIds = parseItemIds(json);
        if (itemIds.isEmpty() && !iconItemId.isBlank()) {
            itemIds = List.of(iconItemId);
        }
        if (iconItemId.isBlank() && !itemIds.isEmpty()) {
            iconItemId = itemIds.get(0);
        }

        if (walletId.isBlank()) {
            throw new IllegalArgumentException("Wallet id is blank: " + sourceDescription);
        }
        if (iconItemId.isBlank()) {
            throw new IllegalArgumentException("Wallet icon item is blank: " + sourceDescription);
        }
        validateItemId(iconItemId, sourceDescription);
        for (String itemId : itemIds) {
            validateItemId(itemId, sourceDescription);
        }

        String title = sanitize(GsonHelper.getAsString(json, "title", ""));
        if (title.isBlank()) {
            title = parseDisplayName(json);
        }
        if (title.isBlank()) {
            title = resolveItemName(iconItemId);
        }
        if (title.isBlank()) {
            title = walletId;
        }

        String currencyName = sanitize(GsonHelper.getAsString(json, "currencyName", ""));
        int sortOrder = GsonHelper.getAsInt(json, "sortOrder", 0);
        return new WalletSpec(
                walletId,
                title,
                currencyName.isBlank() ? title : currencyName,
                iconItemId,
                List.copyOf(itemIds),
                sortOrder
        );
    }

    private static List<String> parseItemIds(JsonObject json) {
        if (!json.has("itemIds")) {
            return List.of();
        }

        JsonArray array = GsonHelper.getAsJsonArray(json, "itemIds");
        List<String> itemIds = new ArrayList<>();
        for (JsonElement element : array) {
            String itemId = sanitize(element.getAsString());
            if (!itemId.isBlank()) {
                itemIds.add(itemId);
            }
        }
        return itemIds;
    }

    private static String parseDisplayName(JsonObject json) {
        if (!json.has("displayName")) {
            return "";
        }

        JsonObject displayName = GsonHelper.getAsJsonObject(json, "displayName");
        String literalValue = sanitize(GsonHelper.getAsString(displayName, "value", ""));
        if (!literalValue.isBlank()) {
            return literalValue;
        }
        return "";
    }

    private static void validateItemId(String itemId, String sourceDescription) {
        ResourceLocation itemKey = ResourceLocation.tryParse(itemId);
        if (itemKey == null || !ForgeRegistries.ITEMS.containsKey(itemKey)) {
            throw new IllegalArgumentException("Wallet item does not exist: " + itemId + " from " + sourceDescription);
        }
    }

    private static String resolveItemName(String itemId) {
        ResourceLocation itemKey = ResourceLocation.tryParse(itemId);
        if (itemKey == null) {
            return "";
        }

        Item item = ForgeRegistries.ITEMS.getValue(itemKey);
        if (item == null) {
            return "";
        }
        return item.getDefaultInstance().getHoverName().getString();
    }

    private static String firstNonBlank(String first, String second) {
        return first.isBlank() ? second : first;
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.trim();
    }
}
