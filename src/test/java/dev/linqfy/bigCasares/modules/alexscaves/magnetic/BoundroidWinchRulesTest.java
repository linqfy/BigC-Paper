package dev.linqfy.bigCasares.modules.alexscaves.magnetic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundroidWinchRulesTest {
    @Test
    void latchProgressRunsFromZeroToFive() {
        int progress = 0;
        for (int i = 0; i < 8; i++) {
            progress = BoundroidWinchRules.stepLatchProgress(progress, true);
        }
        assertEquals(5, progress);
        for (int i = 0; i < 8; i++) {
            progress = BoundroidWinchRules.stepLatchProgress(progress, false);
        }
        assertEquals(0, progress);
        assertEquals(1.0F, BoundroidWinchRules.latchFraction(5));
        assertEquals(0.4F, BoundroidWinchRules.latchFraction(2), 1.0E-6F);
    }

    @Test
    void latchPoseShowsOnceMostlyLatched() {
        assertNull(BoundroidWinchRules.latchState(2));
        assertEquals("latch", BoundroidWinchRules.latchState(3));
        assertEquals("latch", BoundroidWinchRules.latchState(5));
    }

    @Test
    void chainLengthBobsWhenLatchedAndTrailsWhenLoose() {
        assertEquals(3.5, BoundroidWinchRules.chainLength(false, 123));
        for (int age = 0; age < 200; age++) {
            double length = BoundroidWinchRules.chainLength(true, age);
            assertTrue(length >= 1.0 && length <= 1.5, "age " + age);
        }
    }

    @Test
    void chainPullsOnlyPastItsLengthAndNeverWhileSlamming() {
        assertEquals(0.0, BoundroidWinchRules.pull(1.0, 1.25, false, true));
        assertEquals(0.0, BoundroidWinchRules.pull(5.0, 1.25, true, true));
        assertEquals(0.15, BoundroidWinchRules.pull(1.75, 1.25, false, false) * 3, 1.0E-9);
        assertEquals(0.3, BoundroidWinchRules.pull(9.0, 1.25, false, true), 1.0E-9);
        assertEquals(0.1, BoundroidWinchRules.pull(9.0, 1.25, false, false), 1.0E-9);
        assertTrue(BoundroidWinchRules.stopPullingUp(true, 0));
        assertTrue(BoundroidWinchRules.stopPullingUp(false, 3));
        assertFalse(BoundroidWinchRules.stopPullingUp(false, 0));
    }

    @Test
    void headIsBroughtBackThroughBlocksOnlyWhenFarAndUnseen() {
        assertTrue(BoundroidWinchRules.phasesThroughBlocks(false, false, 7.5));
        assertFalse(BoundroidWinchRules.phasesThroughBlocks(true, false, 7.5));
        assertFalse(BoundroidWinchRules.phasesThroughBlocks(false, true, 7.5));
        assertFalse(BoundroidWinchRules.phasesThroughBlocks(false, false, 7.0));
    }

    @Test
    void latchTimerCountsWhileTheStateShouldChange() {
        // Loose under a close ceiling: counts, flips after more than 5 ticks.
        int timer = 0;
        for (int i = 0; i < 5; i++) {
            timer = BoundroidWinchRules.nextLatchTimer(false, timer, 1.0, true, 0);
        }
        assertFalse(BoundroidWinchRules.flipsLatch(timer));
        timer = BoundroidWinchRules.nextLatchTimer(false, timer, 1.0, true, 0);
        assertTrue(BoundroidWinchRules.flipsLatch(timer));
        // Dazed or a far ceiling stops a loose winch from latching.
        assertEquals(0, BoundroidWinchRules.nextLatchTimer(false, 4, 1.0, true, 30));
        assertEquals(0, BoundroidWinchRules.nextLatchTimer(false, 4, 5.0, true, 0));
        // Latched: a far ceiling, a dazed or a dead winch lets go; otherwise the timer resets.
        assertEquals(5, BoundroidWinchRules.nextLatchTimer(true, 4, 3.5, true, 0));
        assertEquals(5, BoundroidWinchRules.nextLatchTimer(true, 4, 1.0, true, 10));
        assertEquals(5, BoundroidWinchRules.nextLatchTimer(true, 4, 1.0, false, 0));
        assertEquals(0, BoundroidWinchRules.nextLatchTimer(true, 4, 1.0, true, 0));
    }

    @Test
    void winchHopsUpOnlyFromTheGroundUnderAHighCeiling() {
        assertTrue(BoundroidWinchRules.hopsUp(true, 0, true, 0, 5.0, true));
        assertFalse(BoundroidWinchRules.hopsUp(false, 0, true, 0, 5.0, true));
        assertFalse(BoundroidWinchRules.hopsUp(true, 10, true, 0, 5.0, true));
        assertFalse(BoundroidWinchRules.hopsUp(true, 0, true, 3, 5.0, true));
        assertFalse(BoundroidWinchRules.hopsUp(true, 0, true, 0, 2.0, true));
        assertFalse(BoundroidWinchRules.hopsUp(true, 0, true, 0, 5.0, false));
    }

    @Test
    void headSlamsWhenTheTargetIsDirectlyBelow() {
        assertTrue(BoundroidWinchRules.startsSlam(true, 1.1, 0));
        assertFalse(BoundroidWinchRules.startsSlam(true, 1.2, 0));
        assertFalse(BoundroidWinchRules.startsSlam(true, 0.5, 4));
        assertFalse(BoundroidWinchRules.startsSlam(false, 0.5, 0));
        assertEquals(30, BoundroidWinchRules.slamPause(0));
        assertEquals(49, BoundroidWinchRules.slamPause(19));
        assertEquals(60, BoundroidWinchRules.dazedTicks(0));
        assertEquals(119, BoundroidWinchRules.dazedTicks(59));
    }

    @Test
    void ceilingDistanceAndLinkCount() {
        assertEquals(1.5, BoundroidWinchRules.ceilingDistance(72, 70.5));
        assertEquals(1, BoundroidWinchRules.links(0.2));
        assertEquals(4, BoundroidWinchRules.links(3.2));
        assertEquals(24, BoundroidWinchRules.links(90.0));
    }
}
