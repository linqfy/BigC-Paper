package dev.linqfy.bigCasares.modules.tideframe.engine.core;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScreenCursorInteractionTest {
    private static final long MS = 1_000_000L;

    @Test void stationaryMouseStopsDriftOnNextRefresh() {
        var input = new ScreenCursorInteraction(0, 0, List.of(), 60, 0);
        input.refresh(0, 0, 100 * MS, 500);
        input.refresh(7, 0, 150 * MS, 500);
        assertEquals(.2, input.refresh(7, 0, 200 * MS, 500).cursor().x(), 1e-12);
    }

    @Test void visualHoverAndActualClickAgreeWhenPredictionCrossesIntoOtherButton() {
        var input = new ScreenCursorInteraction(0, 0, List.of(
                new HitTester.Rect("actual", .2, 0, .02, .1),
                new HitTester.Rect("other", .25, 0, .04, .1)), 60, .02);
        input.refresh(0, 0, 100 * MS, 500);
        var frame = input.refresh(7, 0, 150 * MS, 500);
        assertEquals("actual", frame.selection().orElseThrow());
        assertEquals(frame.selection(), frame.hover());
        assertEquals("actual", HitTester.hit(List.of(
                new HitTester.Rect("actual", .2, 0, .02, .1)), frame.cursor(), 0).orElseThrow());
    }

    @Test void highPingLeadStaysSmallAndReversalRespondsImmediately() {
        var input = new ScreenCursorInteraction(0, 0, List.of(), 75, 0);
        input.refresh(0, 0, 100 * MS, 2000);
        var forward = input.refresh(7, 0, 150 * MS, 2000);
        assertTrue(forward.cursor().x() >= .2);
        assertTrue(forward.cursor().x() <= .25 + 1e-12);
        var reversed = input.refresh(3.5, 0, 200 * MS, 2000);
        assertTrue(reversed.cursor().x() <= .1 + 1e-12);
        assertTrue(reversed.cursor().x() >= .05 - 1e-12);
    }

    @Test void replacingTargetsKeepsCursorAndOrientationContinuous() {
        var input = new ScreenCursorInteraction(0, 0, List.of(), 0, 0);
        double up = -.2 * ScreenCursorInteraction.PITCH_PER_HALF_HEIGHT;
        input.refresh(7, up, 100 * MS, 0);
        var targets = new ArrayList<>(List.of(new HitTester.Rect("new-frame", .2, .2, .1, .1)));
        input.replaceTargets(targets);
        targets.clear();
        var frame = input.refresh(7, up, 150 * MS, 0);
        assertEquals(.2, frame.cursor().x(), 1e-12);
        assertEquals(.2, frame.cursor().y(), 1e-12);
        assertEquals("new-frame", frame.selection().orElseThrow());
        assertThrows(NullPointerException.class, () -> input.replaceTargets(null));
        assertEquals("new-frame", input.refresh(7, up, 200 * MS, 0).selection().orElseThrow());
    }

    @Test void initialOrientationStartsAtScreenCenter() {
        var input = new ScreenCursorInteraction(42, -13, List.of(), 0, 0);
        var frame = input.refresh(42, -13, 100 * MS, 0);
        assertEquals(new PlaneProjection.Point(0, 0), frame.cursor());
        assertTrue(frame.hover().isEmpty());
        assertTrue(frame.selection().isEmpty());
    }

    @Test void yawWrapUsesShortestPositiveTwoDegreeDelta() {
        var input = new ScreenCursorInteraction(179, 0, List.of(), 0, 0);
        var frame = input.refresh(-179, 0, 100 * MS, 0);
        assertEquals(2.0 / 35, frame.cursor().x(), 1e-12);
        assertEquals(0, frame.cursor().y(), 1e-12);
    }

    @Test void negativeYawWrapAndHalfTurnHaveDefinedDirection() {
        var input = new ScreenCursorInteraction(-179, 0, List.of(), 0, 0);
        assertEquals(-2.0 / 35, input.refresh(179, 0, 100 * MS, 0).cursor().x(), 1e-12);
        var halfTurn = new ScreenCursorInteraction(0, 0, List.of(), 0, 0);
        assertEquals(-1, halfTurn.refresh(180, 0, 100 * MS, 0).cursor().x(), 1e-12);
    }

    @Test void lookingDownMovesCursorDownAndUpMovesItUp() {
        var input = new ScreenCursorInteraction(0, 0, List.of(), 0, 0);
        assertEquals(-10 / ScreenCursorInteraction.PITCH_PER_HALF_HEIGHT, input.refresh(0, 10, 100 * MS, 0).cursor().y(), 1e-12);
        assertEquals(10 / ScreenCursorInteraction.PITCH_PER_HALF_HEIGHT, input.refresh(0, -10, 120 * MS, 0).cursor().y(), 1e-12);
    }

    @Test void clampDiscardsOvershootSoReverseMovementRespondsImmediately() {
        var input = new ScreenCursorInteraction(0, 0, List.of(), 0, 0);
        var clamped = input.refresh(70, -50, 100 * MS, 0);
        assertEquals(new PlaneProjection.Point(1, 1), clamped.cursor());
        var reversed = input.refresh(63, -50 + .2 * ScreenCursorInteraction.PITCH_PER_HALF_HEIGHT, 120 * MS, 0);
        assertEquals(.8, reversed.cursor().x(), 1e-12);
        assertEquals(.8, reversed.cursor().y(), 1e-12);
        var minimum = input.refresh(-77, 55, 140 * MS, 0);
        assertEquals(new PlaneProjection.Point(-1, -1), minimum.cursor());
    }

    @Test void onlyRelativeOrientationMattersAndApiTakesNoWorldPosition() throws Exception {
        var first = new ScreenCursorInteraction(0, 0, List.of(), 0, 0);
        var second = new ScreenCursorInteraction(123, -34, List.of(), 0, 0);
        var firstPosition = first.refresh(7, 5, 100 * MS, 0).cursor();
        var secondPosition = second.refresh(130, -29, 100 * MS, 0).cursor();
        assertEquals(firstPosition.x(), secondPosition.x(), 1e-12);
        assertEquals(firstPosition.y(), secondPosition.y(), 1e-12);
        assertArrayEquals(new Class<?>[] {double.class, double.class, long.class, int.class},
                ScreenCursorInteraction.class.getMethod("refresh", double.class, double.class,
                        long.class, int.class).getParameterTypes());
    }

    @Test void distantTargetCannotReceivePredictedHoverOrClick() {
        var target = new HitTester.Rect("predicted", .5, 0, .1, .1);
        var input = new ScreenCursorInteraction(0, 0, List.of(target), 60, 0);
        input.refresh(-10.5, 0, 100 * MS, 80);
        var frame = input.refresh(3.5, 0, 140 * MS, 80);
        assertTrue(frame.cursor().x() >= .1 && frame.cursor().x() <= .15 + 1e-12);
        assertTrue(frame.hover().isEmpty());
        assertTrue(frame.selection().isEmpty(), "Click uses actual .1");
        assertTrue(input.refresh(3.5, 0, 141 * MS, 80).selection().isEmpty());
    }

    @Test void actualSelectionAndVisualCursorSnapToNearestEdge() {
        var input = new ScreenCursorInteraction(0, 0,
                List.of(new HitTester.Rect("snap", 0, 0, .4, .2)), 0, .04);
        var frame = input.refresh(.23 * 35, 0, 100 * MS, 0);
        assertEquals(.2, frame.cursor().x(), 1e-12);
        assertEquals("snap", frame.hover().orElseThrow());
        assertEquals("snap", frame.selection().orElseThrow());
    }

    @Test void oldOrDuplicateTimestampsDoNotMoveCursorOrConsumeOrientationDelta() {
        var input = new ScreenCursorInteraction(0, 0, List.of(), 0, 0);
        assertEquals(.2, input.refresh(7, 0, 100 * MS, 0).cursor().x(), 1e-12);
        assertEquals(.2, input.refresh(35, 0, 90 * MS, 0).cursor().x(), 1e-12);
        assertEquals(.2, input.refresh(-35, 0, 100 * MS, 0).cursor().x(), 1e-12);
        assertEquals(.4, input.refresh(14, 0, 110 * MS, 0).cursor().x(), 1e-12);
    }

    @Test void stoppedInputRemainsStationaryAfterLongGap() {
        var input = new ScreenCursorInteraction(0, 0, List.of(), 60, 0);
        input.refresh(-10.5, 0, 100 * MS, 80);
        assertTrue(input.refresh(3.5, 0, 140 * MS, 80).cursor().x() <= .15 + 1e-12);
        assertEquals(.1, input.refresh(3.5, 0, 400 * MS, 80).cursor().x(), 1e-12);
    }

    @Test void copiesTargetListAndKeepsTopmostHitPriority() {
        var targets = new ArrayList<>(List.of(
                new HitTester.Rect("bottom", 0, 0, .4, .4),
                new HitTester.Rect("top", 0, 0, .2, .2)));
        var input = new ScreenCursorInteraction(0, 0, targets, 0, 0);
        targets.clear();
        assertEquals("top", input.refresh(0, 0, 100 * MS, 0).selection().orElseThrow());
    }

    @Test void rejectsNonFiniteAnglesAndInvalidConfiguration() {
        for (double bad : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new ScreenCursorInteraction(bad, 0, List.of(), 0, 0));
            assertThrows(IllegalArgumentException.class,
                    () -> new ScreenCursorInteraction(0, bad, List.of(), 0, 0));
            assertThrows(IllegalArgumentException.class,
                    () -> new ScreenCursorInteraction(0, 0, List.of(), 0, bad));
            var input = new ScreenCursorInteraction(0, 0, List.of(), 0, 0);
            assertThrows(IllegalArgumentException.class, () -> input.refresh(bad, 0, 100 * MS, 0));
            assertThrows(IllegalArgumentException.class, () -> input.refresh(0, bad, 100 * MS, 0));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new ScreenCursorInteraction(0, 0, List.of(), -1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ScreenCursorInteraction(0, 0, List.of(), 0, -.01));
        assertThrows(NullPointerException.class,
                () -> new ScreenCursorInteraction(0, 0, null, 0, 0));
    }

    @Test void hugeFiniteAnglesStayFiniteAndValidationDoesNotChangeState() {
        var input = new ScreenCursorInteraction(Double.MAX_VALUE, Double.MAX_VALUE, List.of(), 0, 0);
        var frame = input.refresh(-Double.MAX_VALUE, -Double.MAX_VALUE, 100 * MS, 0);
        assertTrue(Double.isFinite(frame.cursor().x()));
        assertTrue(Double.isFinite(frame.cursor().y()));
        assertTrue(Math.abs(frame.cursor().x()) <= 1);
        assertTrue(Math.abs(frame.cursor().y()) <= 1);
        assertThrows(IllegalArgumentException.class,
                () -> input.refresh(Double.NaN, 0, 120 * MS, 0));
        assertEquals(frame.cursor(), input.refresh(-Double.MAX_VALUE, -Double.MAX_VALUE, 140 * MS, 0).cursor());
    }
    @Test void horizontalAndVerticalMovementUseTheSameDegreesPerScreenPixel() {
        var input = new ScreenCursorInteraction(0, 0, List.of(), 0, 0);
        var frame = input.refresh(7, 7, 100 * MS, 0);
        double horizontalPixels = frame.cursor().x() * 128, verticalPixels = -frame.cursor().y() * 72;
        assertEquals(horizontalPixels, verticalPixels, 1e-9);
    }
    @Test void rebaseAdoptsAStalePoseWithoutMovingTheCursor() {
        var input = new ScreenCursorInteraction(0, 0, List.of(), 0, 0);
        input.rebase(0, 80);
        assertEquals(new PlaneProjection.Point(0, 0), input.refresh(0, 80, 100 * MS, 0).cursor());
        assertTrue(input.refresh(0, 82, 150 * MS, 0).cursor().y() < 0, "Movement after the rebase still counts");
    }
    @Test void sensitivityScalesMovementAndIsBounded() {
        var normal = new ScreenCursorInteraction(0, 0, List.of(), 0, 0);
        var fast = new ScreenCursorInteraction(0, 0, List.of(), 0, 0); fast.setSensitivity(2);
        double a = normal.refresh(5, 0, 100 * MS, 0).cursor().x(), b = fast.refresh(5, 0, 100 * MS, 0).cursor().x();
        assertEquals(2 * a, b, 1e-12);
        assertThrows(IllegalArgumentException.class, () -> fast.setSensitivity(0.1));
        assertThrows(IllegalArgumentException.class, () -> fast.setSensitivity(Double.NaN));
    }
}
