package com.phantasm.briefing.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.phantasm.briefing.service.BriefingConditionService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record TradeSpec(
        String tradeId,
        String title,
        String description,
        JsonObject openCondition,
        List<TradeCategorySpec> categories,
        List<TradeEntrySpec> entries
) {
    public static TradeSpec fromJson(String sourceDescription, JsonObject json) {
        String tradeId = firstNonBlank(
                sanitize(GsonHelper.getAsString(json, "tradeId", "")),
                sanitize(GsonHelper.getAsString(json, "shopId", ""))
        );
        if (tradeId.isBlank()) {
            throw new IllegalArgumentException("Trade id is blank: " + sourceDescription);
        }

        String title = TradeTextHelper.readText(json.get("displayName"), tradeId);
        String description = TradeTextHelper.readText(json.get("description"), "");
        JsonObject openCondition = json.has("openCondition")
                ? GsonHelper.getAsJsonObject(json, "openCondition").deepCopy()
                : new JsonObject();

        List<TradeCategorySpec> categories = new ArrayList<>();
        JsonArray categoryArray = json.has("categories") && json.get("categories").isJsonArray()
                ? GsonHelper.getAsJsonArray(json, "categories")
                : new JsonArray();
        int categoryIndex = 0;
        for (JsonElement element : categoryArray) {
            if (element.isJsonObject()) {
                categories.add(TradeCategorySpec.fromJson(sourceDescription + "#categories[" + categoryIndex + "]", element.getAsJsonObject()));
            }
            categoryIndex++;
        }

        List<TradeEntrySpec> entries = new ArrayList<>();
        if (json.has("entries") && json.get("entries").isJsonObject()) {
            JsonObject entriesObject = GsonHelper.getAsJsonObject(json, "entries");
            for (Map.Entry<String, JsonElement> entry : entriesObject.entrySet()) {
                if (entry.getValue().isJsonObject()) {
                    entries.add(TradeEntrySpec.fromJson(sourceDescription + "#entries." + entry.getKey(), entry.getValue().getAsJsonObject()));
                }
            }
        }
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("Trade entries are empty: " + sourceDescription);
        }

        return new TradeSpec(
                tradeId,
                title,
                description,
                openCondition,
                List.copyOf(categories),
                List.copyOf(entries)
        );
    }

    public boolean canOpen(ServerPlayer player) {
        return BriefingConditionService.test(player, null, openCondition);
    }

    public MerchantOffers toMerchantOffers(ServerPlayer player) {
        MerchantOffers offers = new MerchantOffers();
        for (TradeEntrySpec entry : sortedEntries()) {
            boolean unlocked = entry.isUnlocked(player);
            if (unlocked) {
                offers.add(entry.toMerchantOffer());
                continue;
            }
            if (!entry.hideUntilUnlocked()) {
                offers.add(entry.toMerchantOffer(0));
            }
        }
        return offers;
    }

    public Component getTitleComponent() {
        return Component.literal(title);
    }

    private List<TradeEntrySpec> sortedEntries() {
        Map<String, Integer> categoryOrder = new LinkedHashMap<>();
        for (TradeCategorySpec category : categories) {
            categoryOrder.put(category.categoryId(), category.sortOrder());
        }
        return entries.stream()
                .sorted(Comparator
                        .comparingInt((TradeEntrySpec entry) -> categoryOrder.getOrDefault(entry.categoryId(), Integer.MAX_VALUE))
                        .thenComparingInt(TradeEntrySpec::sortOrder)
                        .thenComparing(TradeEntrySpec::entryId, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String firstNonBlank(String first, String second) {
        return first.isBlank() ? second : first;
    }
}
