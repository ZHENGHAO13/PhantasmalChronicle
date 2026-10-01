package com.phantasm.briefing.integration;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class FtbCompletionTracker {
    private final Set<CacheKey> completedQuests = ConcurrentHashMap.newKeySet();

    static String normalizeQuestId(String questId) {
        String normalized = questId == null ? "" : questId.trim();
        while (normalized.startsWith("#")) {
            normalized = normalized.substring(1).trim();
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (isHexQuestId(normalized)) {
            return "0".repeat(16 - normalized.length()) + normalized;
        }
        return normalized;
    }

    static boolean sameQuestId(String firstQuestId, String secondQuestId) {
        String first = normalizeQuestId(firstQuestId);
        String second = normalizeQuestId(secondQuestId);
        return !first.isBlank() && first.equals(second);
    }

    boolean markCompleted(UUID playerId, String questId) {
        CacheKey key = createKey(playerId, questId);
        return key != null && completedQuests.add(key);
    }

    void markIncomplete(UUID playerId, String questId) {
        CacheKey key = createKey(playerId, questId);
        if (key != null) {
            completedQuests.remove(key);
        }
    }

    boolean isCompleted(UUID playerId, String questId) {
        CacheKey key = createKey(playerId, questId);
        return key != null && completedQuests.contains(key);
    }

    void replaceCompleted(UUID playerId, Collection<String> questIds) {
        clear(playerId);
        if (questIds == null) {
            return;
        }
        for (String questId : questIds) {
            markCompleted(playerId, questId);
        }
    }

    void clear(UUID playerId) {
        if (playerId != null) {
            completedQuests.removeIf(key -> key.playerId().equals(playerId));
        }
    }

    private static CacheKey createKey(UUID playerId, String questId) {
        String normalizedQuestId = normalizeQuestId(questId);
        return playerId == null || normalizedQuestId.isBlank()
                ? null
                : new CacheKey(playerId, normalizedQuestId);
    }

    private static boolean isHexQuestId(String questId) {
        if (questId.isBlank() || questId.length() > 16) {
            return false;
        }
        for (int index = 0; index < questId.length(); index++) {
            if (Character.digit(questId.charAt(index), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private record CacheKey(UUID playerId, String questId) {
    }
}
