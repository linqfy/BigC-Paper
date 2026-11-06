package dev.linqfy.bigCasares.modules.tideframe.engine.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.linqfy.bigCasares.modules.tideframe.engine.core.PlaneProjectionTest.assertPoint;

class CursorPredictorTest {
    private static final long MS = 1_000_000L;
    @Test void highPingCannotThrowCursorAcrossTheScreen() {
        var cursor = linearCursor(75);
        var predicted = cursor.predict(200 * MS, 2000);
        assertTrue(predicted.x() > .2);
        assertTrue(Math.hypot(predicted.x() - .2, predicted.y()) <= .05 + 1e-12,
                "Prediction must stay within 2.5 percent of the full screen extent");
    }
    @Test void stalePredictionReturnsContinuouslyInsteadOfSnappingAtTimeout() {
        var cursor = linearCursor(75);
        var beforeTimeout = cursor.predict(289 * MS, 2000);
        var atTimeout = cursor.predict(290 * MS, 2000);
        assertEquals(.2, atTimeout.x(), 1e-12);
        assertTrue(Math.abs(beforeTimeout.x() - atTimeout.x()) < .001);
    }
    @Test void firstMovementAfterLongPacketGapDoesNotReuseOldVelocity() {
        var cursor = linearCursor(75);
        cursor.sample(new PlaneProjection.Point(.3, 0), 500 * MS);
        assertPoint(.3, 0, cursor.predict(520 * MS, 2000));
    }
    @Test void tinySamplingIntervalCannotTurnClickRefreshIntoFling() {
        var cursor = linearCursor(75);
        cursor.sample(new PlaneProjection.Point(.21, 0), 141 * MS);
        assertPoint(.21, 0, cursor.predict(142 * MS, 2000));
    }
    @Test void startsAtCenterAndFirstSampleDoesNotJump() {
        var cursor = new CursorPredictor(60);
        assertPoint(0, 0, cursor.predict(1, 80));
        cursor.sample(new PlaneProjection.Point(.3, -.2), 100 * MS);
        assertPoint(.3, -.2, cursor.predict(110 * MS, 80));
    }
    @Test void shortLeadUsesHalfPingBelowSixteenMilliseconds() {
        var cursor = linearCursor(60);
        assertPoint(.21, 0, cursor.predict(140 * MS, 4));
        assertPoint(.22, 0, cursor.predict(140 * MS, 8));
    }
    @Test void configuredHorizonCapsPredictionEvenWhenPingIsHigh() {
        var cursor = linearCursor(5);
        assertPoint(.225, 0, cursor.predict(140 * MS, 500));
    }
    @Test void oversizedConfiguredHorizonCannotExpandHighPingDisplacement() {
        var cursor = linearCursor(1000);
        assertTrue(cursor.predict(200 * MS, 10000).x() <= .25 + 1e-12);
        assertPoint(.2, 0, cursor.predict(290 * MS, 10000));
    }
    @Test void staleSamplesStopPredictingAfterQuarterSecond() {
        var cursor = linearCursor(75);
        assertPoint(.2, 0, cursor.predict(391 * MS, 500));
        assertPoint(.2, 0, cursor.predict(10000 * MS, 10000));
    }
    @Test void zeroOrNegativePingAndPastTimeDoNotExtrapolate() {
        var cursor = linearCursor(60);
        assertPoint(.2, 0, cursor.predict(200 * MS, 0));
        assertPoint(.2, 0, cursor.predict(200 * MS, -1));
        assertPoint(.2, 0, cursor.predict(100 * MS, 80));
    }
    @Test void clampsSamplesAndPredictionsToScreen() {
        var cursor = new CursorPredictor(75);
        cursor.sample(new PlaneProjection.Point(.98, -.98), 100 * MS);
        cursor.sample(new PlaneProjection.Point(.99, -.99), 110 * MS);
        assertPoint(1, -1, cursor.predict(185 * MS, 200));
        cursor.sample(new PlaneProjection.Point(8, -8), 200 * MS);
        assertPoint(1, -1, cursor.predict(200 * MS, 0));
    }
    @Test void duplicateAndOutOfOrderSamplesDoNotCorruptVelocity() {
        var cursor = linearCursor(60);
        var before = cursor.predict(200 * MS, 80);
        cursor.sample(new PlaneProjection.Point(-.5, .8), 130 * MS);
        cursor.sample(new PlaneProjection.Point(-.5, .8), 140 * MS);
        assertEquals(before, cursor.predict(200 * MS, 80));
    }
    @Test void freshPredictionLeadsByAtMostSixteenMilliseconds() {
        var cursor = new CursorPredictor(60);
        cursor.sample(new PlaneProjection.Point(0, 0), 100 * MS);
        cursor.sample(new PlaneProjection.Point(.05, 0), 150 * MS);
        assertPoint(.066, 0, cursor.predict(150 * MS, 80));
        assertPoint(.066, 0, cursor.predict(150 * MS, 500));
    }
    @Test void diagonalLeadUsesOneDistanceLimitAndStationarySampleCancelsIt() {
        var cursor = new CursorPredictor(60);
        cursor.sample(new PlaneProjection.Point(0, 0), 100 * MS);
        cursor.sample(new PlaneProjection.Point(.2, .2), 150 * MS);
        var predicted = cursor.predict(150 * MS, 500);
        assertTrue(predicted.x() > .2 && predicted.y() > .2);
        assertTrue(Math.hypot(predicted.x() - .2, predicted.y() - .2) <= .05 + 1e-12);
        cursor.sample(new PlaneProjection.Point(.2, .2), 200 * MS);
        assertPoint(.2, .2, cursor.predict(210 * MS, 500));
    }
    @Test void roundingNoiseAtStopCannotKeepPreviousVelocityAlive() {
        var cursor = new CursorPredictor(75);
        cursor.sample(new PlaneProjection.Point(0, 0), 100 * MS);
        cursor.sample(new PlaneProjection.Point(.2, 0), 150 * MS);
        cursor.sample(new PlaneProjection.Point(Math.nextUp(.2), 0), 200 * MS);
        assertPoint(Math.nextUp(.2), 0, cursor.predict(200 * MS, 80));
    }
    @Test void velocitySpikeIsSmoothedButReversalDoesNotKeepOldMomentum() {
        var cursor = new CursorPredictor(60);
        cursor.sample(new PlaneProjection.Point(0, 0), 100 * MS);
        cursor.sample(new PlaneProjection.Point(.02, 0), 150 * MS);
        cursor.sample(new PlaneProjection.Point(.04, 0), 200 * MS);
        cursor.sample(new PlaneProjection.Point(.08, 0), 250 * MS);
        double lead = cursor.predict(250 * MS, 80).x() - .08;
        assertTrue(lead > .0064 && lead < .0128, "Spike must not replace stable velocity abruptly");
        cursor.sample(new PlaneProjection.Point(.06, 0), 300 * MS);
        assertTrue(cursor.predict(300 * MS, 80).x() < .06, "Reversal must respond immediately");
    }
    @Test void disabledPredictionNeverLeadsFreshOrOldSamples() {
        var cursor = linearCursor(0);
        assertPoint(.2, 0, cursor.predict(140 * MS, 500));
        assertPoint(.2, 0, cursor.predict(200 * MS, 500));
    }
    @Test void rejectsNegativeHorizon() {
        assertThrows(IllegalArgumentException.class, () -> new CursorPredictor(-1));
    }
    private static CursorPredictor linearCursor(int horizon) {
        var cursor = new CursorPredictor(horizon);
        cursor.sample(new PlaneProjection.Point(0, 0), 100 * MS);
        cursor.sample(new PlaneProjection.Point(.2, 0), 140 * MS);
        return cursor;
    }
}
