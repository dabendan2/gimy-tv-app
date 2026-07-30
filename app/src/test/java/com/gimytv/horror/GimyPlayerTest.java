package com.gimytv.horror;

public class GimyPlayerTest {

    public static void runTests() {
        System.out.println("Executing GimyPlayer (TimeUtils) Unit Tests...");

        testFormatTime();
        testFormatDelta();
        testScrubbingCalculations();
        testPreviewStripLayoutAndClamping();
        testProgressSaveAllowed();
        testPauseTitleVisibility();

        System.out.println("  [PASS] All GimyPlayer (TimeUtils) tests passed successfully!");
    }

    private static void testFormatTime() {
        // Test short duration (under 1 minute)
        String t1 = TimeUtils.formatTime(45000); // 45 seconds
        if (!"00:45".equals(t1)) throw new AssertionError("Expected '00:45', got: " + t1);

        // Test normal duration (under 1 hour)
        String t2 = TimeUtils.formatTime(125000); // 2 mins 5 seconds
        if (!"02:05".equals(t2)) throw new AssertionError("Expected '02:05', got: " + t2);

        // Test long duration (over 1 hour)
        String t3 = TimeUtils.formatTime(3665000); // 1 hour 1 minute 5 seconds
        if (!"01:01:05".equals(t3)) throw new AssertionError("Expected '01:01:05', got: " + t3);
    }

    private static void testFormatDelta() {
        // Test zero change
        String d0 = TimeUtils.formatDelta(0);
        if (!"+00:00".equals(d0)) throw new AssertionError("Expected '+00:00', got: " + d0);

        // Test positive change (under 1 hour)
        String d1 = TimeUtils.formatDelta(30000); // +30 seconds
        if (!"+00:30".equals(d1)) throw new AssertionError("Expected '+00:30', got: " + d1);

        // Test negative change (under 1 hour)
        String d2 = TimeUtils.formatDelta(-150000); // -2 mins 30 seconds
        if (!"-02:30".equals(d2)) throw new AssertionError("Expected '-02:30', got: " + d2);

        // Test positive change (over 1 hour)
        String d3 = TimeUtils.formatDelta(3665000); // +1 hour 1 minute 5 seconds
        if (!"+01:01:05".equals(d3)) throw new AssertionError("Expected '+01:01:05', got: " + d3);

        // Test negative change (over 1 hour)
        String d4 = TimeUtils.formatDelta(-3665000); // -1 hour 1 minute 5 seconds
        if (!"-01:01:05".equals(d4)) throw new AssertionError("Expected '-01:01:05', got: " + d4);
    }

    private static void testScrubbingCalculations() {
        // Emulate seek stepping logic
        int initialPos = 300000; // 5 minutes
        int duration = 600000;  // 10 minutes

        // Seek forward step of 30 seconds
        int forwardTarget = Math.min(duration, initialPos + 30000);
        if (forwardTarget != 330000) {
            throw new AssertionError("Expected forward target 330,000 ms, got: " + forwardTarget);
        }

        // Seek backward step of 30 seconds
        int backwardTarget = Math.max(0, initialPos - 30000);
        if (backwardTarget != 270000) {
            throw new AssertionError("Expected backward target 270,000 ms, got: " + backwardTarget);
        }

        // Delta check
        int deltaMs = forwardTarget - initialPos;
        String deltaStr = TimeUtils.formatDelta(deltaMs);
        if (!"+00:30".equals(deltaStr)) {
            throw new AssertionError("Expected delta string '+00:30', got: " + deltaStr);
        }
    }

    private static void testPreviewStripLayoutAndClamping() {
        System.out.println("  Testing Preview Strip Layout & Clamping logic...");

        int duration = 600000; // 10 minutes (600,000 ms)
        int[] offsetsMs = { -90000, -60000, -30000, 0, 30000, 60000, 90000 };

        // Test at T = 0 (Start of movie)
        int baseTime0 = 0;
        int[] expectedTimes0 = { 0, 0, 0, 0, 30000, 60000, 90000 };
        for (int i = 0; i < 7; i++) {
            int tempTarget = baseTime0 + offsetsMs[i];
            if (tempTarget < 0) tempTarget = 0;
            if (duration > 0 && tempTarget > duration) tempTarget = duration;

            if (tempTarget != expectedTimes0[i]) {
                throw new AssertionError("At T=0, frame " + i + " expected " + expectedTimes0[i] + " ms, got: " + tempTarget);
            }
        }

        // Test at T = 570000 (Near end of movie)
        int baseTimeEnd = 570000; // 9.5 minutes
        int[] expectedTimesEnd = { 480000, 510000, 540000, 570000, 600000, 600000, 600000 };
        for (int i = 0; i < 7; i++) {
            int tempTarget = baseTimeEnd + offsetsMs[i];
            if (tempTarget < 0) tempTarget = 0;
            if (duration > 0 && tempTarget > duration) tempTarget = duration;

            if (tempTarget != expectedTimesEnd[i]) {
                throw new AssertionError("At T=570,000, frame " + i + " expected " + expectedTimesEnd[i] + " ms, got: " + tempTarget);
            }
        }

        System.out.println("    [PASS] Preview strip clamping and symmetrical layout math verified!");
    }

    private static void testProgressSaveAllowed() {
        System.out.println("  Testing Progress Save Allowed (TDD scenarios)...");

        // Scenario 1: Normal forward progress (existing=2m, new=5m, dur=10m, force=false) -> should allow (true)
        boolean s1 = TimeUtils.isProgressSaveAllowed(300000, 600000, 120000, false);
        if (!s1) throw new AssertionError("Scenario 1: Normal forward progress should be allowed");

        // Scenario 2: Backward rollback prevention (existing=5m, new=2m, dur=10m, force=false) -> should block (false)
        boolean s2 = TimeUtils.isProgressSaveAllowed(120000, 600000, 300000, false);
        if (s2) throw new AssertionError("Scenario 2: Backward rollback without force should be blocked");

        // Scenario 3: Backward rollback with force/manual seek (existing=5m, new=2m, dur=10m, force=true) -> should allow (true)
        boolean s3 = TimeUtils.isProgressSaveAllowed(120000, 600000, 300000, true);
        if (!s3) throw new AssertionError("Scenario 3: Backward rollback with force/manual seek should be allowed");

        // Scenario 4: Tiny progress overwrite prevention (existing=2m, new=10s, dur=10m, force=false) -> should block (false)
        boolean s4 = TimeUtils.isProgressSaveAllowed(10000, 600000, 120000, false);
        if (s4) throw new AssertionError("Scenario 4: Tiny progress overwrite without force should be blocked");

        // Scenario 5: Reset/manual seek to start/0 with force (existing=2m, new=0, dur=10m, force=true) -> should allow (true)
        boolean s5 = TimeUtils.isProgressSaveAllowed(0, 600000, 120000, true);
        if (!s5) throw new AssertionError("Scenario 5: Reset/manual seek to 0 with force should be allowed");

        // Scenario 6: Invalid duration (new=120s, dur=0, existing=0, force=false) -> should block (false)
        boolean s6 = TimeUtils.isProgressSaveAllowed(120000, 0, 0, false);
        if (s6) throw new AssertionError("Scenario 6: Invalid duration should be blocked");

        // Scenario 7: isNearEnd boundary check (pos=585000, dur=600000 -> 15s remaining) -> true
        boolean s7 = TimeUtils.isNearEnd(585000, 600000);
        if (!s7) throw new AssertionError("Scenario 7: Exactly 15s remaining should be near end");

        // Scenario 8: isNearEnd boundary check (pos=584999, dur=600000 -> 15.001s remaining) -> false
        boolean s8 = TimeUtils.isNearEnd(584999, 600000);
        if (s8) throw new AssertionError("Scenario 8: Over 15s remaining should not be near end");

        // Scenario 9: isNearEnd with invalid duration -> false
        boolean s9 = TimeUtils.isNearEnd(100, 0);
        if (s9) throw new AssertionError("Scenario 9: Invalid duration should not be near end");

        System.out.println("    [PASS] Progress save validation logic (including force bypass & isNearEnd) verified!");
    }

    private static void testPauseTitleVisibility() {
        System.out.println("  Testing Pause Title Visibility state rules...");

        // Scenario 1: Active playback (isPlaying=true, isSeekingMode=false, isPausedState=false) -> should NOT show pause title (false)
        boolean v1 = TimeUtils.shouldShowPauseTitle(true, false, false);
        if (v1) throw new AssertionError("Scenario 1: Active playback must hide pause title");

        // Scenario 2: Paused playback (isPlaying=false, isSeekingMode=false, isPausedState=true) -> SHOULD show pause title (true)
        boolean v2 = TimeUtils.shouldShowPauseTitle(false, false, true);
        if (!v2) throw new AssertionError("Scenario 2: Paused playback should show pause title");

        // Scenario 3: Seeking mode while playing (isPlaying=true, isSeekingMode=true, isPausedState=true) -> SHOULD show pause title (true)
        boolean v3 = TimeUtils.shouldShowPauseTitle(true, true, true);
        if (!v3) throw new AssertionError("Scenario 3: Seeking mode should show pause title");

        // Scenario 4: Resuming active playback after pause (isPlaying=true, isSeekingMode=false, isPausedState=false) -> should NOT show pause title (false)
        boolean v4 = TimeUtils.shouldShowPauseTitle(true, false, false);
        if (v4) throw new AssertionError("Scenario 4: Resumed playback must hide pause title");

        System.out.println("    [PASS] Pause title visibility state rules verified!");
    }
}
