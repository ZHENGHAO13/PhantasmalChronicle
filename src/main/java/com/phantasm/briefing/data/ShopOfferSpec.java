package com.phantasm.briefing.data;

import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.Objects;

import static com.phantasm.briefing.data.JsonDataUtil.readText;
import static com.phantasm.briefing.data.JsonDataUtil.sanitize;

/** Current shop-offer schema. Old offer shapes are rewritten before loading. */
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
        String title = readText(json, "displayName");
        String description = readText(json, "description");
        String categoryId = sanitize(GsonHelper.getAsString(json, "category", ""));
        int sortOrder = GsonHelper.getAsInt(json, "sortOrder", 0);
        ShopItemSpec costA = json.has("costA") && json.get("costA").isJsonObject()
                ? ShopItemSpec.fromJson(sourceDescription + "#costA", Objects.requireNonNull(GsonHelper.getAsJsonObject(json, "costA"))) : null;
        ShopItemSpec costB = json.has("costB") && json.get("costB").isJsonObject()
                ? ShopItemSpec.fromJson(sourceDescription + "#costB", Objects.requireNonNull(GsonHelper.getAsJsonObject(json, "costB"))) : null;
        if (!json.has("result") || !json.get("result").isJsonObject()) {
            throw new IllegalArgumentException("Shop offer result is missing: " + sourceDescription);
        }
        ShopItemSpec result = ShopItemSpec.fromJson(sourceDescription + "#result", Objects.requireNonNull(GsonHelper.getAsJsonObject(json, "result")));
        int maxUses = Math.max(1, GsonHelper.getAsInt(json, "maxUses", 9999));
        int villagerXp = Math.max(0, GsonHelper.getAsInt(json, "villagerXp", 0));
        float priceMultiplier = Math.max(0.0F, GsonHelper.getAsFloat(json, "priceMultiplier", 0.0F));
        String requiredQuestId = sanitize(GsonHelper.getAsString(json, "requiredQuestId", ""));
        boolean hideUntilUnlocked = GsonHelper.getAsBoolean(json, "hideUntilUnlocked", false);
        int walletCost = Math.max(0, GsonHelper.getAsInt(json, "walletCost", 0));
        JsonObject visibleCondition = json.has("visibleCondition") && json.get("visibleCondition").isJsonObject()
                ? Objects.requireNonNull(GsonHelper.getAsJsonObject(json, "visibleCondition")).deepCopy()
                : new JsonObject();

        if (offerId.isBlank()) throw new IllegalArgumentException("Shop offer id is blank: " + sourceDescription);
        if (costA == null && walletCost <= 0) {
            throw new IllegalArgumentException("Shop offer is missing both costA and walletCost: " + sourceDescription);
        }
        if (title.isBlank()) title = result.itemId();
        return new ShopOfferSpec(offerId, title, description, categoryId, sortOrder, costA, costB, result,
                maxUses, villagerXp, priceMultiplier, requiredQuestId, hideUntilUnlocked, walletCost, visibleCondition);
    }

    public boolean requiresWalletPayment() { return this.walletCost > 0; }
    public boolean requiresItemPayment() { return this.costA != null; }

    @Nonnull
    public MerchantOffer toMerchantOffer() { return this.toMerchantOffer(this.maxUses); }

    @Nonnull
    public MerchantOffer toMerchantOffer(int maxUses) {
        if (this.costA == null) throw new IllegalStateException("Merchant offer requires costA for vanilla trade conversion");
        ItemStack costAStack = this.costA.toItemStack();
        ItemStack costBStack = this.costB == null ? ItemStack.EMPTY : this.costB.toItemStack();
        ItemStack resultStack = this.result.toItemStack();
        return new MerchantOffer(costAStack, costBStack, resultStack, 0, Math.max(0, maxUses), this.villagerXp, this.priceMultiplier);
    }
}
