package dev.linqfy.bigCasares.modules.caichet;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Distancia de un punto a un segmento (el rayo del arma contra los vigilantes). */
class WatchersTest {

    private static final Vector A = new Vector(0, 0, 0);
    private static final Vector B = new Vector(10, 0, 0);

    @Test
    void aPointOnTheSegmentIsAtDistanceZero() {
        assertEquals(0, Watchers.distanceToSegment(new Vector(4, 0, 0), A, B), 1e-9);
        assertEquals(0, Watchers.distanceToSegment(A, A, B), 1e-9);
        assertEquals(0, Watchers.distanceToSegment(B, A, B), 1e-9);
    }

    @Test
    void aPerpendicularPointIsAtThePerpendicularDistance() {
        assertEquals(3, Watchers.distanceToSegment(new Vector(5, 3, 0), A, B), 1e-9);
        assertEquals(2, Watchers.distanceToSegment(new Vector(7, 0, -2), A, B), 1e-9);
    }

    @Test
    void aPointBeyondAnEndIsMeasuredToThatEnd() {
        assertEquals(3, Watchers.distanceToSegment(new Vector(-3, 0, 0), A, B), 1e-9);
        assertEquals(5, Watchers.distanceToSegment(new Vector(13, 4, 0), A, B), 1e-9);
    }
}
