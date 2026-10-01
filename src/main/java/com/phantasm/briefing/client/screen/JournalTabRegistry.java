package com.phantasm.briefing.client.screen;

import com.phantasm.briefing.client.ui.JournalOrnaments;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Registry backing the journal's top navigation. Built-in pages are registered first; future pages can append tabs. */
public final class JournalTabRegistry {
    public static final String QUESTS = "quests";
    public static final String MANUAL = "manual";

    private static final Map<String, TabDefinition> TABS = new LinkedHashMap<>();

    static {
        registerBuiltIn(QUESTS, "journal.phantasmbriefing.tab_quests", JournalOrnaments.ACCENT_ACTIVE);
        registerBuiltIn(MANUAL, "journal.phantasmbriefing.tab_manual", JournalOrnaments.ACCENT_ACTIVE);
    }

    private JournalTabRegistry() {
    }

    public static synchronized void register(String id, String titleTranslationKey, int accent,
                                             Supplier<? extends JournalTabPage> pageFactory) {
        String normalizedId = normalize(id);
        if (normalizedId.isBlank() || titleTranslationKey == null || titleTranslationKey.isBlank()
                || pageFactory == null) {
            throw new IllegalArgumentException("Journal tab id, translation key and page factory are required");
        }
        if (TABS.containsKey(normalizedId)) {
            throw new IllegalArgumentException("Journal tab is already registered: " + normalizedId);
        }
        TABS.put(normalizedId, new TabDefinition(normalizedId, titleTranslationKey.trim(), accent, pageFactory));
    }

    public static synchronized List<TabDefinition> tabs() {
        return List.copyOf(new ArrayList<>(TABS.values()));
    }

    public static synchronized boolean contains(String id) {
        return TABS.containsKey(normalize(id));
    }

    public static synchronized JournalTabPage createPage(String id) {
        TabDefinition definition = TABS.get(normalize(id));
        return definition == null || definition.pageFactory() == null ? null : definition.pageFactory().get();
    }

    private static void registerBuiltIn(String id, String titleTranslationKey, int accent) {
        TABS.put(id, new TabDefinition(id, titleTranslationKey, accent, null));
    }

    private static String normalize(String id) {
        return id == null ? "" : id.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public record TabDefinition(String id, String titleTranslationKey, int accent,
                                Supplier<? extends JournalTabPage> pageFactory) {
    }
}
