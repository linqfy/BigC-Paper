package dev.linqfy.bigCasares.modules.mythicdungeons.boss.horus;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HorusGlidePathTest {

    private static final HorusPoint CENTER = new HorusPoint(0.5, 64.0, 0.5);
    private static final double RADIUS = 13.0;

    @Test
    void divesToTheTargetThenSkimsPastItInsideTheArena() {
        HorusPoint start = new HorusPoint(10.0, 71.0, 0.5);
        HorusGlidePath path = HorusGlidePath.plan(start, new HorusPoint(0.5, 64.0, 0.5), CENTER, RADIUS, 64.0);

        assertEquals(64.0 + HorusGlidePath.LOW_HEIGHT, path.low().y(), 1.0E-9);
        assertEquals(0.5, path.low().x(), 1.0E-9);
        assertEquals(0.5 - HorusGlidePath.OVERSHOOT, path.end().x(), 1.0E-9);
        assertEquals(64.0 + HorusGlidePath.END_HEIGHT, path.end().y(), 1.0E-9);
        assertEquals(start, path.positionAt(0));
        assertEquals(path.low(), path.positionAt(path.diveTicks()));
        assertEquals(path.end(), path.positionAt(path.totalTicks()));
        assertEquals(path.end(), path.positionAt(path.totalTicks() + 50));
        for (int tick = 0; tick <= path.totalTicks(); tick++) {
            HorusPoint at = path.positionAt(tick);
            assertTrue(at.horizontalDistance(CENTER) <= RADIUS + 1.0E-9);
            assertTrue(at.y() >= 64.0);
        }
    }

    @Test
    void movesNoFasterThanTheDiveSpeedAndSlowsDownSkimming() {
        HorusGlidePath path = HorusGlidePath.plan(new HorusPoint(-9.0, 71.0, -6.0), new HorusPoint(5.0, 64.0, 4.0),
            CENTER, RADIUS, 64.0);
        for (int tick = 1; tick <= path.totalTicks(); tick++) {
            double step = path.positionAt(tick).distance(path.positionAt(tick - 1));
            assertTrue(step <= HorusGlidePath.DIVE_SPEED + 1.0E-9, "step " + step + " at " + tick);
        }
        int skim = path.diveTicks();
        double skimStep = path.positionAt(skim + 2).horizontalDistance(path.positionAt(skim + 1));
        assertTrue(skimStep <= HorusGlidePath.SKIM_SPEED + 1.0E-9);
    }

    @Test
    void targetsOutsideTheArenaAreClampedToItsEdge() {
        HorusGlidePath path = HorusGlidePath.plan(new HorusPoint(0.5, 71.0, 0.5), new HorusPoint(40.5, 64.0, 0.5),
            CENTER, RADIUS, 64.0);
        assertEquals(RADIUS, path.low().horizontalDistance(CENTER), 1.0E-9);
        assertTrue(path.end().horizontalDistance(CENTER) <= RADIUS + 1.0E-9);
    }

    @Test
    void aTargetRightBelowStillGetsADirection() {
        HorusGlidePath path = HorusGlidePath.plan(new HorusPoint(5.5, 71.0, 0.5), new HorusPoint(5.5, 64.0, 0.5),
            CENTER, RADIUS, 64.0);
        assertTrue(path.end().horizontalDistance(path.low()) > 1.0);
    }

    @Test
    void groundMarksFollowTheWholePathOnTheFloor() {
        HorusGlidePath path = HorusGlidePath.plan(new HorusPoint(10.0, 71.0, 0.5), new HorusPoint(0.5, 64.0, 0.5),
            CENTER, RADIUS, 64.0);
        List<HorusPoint> marks = path.groundMarks(64.1, 1.0);
        assertTrue(marks.size() >= 15);
        marks.forEach(mark -> assertEquals(64.1, mark.y(), 1.0E-9));
        assertEquals(path.end().x(), marks.getLast().x(), 1.0E-9);
        assertThrows(IllegalArgumentException.class, () -> path.groundMarks(64.0, 0.0));
    }

    @Test
    void theBodyHitsPlayersItSweepsButNotOnesBelowOrAside() {
        HorusPoint boss = new HorusPoint(0.0, 64.4, 0.0);
        assertTrue(HorusGlidePath.bodyHits(boss, new HorusPoint(1.0, 64.0, 0.5)));
        assertFalse(HorusGlidePath.bodyHits(boss, new HorusPoint(2.5, 64.0, 0.0)));
        assertFalse(HorusGlidePath.bodyHits(new HorusPoint(0.0, 71.0, 0.0), new HorusPoint(0.0, 64.0, 0.0)));
    }

    @Test
    void yawFollowsMinecraftConventions() {
        HorusPoint origin = new HorusPoint(0.0, 0.0, 0.0);
        assertEquals(0.0F, origin.yawTowards(new HorusPoint(0.0, 0.0, 5.0), 99.0F), 1.0E-4F);
        assertEquals(-90.0F, origin.yawTowards(new HorusPoint(5.0, 0.0, 0.0), 99.0F), 1.0E-4F);
        assertEquals(99.0F, origin.yawTowards(origin, 99.0F));
    }
}
