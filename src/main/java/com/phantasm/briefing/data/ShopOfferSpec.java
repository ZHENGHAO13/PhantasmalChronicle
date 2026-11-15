package com.phantasm.briefing.data;

import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import org.jetbrains.annotations.Nullable;
import java.util.Objects;
import javax.annotation.Nonnull;

public record ShopOfferSpec(
        String offerId,
        String title,
        String description,
        String categoryId,
        int sortOrder,
        @Nullable ShopItemSpec costA,
        @Nullable ShopItemSpec costB,
        ShopItemSpec result,
        int maxUses,
        int villagerXp,
        float priceMultiplier,
        String requiredQuestId,
        boolean hideUntilUnlocked,
        int walletCost,
        JsonObject visibleCondition
) {

    public static ShopOfferSpec fromJson(@Nonnull String sourceDescription, @Nonnull JsonObject json) {
        String offerId = sanitize(GsonHelper.getAsString(json, "offerId", ""));
        String resultItemId = firstNonBlank(
                sanitize(GsonHelper.getAsString(json, "resultItemId", "")),
                sanitize(GsonHelper.getAsString(json, "itemId", ""))
        );
        String title = TradeTextHelper.readText(json.get("displayName"), firstNonBlank(offerId, resultItemId));
        String description = TradeTextHelper.readText(json.get("description"), "");
        String categoryId = sanitize(GsonHelper.getAsString(json, "category", ""));
        int sortOrder = GsonHelper.getAsInt(json, "sortOrder", 0);
        ShopItemSpec costA = json.has("costA") ? ShopItemSpec.fromJson(sourceDescription + "#costA", Objects.requireNonNull(GsonHelper.getAsJsonObject(json, "costA"))) : null;
        ShopItemSpec result = ShopItemSpec.fromJson(sourceDescription + "#result", Objects.requireNonNull(GsonHelper.getAsJsonObject(json, "result")));
        ShopItemSpec costB = json.has("costB") ? ShopItemSpec.fromJson(sourceDescription + "#costB", Objects.requireNonNull(GsonHelper.getAsJsonObject(json, "costB"))) : null;
        int maxUses = Math.max(1, GsonHelper.getAsInt(json, "maxUses", 9999));
        int villagerXp = Math.max(0, GsonHelper.getAsInt(json, "villagerXp", 0));
        float priceMultiplier = Math.max(0.0F, GsonHelper.getAsFloat(json, "priceMultiplier", 0.0F));
        String requiredQuestId = sanitize(GsonHelper.getAsString(json, "requiredQuestId", ""));
        boolean hideUntilUnlocked = GsonHelper.getAsBoolean(json, "hideUntilUnlocked", false);
        int walletCost = Math.max(0, GsonHelper.getAsInt(json, "walletCost", 0));
        JsonObject visibleCondition = json.has("visibleCondition")
                ? Objects.requireNonNull(GsonHelper.getAsJsonObject(json, "visibleCondition")).deepCopy()
                : new JsonObject();

        if (costA == null && walletCost <= 0) {
            throw new IllegalArgumentException("Shop offer is missing both costA and walletCost: " + sourceDescription);
        }

        String resolvedOfferId = offerId.isBlank()
                ? sanitize(result.itemId() + "_" + result.count() + "_" + sortOrder)
                : offerId;
        String resolvedTitle = title.isBlank() ? result.itemId() : title;
        return new ShopOfferSpec(
                resolvedOfferId,
                resolvedTitle,
                description,
                categoryId,
                sortOrder,
                costA,
                costB,
                result,
                maxUses,
                villagerXp,
                priceMultiplier,
                requiredQuestId,
                hideUntilUnlocked,
                walletCost,
                visibleCondition
        );
    }

    public boolean requiresWalletPayment() {
        return this.walletCost > 0;
    }

    public boolean requiresItemPayment() {
        return this.costA != null;
    }

    @Nonnull
    public MerchantOffer toMerchantOffer() {
        return this.toMerchantOffer(this.maxUses);
    }

    @Nonnull
    public MerchantOffer toMerchantOffer(int maxUses) {
        if (this.costA == null) {
            throw new IllegalStateException("Merchant offer requires costA for vanilla trade conversion");
        }
        ItemStack costAStack = this.costA.toItemStack();
        ItemStack costBStack = this.costB == null ? ItemStack.EMPTY : this.costB.toItemStack();
        ItemStack resultStack = this.result.toItemStack();
        return new MerchantOffer(costAStack, costBStack, resultStack, 0, Math.max(0, maxUses), this.villagerXp, this.priceMultiplier);
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
