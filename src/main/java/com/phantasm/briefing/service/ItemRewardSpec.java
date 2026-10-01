package com.phantasm.briefing.service;

import java.util.Optional;
import java.util.regex.Pattern;

public record ItemRewardSpec(String itemId, String nbt) {
    private static final int MAX_INPUT_LENGTH = 8192;
    private static final Pattern ITEM_ID_PATTERN = Pattern.compile("(?:[a-z0-9_.-]+:)?[a-z0-9/._-]+");

    public static Optional<ItemRewardSpec> parse(String value) {
        String input = value == null ? "" : value.trim();
        if (input.isBlank() || input.length() > MAX_INPUT_LENGTH) {
            return Optional.empty();
        }

        int nbtStart = input.indexOf('{');
        String itemId = (nbtStart < 0 ? input : input.substring(0, nbtStart)).trim();
        String nbt = nbtStart < 0 ? "" : input.substring(nbtStart).trim();
        if (!ITEM_ID_PATTERN.matcher(itemId).matches()) {
            return Optional.empty();
        }
        if (!nbt.isEmpty() && !nbt.endsWith("}")) {
            return Optional.empty();
        }
        return Optional.of(new ItemRewardSpec(itemId, nbt));
    }
}
