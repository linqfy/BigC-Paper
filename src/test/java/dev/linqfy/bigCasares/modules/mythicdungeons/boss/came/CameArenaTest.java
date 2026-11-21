package dev.linqfy.bigCasares.modules.mythicdungeons.boss.came;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameArenaTest {

    private final CameArena arena = CameArena.around(100.5, 64.0, -20.5);

    @Test
    void onlyPlayersInsideTheCourtAreTargets() {
        assertTrue(arena.contains(100.5, 64.0, -20.5));
        assertTrue(arena.contains(100.5 + 11.0, 65.0, -20.5 + 11.0));
        assertFalse(arena.contains(100.5 + 25.0, 64.0, -20.5), "corridor behind a wall");
        assertFalse(arena.contains(100.5, 64.0 - 6.0, -20.5), "floor below");
        assertFalse(arena.contains(100.5, 64.0 + 12.0, -20.5), "floor above");
    }

    @Test
    void lordsAreKeptAwayFromTheWalls() {
        CameVec inside = new CameVec(103.0, 64.0, -20.5);
        assertSame(inside, arena.clamp(inside, CameArena.MOVE_RADIUS));
        CameVec clamped = arena.clamp(new CameVec(100.5 + 30.0, 70.0, -20.5), CameArena.MOVE_RADIUS);
        assertEquals(100.5 + CameArena.MOVE_RADIUS, clamped.x(), 1.0e-9);
        assertEquals(70.0, clamped.y());
        assertEquals(-20.5, clamped.z(), 1.0e-9);
    }

    @Test
    void rejectsNonsenseSizes() {
        assertThrows(IllegalArgumentException.class, () -> new CameArena(0, 0, 0, 0.0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new CameArena(0, 0, 0, 5.0, -1, 1));
    }
}
