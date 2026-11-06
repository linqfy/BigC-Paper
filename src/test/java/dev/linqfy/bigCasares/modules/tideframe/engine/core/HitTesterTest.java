package dev.linqfy.bigCasares.modules.tideframe.engine.core;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.linqfy.bigCasares.modules.tideframe.engine.core.PlaneProjectionTest.assertPoint;

class HitTesterTest {
    private static final HitTester.Rect BOX = new HitTester.Rect("box", 0, 0, .4, .2);
    @Test void testsRectangleBoundsIncludingEdges() {
        assertEquals("box", HitTester.hit(List.of(BOX), new PlaneProjection.Point(.2, .1), 0).orElseThrow());
        assertTrue(HitTester.hit(List.of(BOX), new PlaneProjection.Point(.2001, .1), 0).isEmpty());
    }
    @Test void topmostInsideElementWinsEvenWhenAnotherCenterIsCloser() {
        var top = new HitTester.Rect("top", .2, 0, .8, .4);
        assertEquals("top", HitTester.hit(List.of(BOX, top), new PlaneProjection.Point(0, 0), 0).orElseThrow());
    }
    @Test void snapUsesNearestEdgeRatherThanCenter() {
        var wide = new HitTester.Rect("wide", -.5, 0, 1, .2);
        var small = new HitTester.Rect("small", .3, 0, .1, .1);
        assertEquals("wide", HitTester.hit(List.of(wide, small), new PlaneProjection.Point(.05, 0), .1).orElseThrow());
        assertPoint(0, 0, HitTester.snapped(List.of(wide, small), new PlaneProjection.Point(.05, 0), .1));
    }
    @Test void snapRespectsEuclideanRadiusAtCornersAndDoesNotJumpToCenter() {
        var p = new PlaneProjection.Point(.23, .14);
        assertEquals("box", HitTester.hit(List.of(BOX), p, .05).orElseThrow());
        assertPoint(.2, .1, HitTester.snapped(List.of(BOX), p, .05));
        assertTrue(HitTester.hit(List.of(BOX), p, .049).isEmpty());
        assertEquals(p, HitTester.snapped(List.of(BOX), p, .049));
    }
    @Test void nearestTiesUseTopmostAndInsidePositionRemainsUnchanged() {
        var duplicate = new HitTester.Rect("top", 0, 0, .4, .2);
        assertEquals("top", HitTester.hit(List.of(BOX, duplicate), new PlaneProjection.Point(.25, 0), .1).orElseThrow());
        var inside = new PlaneProjection.Point(.1, .05);
        assertEquals(inside, HitTester.snapped(List.of(BOX), inside, .1));
    }
    @Test void emptyListHasNoTarget() {
        var p = new PlaneProjection.Point(.5, .5);
        assertTrue(HitTester.hit(List.of(), p, .2).isEmpty());
        assertEquals(p, HitTester.snapped(List.of(), p, .2));
    }
    @Test void rejectsInvalidRectanglesAndSnapRadius() {
        assertThrows(IllegalArgumentException.class, () -> new HitTester.Rect("bad", 0, 0, -1, .2));
        assertThrows(IllegalArgumentException.class, () -> HitTester.hit(List.of(BOX), new PlaneProjection.Point(0, 0), Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> HitTester.hit(List.of(BOX), new PlaneProjection.Point(0, 0), -.1));
    }
}
