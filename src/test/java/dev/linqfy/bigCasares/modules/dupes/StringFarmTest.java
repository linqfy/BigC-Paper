package dev.linqfy.bigCasares.modules.dupes;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static dev.linqfy.bigCasares.modules.dupes.StringFarm.Cell.AIR;
import static dev.linqfy.bigCasares.modules.dupes.StringFarm.Cell.HOOK_EAST;
import static dev.linqfy.bigCasares.modules.dupes.StringFarm.Cell.HOOK_WEST;
import static dev.linqfy.bigCasares.modules.dupes.StringFarm.Cell.HOOK_SOUTH;
import static dev.linqfy.bigCasares.modules.dupes.StringFarm.Cell.HOOK_NORTH;
import static dev.linqfy.bigCasares.modules.dupes.StringFarm.Cell.WIRE;
import static dev.linqfy.bigCasares.modules.dupes.StringFarm.Cell.WATER;
import static dev.linqfy.bigCasares.modules.dupes.StringFarm.Cell.UNLOADED;
import static dev.linqfy.bigCasares.modules.dupes.StringFarm.Cell.OTHER;
import static dev.linqfy.bigCasares.modules.dupes.StringFarm.Cell.WATERLOGGED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class StringFarmTest {
    private final Map<StringFarm.Pos, StringFarm.Cell> cells = new HashMap<>();
    private final StringFarm.View view = p -> cells.getOrDefault(p, AIR);

    @Test void findsEastWestLineAndNorthSouthLine() {
        StringFarm.Line east = eastLine(0, 4);
        assertEquals(3, east.wires().size());
        assertEquals(east, StringFarm.find(view, pos(2, 0)));
        cells.clear();
        cells.put(pos(0, 0), HOOK_SOUTH);
        cells.put(pos(0, 1), WIRE);
        cells.put(pos(0, 2), HOOK_NORTH);
        assertNotNull(StringFarm.find(view, pos(0, 1)));
    }

    @Test void rejectsReversedHooksAndMissingWire() {
        eastLine(0, 4);
        cells.put(pos(0, 0), HOOK_WEST);
        assertNull(StringFarm.find(view, pos(2, 0)));
        cells.put(pos(0, 0), HOOK_EAST);
        cells.put(pos(1, 0), AIR);
        assertNull(StringFarm.find(view, pos(2, 0)));
    }

    @Test void enforcesVanillaMaximumDistance() {
        eastLine(0, 41);
        assertNotNull(StringFarm.find(view, pos(20, 0)));
        cells.clear();
        eastLine(0, 42);
        assertNull(StringFarm.find(view, pos(20, 0)));
    }

    @Test void neverTraversesUnloadedChunk() {
        eastLine(0, 4);
        cells.put(pos(1, 0), UNLOADED);
        assertNull(StringFarm.find(view, pos(2, 0)));
    }

    @Test void restoresOnlyWashedAwayWireWithIntactHooks() {
        StringFarm.Line line = eastLine(0, 2);
        StringFarm.Pos wet = pos(1, 0);
        cells.put(wet, WATER);
        assertEquals(Set.of(wet), StringFarm.restorable(view, Map.of(wet, line)));
        cells.put(pos(0, 0), AIR);
        assertTrue(StringFarm.restorable(view, Map.of(wet, line)).isEmpty());
    }

    @Test void neverOverwritesNewBlockOrAlreadyRestoredWire() {
        StringFarm.Line line = eastLine(0, 2);
        StringFarm.Pos wet = pos(1, 0);
        cells.put(wet, OTHER);
        assertTrue(StringFarm.restorable(view, Map.of(wet, line)).isEmpty());
        cells.put(wet, WATERLOGGED);
        assertTrue(StringFarm.restorable(view, Map.of(wet, line)).isEmpty());
        cells.put(wet, WIRE);
        assertTrue(StringFarm.restorable(view, Map.of(wet, line)).isEmpty());
    }

    @Test void restoresSeveralWiresWashedInSameTick() {
        StringFarm.Line line = eastLine(0, 4);
        cells.put(pos(1, 0), WATER);
        cells.put(pos(2, 0), WATER);
        Map<StringFarm.Pos, StringFarm.Line> pending = Map.of(pos(1, 0), line, pos(2, 0), line);
        assertEquals(pending.keySet(), StringFarm.restorable(view, pending));
    }

    @Test void doesNotBridgeMissingUnscheduledWire() {
        StringFarm.Line line = eastLine(0, 4);
        cells.put(pos(1, 0), WATER);
        cells.put(pos(2, 0), AIR);
        assertTrue(StringFarm.restorable(view, Map.of(pos(1, 0), line)).isEmpty());
    }

    @Test void removesDependentRestoresWhenOneCandidateWasReplaced() {
        StringFarm.Line line = eastLine(0, 4);
        cells.put(pos(1, 0), WATER);
        cells.put(pos(2, 0), OTHER);
        assertTrue(StringFarm.restorable(view, Map.of(pos(1, 0), line, pos(2, 0), line)).isEmpty());
    }

    private StringFarm.Line eastLine(int first, int last) {
        cells.put(pos(first, 0), HOOK_EAST);
        for (int x = first + 1; x < last; x++) cells.put(pos(x, 0), WIRE);
        cells.put(pos(last, 0), HOOK_WEST);
        return StringFarm.find(view, pos(first + 1, 0));
    }
    private static StringFarm.Pos pos(int x, int z) { return new StringFarm.Pos(x, 80, z); }
}
