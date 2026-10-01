package com.phantasm.briefing.integration;

import java.util.List;
import java.util.UUID;

public final class FtbCompletionTrackerTest {
    public static void main(String[] args) {
        UUID firstPlayer = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondPlayer = UUID.fromString("00000000-0000-0000-0000-000000000002");
        FtbCompletionTracker tracker = new FtbCompletionTracker();

        require("0000000000ABCDEF".equals(FtbCompletionTracker.normalizeQuestId(" #abcdef ")));
        require(FtbCompletionTracker.sameQuestId("#abcdef", "ABCDEF"));
        require(tracker.markCompleted(firstPlayer, "#abcdef"));
        require(!tracker.markCompleted(firstPlayer, "0000000000ABCDEF"));
        require(tracker.isCompleted(firstPlayer, "ABCDEF"));
        require(!tracker.isCompleted(secondPlayer, "ABCDEF"));

        tracker.markIncomplete(firstPlayer, "ABCDEF");
        require(tracker.markCompleted(firstPlayer, "ABCDEF"));
        tracker.replaceCompleted(firstPlayer, List.of("NEW"));
        require(!tracker.isCompleted(firstPlayer, "ABCDEF"));
        require(tracker.isCompleted(firstPlayer, "new"));
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw new AssertionError();
        }
    }
}
