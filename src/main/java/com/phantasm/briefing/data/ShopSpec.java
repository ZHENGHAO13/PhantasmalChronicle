package com.phantasm.briefing.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.phantasm.briefing.service.BriefingConditionService;
import net.minecraft.network.chat.Component;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;

public record ShopSpec(
        String shopId,
        String title,
        String walletId,
        JsonObject openCondition,
        List<TradeCategorySpec> categories,
        List<ShopOfferSpec> offers
) {

    public static ShopSpec fromJson(@Nonnull String sourceDescription, @Nonnull JsonObject json) {
        String shopId = sanitize(GsonHelper.getAsString(json, "shopId", ""));
        String title = sanitize(GsonHelper.getAsString(json, "title", ""));
        String walletId = sanitize(GsonHelper.getAsString(json, "walletId", ""));
        JsonObject openCondition = json.has("openCondition")
                ? Objects.requireNonNull(GsonHelper.getAsJsonObject(json, "openCondition")).deepCopy()
                : new JsonObject();
        if (shopId.isBlank()) {
            throw new IllegalArgumentException("Shop id is blank: " + sourceDescription);
        }

        List<TradeCategorySpec> categories = new ArrayList<>();
        JsonArray categoryArray = json.has("categories") && json.get("categories").isJsonArray()
                ? GsonHelper.getAsJsonArray(json, "categories")
                : new JsonArray();
        int categoryIndex = 0;
        for (var element : categoryArray) {
            if (element.isJsonObject()) {
                categories.add(TradeCategorySpec.fromJson(
                        sourceDescription + "#categories[" + categoryIndex + "]",
                        Objects.requireNonNull(element.getAsJsonObject())
                ));
            }
            categoryIndex++;
        }

        List<ShopOfferSpec> offers = new ArrayList<>();
        JsonArray offerArray = GsonHelper.getAsJsonArray(json, "offers");
        offerArray.forEach(element -> offers.add(ShopOfferSpec.fromJson(sourceDescription + "#" + shopId, Objects.requireNonNull(element.getAsJsonObject()))));
        if (offers.isEmpty()) {
            throw new IllegalArgumentException("Shop offers are empty: " + sourceDescription);
        }

        boolean walletShop = !walletId.isBlank();
        for (ShopOfferSpec offer : offers) {
            if (walletShop && offer.requiresItemPayment()) {
                throw new IllegalArgumentException("Wallet shop offer cannot define costA/costB: " + sourceDescription);
            }
            if (!walletShop && !offer.requiresItemPayment()) {
                throw new IllegalArgumentException("Vanilla shop offer requires costA: " + sourceDescription);
            }
            if (!walletShop && offer.requiresWalletPayment()) {
                throw new IllegalArgumentException("walletCost requires walletId on shop: " + sourceDescription);
            }
        }

        return new ShopSpec(
                shopId,
                title.isBlank() ? shopId : title,
                walletId,
                openCondition,
                List.copyOf(categories),
                List.copyOf(offers)
        );
    }

    public boolean isWalletShop() {
        return !this.walletId.isBlank();
    }

    public boolean canOpen(ServerPlayer player) {
        return BriefingConditionService.test(player, null, this.openCondition);
    }

    public boolean isOfferUnlocked(ServerPlayer player, ShopOfferSpec offer) {
        boolean prerequisitePassed = offer.requiredQuestId().isBlank()
                || com.phantasm.briefing.service.QuestRuntimeService.isQuestCompleted(player, offer.requiredQuestId());
        return prerequisitePassed && BriefingConditionService.test(player, null, offer.visibleCondition());
    }

    public MerchantOffers toMerchantOffers() {
        MerchantOffers offers = new MerchantOffers();
        this.offers.forEach(offer -> offers.add(offer.toMerchantOffer()));
        return offers;
    }

    public MerchantOffers toMerchantOffers(ServerPlayer player) {
        MerchantOffers offers = new MerchantOffers();
        for (ShopOfferSpec offer : this.sortedOffers()) {
            boolean unlocked = this.isOfferUnlocked(player, offer);
            if (unlocked) {
                offers.add(offer.toMerchantOffer());
                continue;
            }

            if (!offer.hideUntilUnlocked()) {
                offers.add(offer.toMerchantOffer(0));
            }
        }
        return offers;
    }

    @Nonnull
    public List<ShopOfferSpec> sortedOffers() {
        Map<String, Integer> categoryOrder = new LinkedHashMap<>();
        for (TradeCategorySpec category : this.categories) {
            categoryOrder.put(category.categoryId(), category.sortOrder());
        }
        return this.offers.stream()
                .sorted(Comparator
                        .comparingInt((ShopOfferSpec offer) -> categoryOrder.getOrDefault(offer.categoryId(), Integer.MAX_VALUE))
                        .thenComparingInt(ShopOfferSpec::sortOrder)
                        .thenComparing(ShopOfferSpec::offerId, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Nonnull
    public Component getTitleComponent() {
        return Component.literal(this.title);
    }

    @Nonnull
    private static String sanitize(String value) {
        return value == null ? "" : value.trim();
    }
}
