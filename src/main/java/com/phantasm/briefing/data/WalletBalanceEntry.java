package com.phantasm.briefing.data;

public record WalletBalanceEntry(
        String walletId,
        String title,
        String currencyName,
        int balance
) {
}
