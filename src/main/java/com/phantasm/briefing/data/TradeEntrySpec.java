package com.phantasm.briefing.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.phantasm.briefing.service.BriefingConditionService;
import com.phantasm.briefing.service.QuestRuntimeService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import java.util.Objects;

public record TradeEntrySpec(
        String entryId,
        String title,
        String categoryId,
        int sortOrder,
        List<ShopItemSpec> costs,
        List<ShopItemSpec> rewards,
        String requiredQuestId,
        boolean hideUntilUnlocked,
        JsonObject visibleCondition
) {
    public static TradeEntrySpec fromJson(@Nonnull String sourceDescription, @Nonnull JsonObject json) {
        String entryId = sanitize(GsonHelper.getAsString(json, "entryId", ""));
        if (entryId.isBlank()) {
            throw new IllegalArgumentException("Trade entry id is blank: " + sourceDescription);
        }

        String title = TradeTextHelper.readText(json.get("displayName"), entryId);
        String categoryId = sanitize(GsonHelper.getAsString(json, "category", ""));
        int sortOrder = GsonHelper.getAsInt(json, "sortOrder", 0);
        String requiredQuestId = sanitize(GsonHelper.getAsString(json, "requiredQuestId", ""));
        boolean hideUntilUnlocked = GsonHelper.getAsBoolean(json, "hideUntilUnlocked", false);
        JsonObject visibleCondition = json.has("visibleCondition")
                ? Objects.requireNonNull(GsonHelper.getAsJsonObject(json, "visibleCondition")).deepCopy()
                : new JsonObject();

        List<ShopItemSpec> costs = readItems(sourceDescription + "#costs", GsonHelper.getAsJsonArray(json, "costs"));
        List<ShopItemSpec> rewards = readItems(sourceDescription + "#rewards", GsonHelper.getAsJsonArray(json, "rewards"));
        if (costs.isEmpty() || costs.size() > 2) {
            throw new IllegalArgumentException("Trade entry requires 1-2 item costs: " + sourceDescription);
        }
        if (rewards.size() != 1) {
            throw new IllegalArgumentException("Trade entry requires exactly 1 item reward: " + sourceDescription);
        }

        return new TradeEntrySpec(
                entryId,
                title,
                categoryId,
                sortOrder,
                List.copyOf(costs),
                List.copyOf(rewards),
                requiredQuestId,
                hideUntilUnlocked,
                visibleCondition
        );
    }

    public boolean isUnlocked(ServerPlayer player) {
        boolean prerequisitePassed = requiredQuestId.isBlank() || QuestRuntimeService.isQuestCompleted(player, requiredQuestId);
        return prerequisitePassed && BriefingConditionService.test(player, null, visibleCondition);
    }

    @Nonnull
    public MerchantOffer toMerchantOffer() {
        return toMerchantOffer(9999);
    }

    @Nonnull
    public MerchantOffer toMerchantOffer(int maxUses) {
        ItemStack costA = costs.get(0).toItemStack();
        ItemStack costB = costs.size() > 1 ? costs.get(1).toItemStack() : ItemStack.EMPTY;
        ItemStack result = rewards.get(0).toItemStack();
        return new MerchantOffer(costA, costB, result, 0, Math.max(0, maxUses), 0, 0.0F);
    }

    @Nonnull
    private static List<ShopItemSpec> readItems(@Nonnull String sourceDescription, @Nonnull JsonArray array) {
        List<ShopItemSpec> result = new ArrayList<>();
        int index = 0;
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                index++;
                continue;
            }
            JsonObject json = element.getAsJsonObject();
            String type = sanitize(GsonHelper.getAsString(json, "type", "item"));
            if (!type.isBlank() && !"item".equals(type)) {
                throw new IllegalArgumentException("Only item trade entries are supported: " + sourceDescription + "[" + index + "]");
            }
            result.add(ShopItemSpec.fromJson(sourceDescription + "[" + index + "]", json));
            index++;
        }
        return result;
    }

    @Nonnull
    private static String sanitize(String value) {
        return value == null ? "" : value.trim();
    }
}
