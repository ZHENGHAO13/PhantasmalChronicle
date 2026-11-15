package com.phantasm.briefing.client;

import com.phantasm.briefing.data.QuestHintType;
import com.phantasm.briefing.data.QuestTrackerEntry;
import org.jetbrains.annotations.Nullable;

public final class QuestTrackerClientState {
    @Nullable
    private static QuestTrackerEntry trackedEntry;
    private static boolean trackerHudEnabled = true;
    private static int hintEntityId = -1;
    private static QuestHintType hintType = QuestHintType.NONE;

    private QuestTrackerClientState() {
    }

    @Nullable
    public static QuestTrackerEntry getTrackedEntry() {
        return trackedEntry;
    }

    public static void setTrackedEntry(@Nullable QuestTrackerEntry entry) {
        trackedEntry = entry;
    }

    public static boolean isTrackerHudEnabled() {
        return trackerHudEnabled;
    }

    public static void setTrackerHudEnabled(boolean enabled) {
        trackerHudEnabled = enabled;
    }

    public static int getHintEntityId() {
        return hintEntityId;
    }

    public static void setHintEntityId(int entityId) {
        hintEntityId = entityId;
    }

    public static QuestHintType getHintType() {
        return hintType;
    }

    public static void setHint(int entityId, QuestHintType type) {
        hintEntityId = entityId;
        hintType = type == null ? QuestHintType.NONE : type;
    }

    public static void clear() {
        trackedEntry = null;
        trackerHudEnabled = true;
        hintEntityId = -1;
        hintType = QuestHintType.NONE;
    }
}
