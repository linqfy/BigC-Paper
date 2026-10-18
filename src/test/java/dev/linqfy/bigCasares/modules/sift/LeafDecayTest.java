package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.modules.sift.LeafDecay.Kind;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeafDecayTest {

    /** A world on a map: unlisted cells are air. */
    private static final class World implements LeafDecay.Grid {
        final Map<SiftPos, Kind> cells = new HashMap<>();
        int reads;

        World set(int x, int y, int z, Kind kind) {
            cells.put(new SiftPos(x, y, z), kind);
            return this;
        }

        @Override
        public Kind kindAt(int x, int y, int z) {
            reads++;
            return cells.getOrDefault(new SiftPos(x, y, z), Kind.OTHER);
        }
    }

    /** A row of leaves along x from 1 to {@code length}, a log at 0. */
    private static World row(int length) {
        World world = new World().set(0, 0, 0, Kind.LOG);
        for (int x = 1; x <= length; x++) {
            world.set(x, 0, 0, Kind.LEAVES);
        }
        return world;
    }

    @Test
    void aLeafTouchingALogHasDistanceOne() {
        assertEquals(1, LeafDecay.distance(row(1), 1, 0, 0));
    }

    @Test
    void theDistanceCountsStepsThroughLeaves() {
        World world = row(6);
        for (int x = 1; x <= 6; x++) {
            assertEquals(x, LeafDecay.distance(world, x, 0, 0), "leaf " + x);
        }
    }

    @Test
    void sixStepsKeepALeafAndSevenDecayIt() {
        assertFalse(LeafDecay.decays(row(6), 6, 0, 0), "vanilla: distance 6 survives");
        assertTrue(LeafDecay.decays(row(7), 7, 0, 0), "vanilla: distance 7 decays");
        assertEquals(LeafDecay.DECAY_DISTANCE, LeafDecay.distance(row(7), 7, 0, 0));
    }

    @Test
    void aLeafWithoutAnyLogDecays() {
        World world = new World().set(0, 0, 0, Kind.LEAVES).set(1, 0, 0, Kind.LEAVES);
        assertTrue(LeafDecay.decays(world, 0, 0, 0));
    }

    @Test
    void theNearestLogWinsWhateverTheDirection() {
        // A log at the end of a six-leaf row, and another right above the last leaf.
        World world = row(6).set(6, 1, 0, Kind.LOG);
        assertEquals(1, LeafDecay.distance(world, 6, 0, 0));
        assertEquals(2, LeafDecay.distance(world, 5, 0, 0), "one step along the row, then up");
    }

    @Test
    void otherBlocksBreakThePath() {
        World world = row(3).set(2, 0, 0, Kind.OTHER);
        assertEquals(LeafDecay.DECAY_DISTANCE, LeafDecay.distance(world, 3, 0, 0), "a gap cuts the leaf off the log");
    }

    @Test
    void logsDoNotCarryTheDistanceFurther() {
        // log - leaf - log - leaf: the last leaf is next to a log, the middle one is not what counts.
        World world = new World().set(0, 0, 0, Kind.LOG).set(1, 0, 0, Kind.LEAVES).set(2, 0, 0, Kind.LOG);
        assertEquals(1, LeafDecay.distance(world, 1, 0, 0));
    }

    @Test
    void aCrownAroundATrunkKeepsEveryLeafWithinReach() {
        World world = new World();
        for (int y = 0; y < 8; y++) {
            world.set(0, y, 0, Kind.LOG);
        }
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                for (int y = 4; y <= 7; y++) {
                    if (x != 0 || z != 0) {
                        world.set(x, y, z, Kind.LEAVES);
                    }
                }
            }
        }
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                if (x != 0 || z != 0) {
                    assertFalse(LeafDecay.decays(world, x, 5, z), x + "," + z);
                }
            }
        }
        // Cut the trunk: every leaf now decays.
        for (int y = 0; y < 8; y++) {
            world.set(0, y, 0, Kind.OTHER);
        }
        assertTrue(LeafDecay.decays(world, 3, 5, 3));
        assertTrue(LeafDecay.decays(world, 1, 5, 0));
    }

    @Test
    void theSearchReadsAtMostTheCellsWithinSixSteps() {
        World world = new World();
        for (int x = -8; x <= 8; x++) {
            for (int y = -8; y <= 8; y++) {
                for (int z = -8; z <= 8; z++) {
                    world.set(x, y, z, Kind.LEAVES);
                }
            }
        }
        assertTrue(LeafDecay.decays(world, 0, 0, 0));
        assertTrue(world.reads <= 377 + 6 * 7, "bounded work: " + world.reads);
    }

    @Test
    void aSolidLogNextToTheLeafReturnsQuickly() {
        World world = row(1);
        LeafDecay.distance(world, 1, 0, 0);
        assertTrue(world.reads <= 6, "stops at the first log: " + world.reads);
    }
}
