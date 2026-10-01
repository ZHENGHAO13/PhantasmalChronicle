package com.phantasm.briefing.client;

import com.phantasm.briefing.data.QuestHintTarget;
import com.phantasm.briefing.data.QuestTrackerEntry;
import com.phantasm.briefing.data.ManualReadPromptState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class QuestTrackerClientState {
    @Nullable
    private static QuestTrackerEntry trackedEntry;
    private static boolean trackerHudEnabled = true;
    private static List<QuestHintTarget> hintTargets = List.of();
    @Nullable
    private static ManualReadPromptState manualReadPrompt;

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

    public static List<QuestHintTarget> getHintTargets() {
        return hintTargets;
    }

    public static void setHints(List<QuestHintTarget> targets) {
        hintTargets = targets == null ? List.of() : List.copyOf(targets);
    }

    @Nullable
    public static ManualReadPromptState getManualReadPrompt() {
        return manualReadPrompt;
    }

    public static void setManualReadPrompt(@Nullable ManualReadPromptState prompt) {
        manualReadPrompt = prompt;
    }

    public static void clear() {
        trackedEntry = null;
        trackerHudEnabled = true;
        hintTargets = List.of();
        manualReadPrompt = null;
    }
}
