package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enlil;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnlilWindTest {

    @Test
    void gustThrowsAwayFromEnlilAndBracingResists() {
        EnlilWind.Push push = EnlilWind.gust(0, 0, 3, 4, false);
        assertEquals(EnlilTuning.GUST_PUSH, push.horizontal(), 1.0e-9);
        assertEquals(0.6, push.x() / push.horizontal(), 1.0e-9);
        assertEquals(0.8, push.z() / push.horizontal(), 1.0e-9);
        assertTrue(push.y() > 0.3);
        EnlilWind.Push braced = EnlilWind.gust(0, 0, 3, 4, true);
        assertEquals(EnlilTuning.GUST_PUSH * 0.3, braced.horizontal(), 1.0e-9);
        assertTrue(braced.y() < push.y());
        assertEquals(EnlilTuning.GUST_PUSH, EnlilWind.gust(0, 0, 0, 0, false).horizontal(), 1.0e-9,
            "someone standing inside him is still thrown");
    }

    @Test
    void tornadoColumnWidensWithHeightAndLiftsUntilTheTop() {
        assertTrue(EnlilWind.insideTornado(1.0, 0.0, 0.0));
        assertFalse(EnlilWind.insideTornado(2.0, 0.0, 0.0));
        assertTrue(EnlilWind.insideTornado(2.0, 6.0, 0.0));
        assertFalse(EnlilWind.insideTornado(0.0, EnlilTuning.TORNADO_HEIGHT + 0.5, 0.0));
        assertFalse(EnlilWind.insideTornado(0.0, -1.0, 0.0));
        EnlilWind.Push low = EnlilWind.tornadoDrag(1.0, 0.0, 0.0);
        assertTrue(low.y() > 0.3);
        assertTrue(low.z() > 0.0, "swirls around the axis");
        assertTrue(low.x() < 0.0, "pulls in a little");
        assertTrue(EnlilWind.tornadoDrag(1.0, EnlilTuning.TORNADO_HEIGHT - 1.0, 0.0).y() < 0.1);
    }

    @Test
    void tornadoStepsAreSlowerThanWalking() {
        double[] next = EnlilWind.step(0, 0, 10, 0);
        assertEquals(EnlilTuning.TORNADO_SPEED, next[0], 1.0e-9);
        assertTrue(EnlilTuning.TORNADO_SPEED < 0.2);
        double[] arrived = EnlilWind.step(0, 0, 0.01, 0);
        assertEquals(0.01, arrived[0], 1.0e-9);
    }

    @Test
    void incomingArrowsBounceBack() {
        // an arrow 3 blocks east of him flying west, straight at him
        assertTrue(EnlilWind.incoming(3, 0, 0, -2, 0, 0));
        assertFalse(EnlilWind.incoming(3, 0, 0, 2, 0, 0));
        EnlilWind.Push bounce = EnlilWind.deflect(3, 0, 0, -2, 0, 0, 0.0);
        assertTrue(bounce.x() > 1.0, "sent back east");
        assertTrue(bounce.y() > 0.0);
        EnlilWind.Push scattered = EnlilWind.deflect(3, 0, 0, -2, 0, 0, 0.5);
        assertEquals(bounce.horizontal(), scattered.horizontal(), 1.0e-9);
        assertTrue(Math.abs(scattered.z()) > 0.1);
    }
}
