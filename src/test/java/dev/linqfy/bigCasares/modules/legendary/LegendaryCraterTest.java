package dev.linqfy.bigCasares.modules.legendary;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegendaryCraterTest {
    @Test void depthDropsOneBlockEveryTwoHorizontalBlocks() {
        assertEquals(4, LegendaryCrater.depth(0, 0));
        assertEquals(4, LegendaryCrater.depth(1, 1));
        assertEquals(3, LegendaryCrater.depth(2, 0));
        assertEquals(2, LegendaryCrater.depth(4, 0));
        assertEquals(1, LegendaryCrater.depth(6, 0));
        assertEquals(1, LegendaryCrater.depth(8, 0));
        assertEquals(0, LegendaryCrater.depth(8, 1));
        assertEquals(0, LegendaryCrater.depth(9, 0));
    }

    @Test void circleIsSymmetricAndNeverCutsBelowFourBlocksOrOutsideRadiusEight() {
        var cells = LegendaryCrater.cells();
        var unique = new HashSet<>(cells);
        assertEquals(cells.size(), unique.size());
        assertEquals(197, cells.stream().filter(cell -> cell.y() == 0).count());
        for (var cell : cells) {
            assertTrue(cell.x() * cell.x() + cell.z() * cell.z() <= 64);
            assertTrue(cell.y() <= 0 && cell.y() >= -3);
            assertTrue(unique.contains(new LegendaryCrater.Cell(-cell.x(), cell.y(), cell.z())));
            assertTrue(unique.contains(new LegendaryCrater.Cell(cell.z(), cell.y(), cell.x())));
        }
        for (int y = 0; y >= -3; y--) assertTrue(unique.contains(new LegendaryCrater.Cell(0, y, 0)));
        assertFalse(unique.contains(new LegendaryCrater.Cell(0, -4, 0)));
    }

    @Test void eachTickHasABoundedBudgetEvenWhenEveryBlockIsProtected() {
        var crater = new LegendaryCrater();
        var visited = new ArrayList<LegendaryCrater.Cell>();
        crater.step(32, cell -> { visited.add(cell); return false; });
        assertEquals(32, visited.size());
        assertFalse(crater.complete());
        while (!crater.complete()) crater.step(32, cell -> { visited.add(cell); return false; });
        assertEquals(LegendaryCrater.cells().size(), visited.size());
        assertEquals(visited.size(), new HashSet<>(visited).size());
    }

    @Test void carvingNeverRevisitsAcceptedBlocksAndStopsAtTheEnd() {
        var crater = new LegendaryCrater();
        var removed = new HashSet<LegendaryCrater.Cell>();
        while (!crater.complete()) crater.step(64, cell -> { assertTrue(removed.add(cell)); return true; });
        assertEquals(new HashSet<>(LegendaryCrater.cells()), removed);
        assertEquals(0, crater.step(64, cell -> { throw new AssertionError("Already completed"); }));
        assertThrows(IllegalArgumentException.class, () -> crater.step(0, cell -> true));
    }
}
