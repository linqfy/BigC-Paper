package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enlil;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnlilArenaTest {

    @Test
    void measuredFromTheAltarToTheWalls() {
        // the generated arena: altar at x 17, z 20 of a 33x33 interior (x 1..33, z 1..33)
        EnlilArena arena = EnlilArena.measured(117, 220, 64, 16, 16, 19, 13);
        assertEquals(101, arena.minX());
        assertEquals(133, arena.maxX());
        assertEquals(201, arena.minZ());
        assertEquals(233, arena.maxZ());
        assertEquals(117.5, arena.centerX(), 1.0e-9);
        assertEquals(217.5, arena.centerZ(), 1.0e-9);
    }

    @Test
    void openSidesAndFarWallsAreCapped() {
        EnlilArena arena = EnlilArena.measured(0, 0, 10, -1, 50, 3, -1);
        assertEquals(-EnlilArena.OPEN_REACH, arena.minX());
        assertEquals(EnlilArena.MAX_REACH, arena.maxX());
        assertEquals(-3, arena.minZ());
        assertEquals(EnlilArena.OPEN_REACH, arena.maxZ());
    }

    @Test
    void clampKeepsAMarginFromTheWalls() {
        EnlilArena arena = new EnlilArena(0, 9, 0, 9, 0);
        assertEquals(2.0, arena.clampX(-5, 2.0), 1.0e-9);
        assertEquals(8.0, arena.clampX(50, 2.0), 1.0e-9);
        assertEquals(5.0, arena.clampZ(5, 2.0), 1.0e-9);
        assertEquals(5.0, arena.clampZ(0, 9.0), 1.0e-9, "a margin larger than the room gives the centre");
        assertTrue(arena.contains(9.9, 0.0));
        assertFalse(arena.contains(10.0, 0.0));
    }

    @Test
    void ringPointsAreSpreadAndInside() {
        EnlilArena arena = new EnlilArena(-16, 16, -16, 16, 0);
        List<double[]> ring = arena.ring(3, 7.0, 0.0);
        assertEquals(3, ring.size());
        for (double[] point : ring) {
            assertEquals(7.0, Math.hypot(point[0] - arena.centerX(), point[1] - arena.centerZ()), 1.0e-9);
        }
        EnlilArena small = new EnlilArena(0, 3, 0, 3, 0);
        for (double[] point : small.ring(3, 7.0, 0.0)) {
            assertTrue(small.contains(point[0], point[1]));
        }
        assertThrows(IllegalArgumentException.class, () -> new EnlilArena(5, 4, 0, 1, 0));
    }
}
