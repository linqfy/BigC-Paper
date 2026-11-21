package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enlil;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnlilFloodPlannerTest {

    /** A walled room with interior x/z 1..size, open everywhere inside except the given solid cells. */
    private record Room(int size, Set<EnlilFloodPlanner.Cell> solid, boolean holeInWall)
        implements EnlilFloodPlanner.OpenCells {

        @Override
        public boolean open(int x, int z) {
            if (holeInWall && x == 0 && z == 3) {
                return true;
            }
            if (x < 0 && holeInWall) {
                return true; // the world outside the hole
            }
            boolean inside = x >= 1 && x <= size && z >= 1 && z <= size;
            return inside && !solid.contains(new EnlilFloodPlanner.Cell(x, z));
        }
    }

    @Test
    void fillsEveryOpenCellOfAWatertightRoomFromTheWallsInward() {
        Set<EnlilFloodPlanner.Cell> solid = new HashSet<>();
        solid.add(new EnlilFloodPlanner.Cell(5, 5)); // the altar under the boss
        solid.add(new EnlilFloodPlanner.Cell(1, 4)); // a pillar
        EnlilArena arena = new EnlilArena(1, 9, 1, 9, 64);
        Optional<List<EnlilFloodPlanner.Cell>> plan = EnlilFloodPlanner.plan(arena, 5, 5, new Room(9, solid, false));
        assertTrue(plan.isPresent());
        List<EnlilFloodPlanner.Cell> cells = plan.get();
        assertEquals(81 - 2, cells.size());
        assertFalse(cells.contains(new EnlilFloodPlanner.Cell(5, 5)));
        assertEquals(new HashSet<>(cells).size(), cells.size(), "no cell twice");
        EnlilFloodPlanner.Cell first = cells.get(0);
        EnlilFloodPlanner.Cell last = cells.get(cells.size() - 1);
        assertTrue(Math.hypot(first.x() - 5, first.z() - 5) > Math.hypot(last.x() - 5, last.z() - 5));
    }

    @Test
    void aLeakingLayerIsNotFlooded() {
        EnlilArena arena = new EnlilArena(1, 9, 1, 9, 64);
        assertTrue(EnlilFloodPlanner.plan(arena, 5, 5, new Room(9, Set.of(), true)).isEmpty());
    }

    @Test
    void theOpenWorldIsNeverFlooded() {
        EnlilArena arena = EnlilArena.measured(0, 0, 64, -1, -1, -1, -1);
        assertTrue(EnlilFloodPlanner.plan(arena, 0, 0, (x, z) -> true).isEmpty());
    }

    @Test
    void noOpenStartMeansNoFlood() {
        EnlilArena arena = new EnlilArena(1, 9, 1, 9, 64);
        assertTrue(EnlilFloodPlanner.plan(arena, 5, 5, (x, z) -> false).isEmpty());
    }
}
