package dev.linqfy.bigCasares.modules.tideframe.engine.core;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** behavior.keyboard-navigation: where W, A, S and D take the focus. */
class KeyboardFocusTest {
    private static final long MS = 1_000_000L;
    // A shop: two columns of items and a wide "buy" bar under both.
    private static final List<HitTester.Rect> SHOP = List.of(
            new HitTester.Rect("a", -.5, .5, .3, .2), new HitTester.Rect("b", .5, .5, .3, .2),
            new HitTester.Rect("c", -.5, -.1, .3, .2), new HitTester.Rect("d", .5, -.1, .3, .2),
            new HitTester.Rect("buy", 0, -.7, 1.2, .2));
    private static String next(double x, double y, int dx, int dy) {
        return KeyboardFocus.next(SHOP, new PlaneProjection.Point(x, y), dx, dy).map(HitTester.Rect::id).orElse(null);
    }

    @Test void eachKeyGoesToTheNeighbourThatWayAndStopsAtTheEdge() {
        assertEquals("b", next(-.5, .5, 1, 0));
        assertEquals("c", next(-.5, .5, 0, -1));
        assertNull(next(-.5, .5, -1, 0), "nothing further left: the focus stays");
        assertNull(next(-.5, .5, 0, 1));
        assertEquals("c", next(.5, -.1, -1, 0));
        assertEquals("buy", next(.5, -.1, 0, -1), "the wide bar is below either column");
        assertEquals("buy", next(-.5, -.1, 0, -1));
        assertEquals("c", next(-.4, -.7, 0, 1), "from the bar, up goes to the column the cursor is nearer");
        assertEquals("d", next(.4, -.7, 0, 1));
    }

    @Test void aNeighbourOnePixelHigherIsBesideNotAbove() {
        var row = List.of(new HitTester.Rect("left", -.3, 0, .3, .2), new HitTester.Rect("right", .3, 1 / 72.0, .3, .2));
        assertTrue(KeyboardFocus.next(row, new PlaneProjection.Point(-.3, 0), 0, 1).isEmpty());
        assertEquals("right", KeyboardFocus.next(row, new PlaneProjection.Point(-.3, 0), 1, 0).orElseThrow().id());
    }

    @Test void withTheCursorOverNoButtonTheFirstPressEntersOne() {
        assertEquals("b", next(.9, .3, -1, 0), "the nearest button that way from the cursor");
        assertEquals("d", next(.95, -.95, 1, 0), "nothing that way: the nearest button");
        assertTrue(KeyboardFocus.next(List.of(), new PlaneProjection.Point(0, 0), 1, 0).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> next(0, 0, 1, 1));
    }

    @Test void theCursorJumpsToTheButtonAndTheMouseCarriesOnFromThere() {
        var input = new ScreenCursorInteraction(0, 0, SHOP, 60, 0);
        input.refresh(0, 0, 100 * MS, 50);
        var frame = input.focus(0, -1, 110 * MS, 50).orElseThrow();
        assertEquals("buy", frame.hover().orElseThrow(), "nothing under the cursor at the centre: the nearest button below");
        assertEquals(new PlaneProjection.Point(0, -.7), frame.cursor());
        assertEquals("buy", frame.selection().orElseThrow(), "a click now presses it");
        assertEquals(new PlaneProjection.Point(0, -.7), input.current(115 * MS, 50).cursor(), "no momentum from the jump");
        // 3.5 degrees of yaw is a tenth of the half width: the mouse moves on from the button, not from the old spot
        // (ping 0: no prediction lead in the number).
        assertEquals(.1, input.refresh(3.5, 0, 200 * MS, 0).cursor().x(), 1e-9);
        assertTrue(input.focus(0, -1, 210 * MS, 50).isEmpty(), "nothing below the bar");
    }
}
