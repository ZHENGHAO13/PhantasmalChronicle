package com.phantasm.briefing.service;

public final class ItemRewardSpecTest {
    private ItemRewardSpecTest() {
    }

    public static void main(String[] args) {
        parsesPlainItemId();
        parsesItemIdWithNbt();
        parsesNestedItemNbt();
        rejectsMalformedNbt();
        rejectsInvalidItemId();
    }

    private static void parsesPlainItemId() {
        ItemRewardSpec spec = ItemRewardSpec.parse("minecraft:emerald").orElseThrow();
        expectEquals("minecraft:emerald", spec.itemId());
        expectEquals("", spec.nbt());
    }

    private static void parsesItemIdWithNbt() {
        ItemRewardSpec spec = ItemRewardSpec.parse("mmorpg:stat_soul/family/weapon/common{tier:1}").orElseThrow();
        expectEquals("mmorpg:stat_soul/family/weapon/common", spec.itemId());
        expectEquals("{tier:1}", spec.nbt());
    }

    private static void parsesNestedItemNbt() {
        ItemRewardSpec spec = ItemRewardSpec.parse("minecraft:diamond{display:{Name:'Test'},tier:1}").orElseThrow();
        expectEquals("minecraft:diamond", spec.itemId());
        expectEquals("{display:{Name:'Test'},tier:1}", spec.nbt());
    }

    private static void rejectsMalformedNbt() {
        expectTrue(ItemRewardSpec.parse("minecraft:diamond{tier:1").isEmpty());
        expectTrue(ItemRewardSpec.parse("minecraft:diamond{tier:1}extra").isEmpty());
    }

    private static void rejectsInvalidItemId() {
        expectTrue(ItemRewardSpec.parse("minecraft:diamond sword").isEmpty());
        expectTrue(ItemRewardSpec.parse("").isEmpty());
    }

    private static void expectTrue(boolean value) {
        if (!value) {
            throw new AssertionError("Expected true");
        }
    }

    private static void expectEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }
}
