package dev.linqfy.bigCasares.modules.mythicdungeons.boss.iblis;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IblisSmokeAndTelegraphsTest {

    @Test
    void fireballsAndLinesAlternateAndEmbersOnlyPunishHuggers() {
        IblisSmokeCycle cycle = new IblisSmokeCycle(0);
        assertNull(cycle.advance(IblisSmokeCycle.FIRST_DELAY - 1, 1.0, false));
        int t = IblisSmokeCycle.FIRST_DELAY;
        assertEquals(IblisSmokeCycle.Attack.FIREBALLS, cycle.advance(t, 1.0, false));
        assertTrue(cycle.casting(t + 1));
        assertFalse(cycle.casting(t + IblisSmokeCycle.CAST_TICKS));
        assertNull(cycle.advance(t + IblisSmokeCycle.PERIOD - 1, 1.0, false));
        t += IblisSmokeCycle.PERIOD;
        assertEquals(IblisSmokeCycle.Attack.FLAME_LINE, cycle.advance(t, 1.0, false));
        t += IblisSmokeCycle.PERIOD;
        assertEquals(IblisSmokeCycle.Attack.FIREBALLS, cycle.advance(t, 1.0, false), "nobody close: no embers");
        t += IblisSmokeCycle.PERIOD;
        assertEquals(IblisSmokeCycle.Attack.FLAME_LINE, cycle.advance(t, 0.6, true));
        // below 75% it speeds up
        assertNull(cycle.advance(t + IblisSmokeCycle.FAST_PERIOD - 1, 0.6, true));
        t += IblisSmokeCycle.FAST_PERIOD;
        assertEquals(IblisSmokeCycle.Attack.FIREBALLS, cycle.advance(t, 0.6, true));
        t += IblisSmokeCycle.FAST_PERIOD;
        assertEquals(IblisSmokeCycle.Attack.EMBER_RING, cycle.advance(t, 0.6, true));
    }

    @Test
    void oneFireballPerPlayerUpToThree() {
        assertEquals(0, IblisSmokeCycle.fireballs(0));
        assertEquals(1, IblisSmokeCycle.fireballs(1));
        assertEquals(3, IblisSmokeCycle.fireballs(3));
        assertEquals(3, IblisSmokeCycle.fireballs(8));
    }

    @Test
    void nothingLandsBeforeItsWarning() {
        IblisTelegraphs<String> telegraphs = new IblisTelegraphs<>();
        telegraphs.add("short", 10, 2);
        telegraphs.add("long", 10, 30);
        assertEquals(2, telegraphs.pending().size());
        assertEquals(0.5, telegraphs.pending().get(1).progress(25), 1.0e-9);
        assertTrue(telegraphs.land(10 + IblisTelegraphs.MINIMUM_WARNING - 1).isEmpty(), "a warning is at least 0.8 s");
        assertEquals(List.of("short"), telegraphs.land(10 + IblisTelegraphs.MINIMUM_WARNING));
        assertEquals(List.of("long"), telegraphs.land(40));
        assertTrue(telegraphs.isEmpty());
        telegraphs.add("x", 0, 20);
        telegraphs.clear();
        assertTrue(telegraphs.land(100).isEmpty());
    }

    @Test
    void segmentDistance() {
        IblisPoint a = new IblisPoint(0, 0);
        IblisPoint b = new IblisPoint(10, 0);
        assertEquals(1.0, new IblisPoint(5, 1).distanceToSegment(a, b), 1.0e-9);
        assertEquals(5.0, new IblisPoint(13, 4).distanceToSegment(a, b), 1.0e-9);
        assertEquals(2.0, new IblisPoint(0, 2).distanceToSegment(a, a), 1.0e-9);
    }
}
