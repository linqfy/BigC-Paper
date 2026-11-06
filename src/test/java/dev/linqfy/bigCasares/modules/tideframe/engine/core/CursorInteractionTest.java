package dev.linqfy.bigCasares.modules.tideframe.engine.core;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CursorInteractionTest {
    private static final Vec3 EYE = new Vec3(0, 64, 0);
    private static final long MS = 1_000_000L;
    private static final List<HitTester.Rect> TARGETS = List.of(
            new HitTester.Rect("center", 0, 0, .2, .2),
            new HitTester.Rect("right", .6, 0, .2, .2));

    @Test void translationAtFixedYawAndPitchChangesProjectedCursor() {
        var input = input(TARGETS, 0);
        input.refresh(EYE, 0, 0, 100 * MS, 0);
        var translated = input.refresh(EYE.add(new Vec3(-.24, 0, 0)), 0, 0, 120 * MS, 0);
        assertEquals(.2, translated.cursor().x(), 1e-12);
        assertTrue(translated.inside());
        assertTrue(translated.selection().isEmpty());
    }
    @Test void verticalEyeChangeAtFixedAnglesChangesProjectedCursor() {
        var input = input(TARGETS, 0);
        input.refresh(EYE, 0, 0, 100 * MS, 0);
        var translated = input.refresh(EYE.add(new Vec3(0, -.14, 0)), 0, 0, 120 * MS, 0);
        assertEquals(-.2, translated.cursor().y(), 1e-12);
        assertTrue(translated.selection().isEmpty());
    }
    @Test void freshOutsideRayClearsOldClickableSelectionImmediately() {
        var input = input(TARGETS, 0);
        assertEquals("center", input.refresh(EYE, 0, 0, 100 * MS, 0).selection().orElseThrow());
        var clickFrame = input.refresh(EYE, 90, 0, 101 * MS, 0);
        assertFalse(clickFrame.inside());
        assertTrue(clickFrame.selection().isEmpty());
        assertTrue(clickFrame.hover().isEmpty());
    }
    @Test void freshLookSelectsNewButtonWithoutWaitingForAnotherTick() {
        var input = input(TARGETS, 0);
        input.refresh(EYE, 0, 0, 100 * MS, 0);
        var clickFrame = input.refresh(EYE, yawFor(.6), 0, 101 * MS, 0);
        assertEquals("right", clickFrame.selection().orElseThrow());
        assertEquals(.6, clickFrame.cursor().x(), 1e-12);
    }
    @Test void pointOutsidePanelCannotSnapToOutsideRectangleOrUseOldSelection() {
        var outside = new HitTester.Rect("outside", 1.3, 0, .2, .2);
        var input = input(List.of(TARGETS.getFirst(), outside), .2);
        input.refresh(EYE, 0, 0, 100 * MS, 0);
        var clickFrame = input.refresh(EYE, yawFor(1.3), 0, 101 * MS, 0);
        assertFalse(clickFrame.inside());
        assertTrue(clickFrame.selection().isEmpty());
    }
    @Test void predictionDoesNotChooseClickTargetAbsentFromCurrentRay() {
        var right = new HitTester.Rect("predicted", .145, 0, .03, .1);
        var input = input(List.of(right), 0);
        input.refresh(EYE, yawFor(-.3), 0, 100 * MS, 80);
        var current = input.refresh(EYE, yawFor(.1), 0, 140 * MS, 80);
        assertEquals("predicted", current.hover().orElseThrow());
        assertTrue(current.selection().isEmpty(), "A predicted visual position must not authorize a different button");
        assertTrue(input.refresh(EYE, yawFor(.1), 0, 141 * MS, 80).selection().isEmpty());
    }
    @Test void predictionUsesCurrentClockInsteadOfAddingConfiguredFuture() {
        var input = input(List.of(), 0);
        input.refresh(EYE, yawFor(0), 0, 100 * MS, 80);
        var frame = input.refresh(EYE, yawFor(.02), 0, 150 * MS, 80);
        assertEquals(.0264, frame.cursor().x(), 1e-12);
    }
    @Test void freshSelectionStillSupportsConfiguredSnapAtNearestEdge() {
        var input = input(List.of(new HitTester.Rect("snap", 0, 0, .4, .2)), .04);
        var frame = input.refresh(EYE, yawFor(.23), 0, 100 * MS, 0);
        assertEquals("snap", frame.selection().orElseThrow());
        assertEquals(.2, frame.cursor().x(), 1e-12);
    }
    @Test void reenteringPanelUsesCurrentPoseInsteadOfOldSelection() {
        var input = input(TARGETS, 0);
        input.refresh(EYE, 0, 0, 100 * MS, 0);
        input.refresh(EYE, 90, 0, 110 * MS, 0);
        assertEquals("right", input.refresh(EYE, yawFor(.6), 0, 120 * MS, 0).selection().orElseThrow());
    }
    private static CursorInteraction input(List<HitTester.Rect> targets, double snap) {
        return new CursorInteraction(PlaneProjection.create(EYE, 0, 0, 2), targets, 60, snap);
    }
    private static double yawFor(double x) { return Math.toDegrees(Math.atan(x * 1.2 / 2)); }
}
