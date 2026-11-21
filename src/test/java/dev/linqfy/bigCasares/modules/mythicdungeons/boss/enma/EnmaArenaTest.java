package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enma;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnmaArenaTest {

    private final EnmaArena arena = EnmaArena.around(100.5, 64.0, -20.5);

    @Test
    void playersInsideTheRoomCount() {
        assertTrue(arena.contains(100.5, 64.0, -20.5));
        assertTrue(arena.contains(100.5 + 16.0, 64.0, -20.5));
        assertTrue(arena.contains(100.5, 64.0 + 10.0, -20.5));
        assertTrue(arena.contains(100.5, 64.0 - 3.0, -20.5));
    }

    @Test
    void aCorridorBehindTheWallOrAnotherFloorDoesNot() {
        assertFalse(arena.contains(100.5 + 20.0, 64.0, -20.5));
        assertFalse(arena.contains(100.5 + 13.0, 64.0, -20.5 + 13.0));
        assertFalse(arena.contains(100.5, 64.0 + 13.0, -20.5));
        assertFalse(arena.contains(100.5, 64.0 - 5.0, -20.5));
        assertTrue(arena.containsHorizontally(100.5, -20.5 + 16.9));
    }

    @Test
    void rejectsNonsenseSizes() {
        assertThrows(IllegalArgumentException.class, () -> new EnmaArena(0, 0, 0, 0.0, 1.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new EnmaArena(0, 0, 0, 5.0, -1.0, 1.0));
    }
}
