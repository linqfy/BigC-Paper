package dev.linqfy.bigCasares.modules.mythicdungeons.boss.odin;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OdinChargeTest {

    /** Un piso plano en y=63 (pies en y=64) con bloques extra que se agregan. */
    private static final class Floor implements OdinCharge.Terrain {
        private final Set<String> blocks = new HashSet<>();
        private final int floorY;
        private final Set<String> holes = new HashSet<>();

        private Floor(int floorY) {
            this.floorY = floorY;
        }

        Floor add(int x, int y, int z) {
            blocks.add(x + ":" + y + ":" + z);
            return this;
        }

        Floor hole(int x, int z) {
            holes.add(x + ":" + z);
            return this;
        }

        Floor wallAtX(int x, int height) {
            for (int z = -30; z <= 30; z++) {
                for (int y = floorY + 1; y <= floorY + height; y++) {
                    add(x, y, z);
                }
            }
            return this;
        }

        @Override
        public boolean solid(int x, int y, int z) {
            if (y <= floorY && y >= floorY - 10 && holes.contains(x + ":" + z)) {
                return false;
            }
            return y == floorY || blocks.contains(x + ":" + y + ":" + z);
        }
    }

    @Test
    void movesForwardOnFlatGround() {
        OdinCharge.Step step = OdinCharge.step(new Floor(63), 0.5, 64, 0.5, 1, 0, 0.85, 0, 0, 18);
        assertEquals(OdinCharge.Outcome.MOVE, step.outcome());
        assertEquals(1.35, step.x(), 1e-9);
        assertEquals(64.0, step.y(), 1e-9);
        assertEquals(0.5, step.z(), 1e-9);
    }

    @Test
    void aTallWallStopsTheChargeAsAWall() {
        Floor floor = new Floor(63).wallAtX(3, 4);
        OdinCharge.Step step = OdinCharge.step(floor, 1.5, 64, 0.5, 1, 0, 0.85, 0, 0, 18);
        assertEquals(OdinCharge.Outcome.WALL, step.outcome());
        assertEquals(1.5, step.x(), 1e-9);
    }

    @Test
    void aSingleStepIsClimbedButTwoAreAWall() {
        Floor step = new Floor(63).wallAtX(3, 1);
        OdinCharge.Step up = OdinCharge.step(step, 1.5, 64, 0.5, 1, 0, 0.85, 0, 0, 18);
        assertEquals(OdinCharge.Outcome.MOVE, up.outcome());
        assertEquals(65.0, up.y(), 1e-9);

        Floor two = new Floor(63).wallAtX(3, 2);
        assertEquals(OdinCharge.Outcome.WALL, OdinCharge.step(two, 1.5, 64, 0.5, 1, 0, 0.85, 0, 0, 18).outcome());
    }

    @Test
    void aLowCeilingCountsAsAWall() {
        Floor floor = new Floor(63).add(3, 66, 0);
        assertEquals(OdinCharge.Outcome.WALL, OdinCharge.step(floor, 1.5, 64, 0.5, 1, 0, 0.85, 0, 0, 18).outcome());
    }

    @Test
    void aDeepHoleOrTheArenaRadiusEndsWithoutStun() {
        Floor pit = new Floor(63).hole(2, 0).hole(3, 0);
        assertEquals(OdinCharge.Outcome.EDGE, OdinCharge.step(pit, 1.2, 64, 0.5, 1, 0, 0.85, 0, 0, 18).outcome());

        assertEquals(OdinCharge.Outcome.EDGE,
            OdinCharge.step(new Floor(63), 17.5, 64, 0.5, 1, 0, 0.85, 0, 0, 18).outcome());
    }

    @Test
    void followsTheFloorDownShortDrops() {
        Floor floor = new Floor(63);
        // escalon de bajada: desde la x=2 el piso esta un bloque mas abajo
        OdinCharge.Terrain lower = (x, y, z) -> x < 2 ? y == 63 || y == 62 : y == 62;
        OdinCharge.Step down = OdinCharge.step(lower, 1.2, 64, 0.5, 1, 0, 0.85, 0, 0, 18);
        assertEquals(OdinCharge.Outcome.MOVE, down.outcome());
        assertEquals(63.0, down.y(), 1e-9);
        assertEquals(OdinCharge.Outcome.MOVE, OdinCharge.step(floor, 1.2, 64, 0.5, 1, 0, 0.85, 0, 0, 18).outcome());
    }

    @Test
    void reachStopsAtTheWallAndNeverExceedsTheMaximum() {
        Floor walled = new Floor(63).wallAtX(10, 4);
        double reach = OdinCharge.reach(walled, 0.5, 64, 0.5, 1, 0, 0, 0, 40);
        assertTrue(reach > 6.0 && reach < 9.5, "reach " + reach);
        double open = OdinCharge.reach(new Floor(63), 0.5, 64, 0.5, 1, 0, 0, 0, 100);
        assertTrue(open <= OdinCharge.MAX_DISTANCE && open > OdinCharge.MAX_DISTANCE - 1.0, "open " + open);
    }

    @Test
    void hitsPlayersUnderTheBodyOnly() {
        assertTrue(OdinCharge.hits(0, 64, 0, 1.5, 64, 1.0));
        assertFalse(OdinCharge.hits(0, 64, 0, 2.5, 64, 0.0));
        assertFalse(OdinCharge.hits(0, 64, 0, 0.5, 68, 0.0));
        assertTrue(OdinCharge.hits(0, 64, 0, 0.5, 66, 0.0));
    }

    @Test
    void theRamTakesHalfOfTheMaximumHealth() {
        OdinCharge.Impact full = OdinCharge.Impact.against(20.0, 20.0);
        assertFalse(full.lethal());
        assertEquals(10.0, full.remainingHealth(), 1e-9);

        OdinCharge.Impact hurt = OdinCharge.Impact.against(14.0, 20.0);
        assertEquals(4.0, hurt.remainingHealth(), 1e-9);

        assertTrue(OdinCharge.Impact.against(10.0, 20.0).lethal());
        assertTrue(OdinCharge.Impact.against(3.0, 20.0).lethal());
        // con mas vida maxima (por ejemplo un buff), sigue siendo la mitad
        assertEquals(20.0, OdinCharge.Impact.against(40.0, 40.0).remainingHealth(), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> OdinCharge.Impact.against(10.0, 0.0));
    }

    @Test
    void pickTargetAvoidsRepeatingTheLastOneWhenPossible() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        for (int roll = 0; roll < 5; roll++) {
            int fixed = roll;
            assertNotEquals(a, OdinCharge.pickTarget(List.of(a, b, c), a, bound -> fixed));
        }
        assertEquals(a, OdinCharge.pickTarget(List.of(a), a, bound -> 0));
        assertEquals(b, OdinCharge.pickTarget(List.of(a, b), null, bound -> 1));
        assertThrows(IllegalArgumentException.class, () -> OdinCharge.pickTarget(List.of(), null, bound -> 0));
    }
}
