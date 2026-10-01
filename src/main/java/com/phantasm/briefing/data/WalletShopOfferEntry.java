package com.phantasm.briefing.data;

public record WalletShopOfferEntry(
        String entryId,
        String categoryId,
        String categoryTitle,
        String title,
        String description,
        String resultItemId,
        int resultCount,
        String costSummary,
        String rewardSummary,
        String stateText,
        String primaryCostItemId,
        int primaryCostCount,
        String secondaryCostItemId,
        int secondaryCostCount,
        String statusBadge,
        boolean unlocked,
        boolean affordable,
        boolean walletPayment
) {
    public static final String STATUS_READY = "READY";
    public static final String STATUS_SHORT = "SHORT";
    public static final String STATUS_LOCKED = "LOCKED";

    public boolean isReadyState() {
        return STATUS_READY.equals(this.statusBadge);
    }

    public boolean isShortState() {
        return STATUS_SHORT.equals(this.statusBadge);
    }

    public boolean isLockedState() {
        return STATUS_LOCKED.equals(this.statusBadge);
    }
}
