package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.modules.sift.SiftGeometry.Vec;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnifferInfectionTest {

    @Test
    void diggingOnSculkStartsTheInfectionAndItCompletesAfterSixHundredTicks() {
        SnifferInfection infection = new SnifferInfection(0);
        assertFalse(infection.infected());
        assertFalse(infection.tick(), "an uninfected sniffer does not count");
        infection.diggingStarted(true);
        assertTrue(infection.infected());
        assertEquals(1, infection.ticks());
        // The mod starts at 1 and increments before comparing with 600.
        for (int tick = 1; tick < SnifferInfection.CONVERSION_TICKS - 1; tick++) {
            assertFalse(infection.tick(), "tick " + tick);
        }
        assertTrue(infection.tick());
        assertEquals(SnifferInfection.CONVERSION_TICKS, infection.ticks());
    }

    @Test
    void diggingOnAnythingElseDoesNotInfect() {
        SnifferInfection infection = new SnifferInfection(0);
        infection.diggingStarted(false);
        assertFalse(infection.infected());
    }

    @Test
    void aRunningInfectionIsNotRestartedByAnotherDig() {
        SnifferInfection infection = new SnifferInfection(0);
        infection.diggingStarted(true);
        for (int tick = 0; tick < 100; tick++) {
            infection.tick();
        }
        int before = infection.ticks();
        infection.diggingStarted(true);
        assertEquals(before, infection.ticks());
    }

    @Test
    void savedProgressIsResumedAndNegativeValuesMeanHealthy() {
        assertEquals(250, new SnifferInfection(250).ticks());
        assertFalse(new SnifferInfection(-3).infected());
        SnifferInfection nearlyDone = new SnifferInfection(SnifferInfection.CONVERSION_TICKS - 1);
        assertTrue(nearlyDone.tick());
    }

    @Test
    void theDugBlockIsTwoAndAQuarterBlocksAheadOfTheSniffer() {
        // Bukkit yaw 0 faces south (+z), 90 faces west (-x), 180 north, -90 east.
        Vec origin = new Vec(10, 64, 20);
        Vec south = SnifferInfection.headPoint(origin, 0.0F);
        assertEquals(10.0, south.x(), 1.0E-9);
        assertEquals(22.25, south.z(), 1.0E-9);
        Vec west = SnifferInfection.headPoint(origin, 90.0F);
        assertEquals(7.75, west.x(), 1.0E-9);
        assertEquals(20.0, west.z(), 1.0E-9);
        Vec north = SnifferInfection.headPoint(origin, 180.0F);
        assertEquals(17.75, north.z(), 1.0E-9);
        Vec east = SnifferInfection.headPoint(origin, -90.0F);
        assertEquals(12.25, east.x(), 1.0E-9);
    }

    @Test
    void aFleeOffsetPointsAwayFromTheThreatWithinTheModsBox() {
        Random random = new Random(11);
        Vec sniffer = new Vec(0, 64, 0);
        Vec threat = new Vec(10, 64, 0);
        int usable = 0;
        for (int draw = 0; draw < 2000; draw++) {
            Optional<Vec> offset = SnifferInfection.awayOffset(random, sniffer, threat);
            if (offset.isEmpty()) {
                continue;
            }
            usable++;
            Vec at = offset.get();
            assertTrue(Math.abs(at.x()) <= SnifferInfection.FLEE_HORIZONTAL);
            assertTrue(Math.abs(at.z()) <= SnifferInfection.FLEE_HORIZONTAL);
            assertTrue(at.y() >= -SnifferInfection.FLEE_VERTICAL && at.y() <= SnifferInfection.FLEE_VERTICAL);
            // Within 90 degrees of straight away (-x): never towards the threat.
            assertTrue(at.x() <= 1.0E-9, "away from a threat at +x: " + at);
        }
        assertTrue(usable > 1000, "most draws are inside the box: " + usable);
    }

    @Test
    void fleeOffsetsFollowTheDirectionAwayFromTheThreatWhereverItIs() {
        Random random = new Random(5);
        Vec sniffer = new Vec(100, 64, 100);
        Vec threat = new Vec(100, 64, 90);
        double sumZ = 0.0;
        int count = 0;
        for (int draw = 0; draw < 2000; draw++) {
            Optional<Vec> offset = SnifferInfection.awayOffset(random, sniffer, threat);
            if (offset.isPresent()) {
                assertTrue(offset.get().z() >= -1.0E-9, "away from a threat at -z is towards +z");
                sumZ += offset.get().z();
                count++;
            }
        }
        assertTrue(sumZ / count > 4.0, "mean distance away is well above zero");
    }
}
