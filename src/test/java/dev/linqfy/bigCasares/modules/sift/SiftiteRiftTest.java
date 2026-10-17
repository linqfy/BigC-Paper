package dev.linqfy.bigCasares.modules.sift;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiftiteRiftTest {

    @Test
    void releasesOneItemEveryFiveTicksStartingAtTickFifteen() {
        SiftiteRift rift = new SiftiteRift(4);
        List<Integer> releases = new ArrayList<>();
        for (int tick = 1; tick <= 60 && !rift.isFinished(); tick++) {
            if (rift.tick()) {
                releases.add(tick);
            }
        }
        assertEquals(List.of(15, 20, 25, 30), releases);
        assertEquals(0, rift.remaining());
    }

    @Test
    void closesForTwelveTicksAfterTheLastItemAndThenFinishes() {
        SiftiteRift rift = new SiftiteRift(2);
        int tick = 0;
        while (!rift.isClosing()) {
            rift.tick();
            tick++;
            assertTrue(tick < 100);
        }
        assertEquals(20, tick, "closing starts on the tick of the last release");
        assertEquals(1.0F, rift.openScale());
        int closing = 0;
        while (!rift.isFinished()) {
            assertFalse(rift.tick(), "nothing is released while closing");
            closing++;
        }
        assertEquals(13, closing, "discarded when the closing counter passes twelve");
    }

    @Test
    void anEmptyRiftOpensFullyThenCloses() {
        SiftiteRift rift = new SiftiteRift(0);
        for (int tick = 1; tick <= 11; tick++) {
            assertFalse(rift.tick());
            assertFalse(rift.isClosing());
        }
        assertFalse(rift.tick());
        assertTrue(rift.isClosing());
    }

    @Test
    void opensAndClosesWithASmoothScale() {
        SiftiteRift rift = new SiftiteRift(1);
        assertEquals(0.0F, rift.size());
        rift.tick();
        assertEquals(1 / 12.0F, rift.openScale(), 1.0e-6F);
        for (int tick = 2; tick <= 6; tick++) {
            rift.tick();
        }
        assertEquals(0.5F, rift.openScale(), 1.0e-6F);
        assertEquals(0.5F, rift.size(), 1.0e-6F, "smoothstep(0.5) = 0.5");
        while (!rift.isClosing()) {
            rift.tick();
        }
        assertEquals(1.0F, rift.size());
        for (int tick = 0; tick < 6; tick++) {
            rift.tick();
        }
        assertEquals(0.5F, rift.openScale(), 1.0e-6F);
        while (!rift.isFinished()) {
            rift.tick();
        }
        assertEquals(0.0F, rift.size());
    }

    @Test
    void candidatesCircleThePlayerAtOnePointFiveFiveBlocks() {
        assertEquals(8, SiftiteRift.CANDIDATES);
        for (int index = 0; index < SiftiteRift.CANDIDATES; index++) {
            double[] offset = SiftiteRift.candidate(index);
            assertEquals(1.55, Math.hypot(offset[0], offset[2]), 1.0e-9);
            assertEquals(0.75, offset[1]);
        }
        assertEquals(1.55, SiftiteRift.candidate(0)[0], 1.0e-9);
        assertEquals(0.0, SiftiteRift.candidate(0)[2], 1.0e-9);
        assertEquals(1.55, SiftiteRift.candidate(2)[2], 1.0e-9);
        assertEquals(-1.55, SiftiteRift.candidate(4)[0], 1.0e-9);
        assertEquals(-1.55, SiftiteRift.candidate(6)[2], 1.0e-9);
        double[] fallback = SiftiteRift.fallback();
        assertEquals(1.55, fallback[0]);
        assertEquals(0.75, fallback[1]);
        assertEquals(0.0, fallback[2]);
    }
}
