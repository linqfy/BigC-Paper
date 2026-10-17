package dev.linqfy.bigCasares.modules.sift;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiftiteReturnFlightTest {

    private static double distance(SiftiteReturnFlight flight, double x, double y, double z) {
        return Math.sqrt(Math.pow(flight.x() - x, 2) + Math.pow(flight.y() - y, 2) + Math.pow(flight.z() - z, 2));
    }

    @Test
    void aFreeItemFliesToTheTargetWithinTheSpeedLimitAndArrives() {
        SiftiteReturnFlight flight = new SiftiteReturnFlight(0, 0.75, 1.55, 0);
        double previous = distance(flight, 6, 1, -4);
        int ticks = 0;
        while (!flight.arrived(6, 1, -4)) {
            flight.beginTick();
            flight.move(6, 1, -4, false);
            assertTrue(flight.speed() <= SiftiteReturnFlight.FLY_SPEED_LIMIT + 1.0e-9, "speed " + flight.speed());
            double now = distance(flight, 6, 1, -4);
            assertTrue(now < previous + 1.0e-9, "never moves away from a still target");
            previous = now;
            assertTrue(++ticks < 60, "should arrive in a few ticks");
        }
        assertTrue(ticks >= 4, "it takes a while to cross seven blocks: " + ticks);
        assertTrue(distance(flight, 6, 1, -4) <= 0.65 + 1.0e-9);
    }

    @Test
    void arrivalIsWithinSixtyFiveHundredthsOfABlock() {
        SiftiteReturnFlight flight = new SiftiteReturnFlight(0, 0, 0, 0);
        assertTrue(flight.arrived(0.65, 0, 0));
        assertFalse(flight.arrived(0.66, 0, 0));
        assertTrue(flight.arrived(0.4, 0.4, 0.3));
        assertEquals(0.4225, SiftiteReturnFlight.ARRIVAL_DISTANCE_SQUARED, 1.0e-12);
    }

    @Test
    void theFirstStepFollowsTheModsFormula() {
        SiftiteReturnFlight flight = new SiftiteReturnFlight(0, 0, 0, 0);
        flight.beginTick();
        flight.move(10, 0, 0, false);
        // velocity = 0.65 * 0 + 0.35 * (0.38 * 10) = 1.33, capped at 1.25
        assertEquals(1.25, flight.x(), 1.0e-12);
        SiftiteReturnFlight near = new SiftiteReturnFlight(0, 0, 0, 0);
        near.beginTick();
        near.move(2, 0, 0, false);
        assertEquals(0.35 * 0.38 * 2, near.x(), 1.0e-12);
        assertEquals(0.0, near.y(), 1.0e-12);
    }

    @Test
    void aWaitingItemCirclesTheTargetSlowlyAtTheOrbitRadius() {
        SiftiteReturnFlight flight = new SiftiteReturnFlight(0, 0, 0, 3);
        double minimum = Double.MAX_VALUE;
        double maximum = 0;
        for (int tick = 0; tick < 400; tick++) {
            flight.beginTick();
            flight.move(0, 1, 0, true);
            assertTrue(flight.speed() <= SiftiteReturnFlight.WAIT_SPEED_LIMIT + 1.0e-9, "speed " + flight.speed());
            if (tick > 150) {
                double horizontal = Math.hypot(flight.x(), flight.z());
                minimum = Math.min(minimum, horizontal);
                maximum = Math.max(maximum, horizontal);
            }
        }
        assertTrue(minimum > 1.0 && maximum < 2.3, "stays around the orbit radius: " + minimum + ".." + maximum);
        assertFalse(flight.arrived(0, 1, 0), "an orbiting item never reaches the target");
    }

    @Test
    void trailsAreLeftOnEvenAges() {
        SiftiteReturnFlight flight = new SiftiteReturnFlight(0, 0, 0, 0);
        flight.beginTick();
        assertFalse(flight.leavesTrail());
        flight.beginTick();
        assertTrue(flight.leavesTrail());
        assertEquals(2, flight.age());
    }

    @Test
    void theOrbitPhaseDependsOnTheFlight() {
        SiftiteReturnFlight one = new SiftiteReturnFlight(0, 0, 0, 1);
        SiftiteReturnFlight other = new SiftiteReturnFlight(0, 0, 0, 2);
        for (int tick = 0; tick < 40; tick++) {
            one.beginTick();
            one.move(0, 0, 0, true);
            other.beginTick();
            other.move(0, 0, 0, true);
        }
        assertTrue(Math.abs(one.x() - other.x()) + Math.abs(one.z() - other.z()) > 0.05);
    }
}
