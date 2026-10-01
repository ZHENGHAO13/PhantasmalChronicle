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

import static com.phantasm.briefing.data.JsonDataUtil.readText;
import static com.phantasm.briefing.data.JsonDataUtil.sanitize;

/** Current item-trade schema. Old trade shapes are rewritten before loading. */
public record TradeSpec(String tradeId, String title, String description, JsonObject openCondition,
                        List<TradeCategorySpec> categories, List<TradeEntrySpec> entries) {
    public static TradeSpec fromJson(String sourceDescription, JsonObject json) {
        String tradeId = sanitize(GsonHelper.getAsString(json, "tradeId", ""));
        if (tradeId.isBlank()) throw new IllegalArgumentException("Trade id is blank: " + sourceDescription);
        String title = readText(json, "displayName");
        if (title.isBlank()) title = tradeId;
        String description = readText(json, "description");
        JsonObject openCondition = json.has("openCondition") && json.get("openCondition").isJsonObject()
                ? GsonHelper.getAsJsonObject(json, "openCondition").deepCopy() : new JsonObject();

        List<TradeCategorySpec> categories = new ArrayList<>();
        JsonArray categoryArray = json.has("categories") && json.get("categories").isJsonArray()
                ? GsonHelper.getAsJsonArray(json, "categories") : new JsonArray();
        for (int i = 0; i < categoryArray.size(); i++) {
            JsonElement element = categoryArray.get(i);
            if (element.isJsonObject()) categories.add(TradeCategorySpec.fromJson(sourceDescription + "#categories[" + i + "]", element.getAsJsonObject()));
        }

        if (!json.has("entries") || !json.get("entries").isJsonObject()) {
            throw new IllegalArgumentException("Trade entries must use the current object format: " + sourceDescription);
        }
        List<TradeEntrySpec> entries = new ArrayList<>();
        JsonObject entriesObject = GsonHelper.getAsJsonObject(json, "entries");
        for (Map.Entry<String, JsonElement> entry : entriesObject.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject entryJson = entry.getValue().getAsJsonObject().deepCopy();
            if (!entryJson.has("entryId")) entryJson.addProperty("entryId", entry.getKey());
            entries.add(TradeEntrySpec.fromJson(sourceDescription + "#entries." + entry.getKey(), entryJson));
        }
        if (entries.isEmpty()) throw new IllegalArgumentException("Trade entries are empty: " + sourceDescription);
        return new TradeSpec(tradeId, title, description, openCondition, List.copyOf(categories), List.copyOf(entries));
    }

    public boolean canOpen(ServerPlayer player) { return BriefingConditionService.test(player, null, openCondition); }
    public MerchantOffers toMerchantOffers(ServerPlayer player) {
        MerchantOffers offers = new MerchantOffers();
        for (TradeEntrySpec entry : sortedEntries()) {
            boolean unlocked = entry.isUnlocked(player);
            if (unlocked) offers.add(entry.toMerchantOffer());
            else if (!entry.hideUntilUnlocked()) offers.add(entry.toMerchantOffer(0));
        }
        return offers;
    }
    public Component getTitleComponent() { return Component.literal(title); }
    private List<TradeEntrySpec> sortedEntries() {
        Map<String,Integer> categoryOrder = new LinkedHashMap<>();
        for (TradeCategorySpec category : categories) categoryOrder.put(category.categoryId(), category.sortOrder());
        return entries.stream().sorted(Comparator
                .comparingInt((TradeEntrySpec entry) -> categoryOrder.getOrDefault(entry.categoryId(), Integer.MAX_VALUE))
                .thenComparingInt(TradeEntrySpec::sortOrder)
                .thenComparing(TradeEntrySpec::entryId, String.CASE_INSENSITIVE_ORDER)).toList();
    }
}
