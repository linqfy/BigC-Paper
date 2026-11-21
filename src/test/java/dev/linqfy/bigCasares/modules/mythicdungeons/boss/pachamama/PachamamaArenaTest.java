package dev.linqfy.bigCasares.modules.mythicdungeons.boss.pachamama;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PachamamaArenaTest {

    /** A 33x33 room (interior -16..16) with a floor at y = 63 and an altar block at the origin. */
    private static boolean room(int x, int y, int z) {
        if (y <= 63) {
            return false;
        }
        if (Math.abs(x) > 16 || Math.abs(z) > 16) {
            return false;
        }
        return !(x == 0 && z == 0 && y == 64);
    }

    @Test
    void measuresTheRoomFromTheAltar() {
        int floor = PachamamaArena.floorUnder(0, 65, 0, PachamamaArenaTest::room);
        assertEquals(64, floor);
        PachamamaArena arena = PachamamaArena.probe(0, 0, floor, PachamamaArenaTest::room);
        assertEquals(new PachamamaArena(-16, 16, -16, 16, 64), arena);
        assertTrue(arena.containsFeet(5.5, 64.0, -3.2));
        assertFalse(arena.containsFeet(17.5, 64.0, 0.0));
        assertFalse(arena.containsFeet(0.0, 90.0, 0.0));
    }

    @Test
    void anOpenSideFallsBackToADefaultReach() {
        PachamamaArena arena = PachamamaArena.probe(0, 0, 64, (x, y, z) -> y > 63);
        assertEquals(-PachamamaArena.OPEN_REACH, arena.minX());
        assertEquals(PachamamaArena.OPEN_REACH, arena.maxZ());
    }

    @Test
    void theChessboardCoversEveryColumnOnce() {
        PachamamaArena arena = new PachamamaArena(-16, 16, -16, 16, 64);
        List<PachamamaTile> tiles = arena.tiles();
        assertEquals(11 * 11, tiles.size());
        Set<Long> seen = new HashSet<>();
        for (PachamamaTile tile : tiles) {
            for (int[] column : arena.columns(tile)) {
                assertTrue(seen.add(((long) column[0] << 32) | (column[1] & 0xffffffffL)), "column twice");
                assertEquals(tile, arena.tileAt(column[0], column[1]));
            }
        }
        assertEquals(33 * 33, seen.size());
        double[] centre = arena.tileCenter(new PachamamaTile(0, 0));
        assertEquals(-14.5, centre[0]);
        assertEquals(-14.5, centre[1]);
        assertEquals(0, new PachamamaTile(3, 5).parity());
        assertEquals(1, new PachamamaTile(-1, 0).parity());
    }

    @Test
    void clampsKeepInsideAndInvertedArenasAreRejected() {
        PachamamaArena arena = new PachamamaArena(0, 9, 0, 9, 64);
        assertEquals(1.5, arena.clampX(-4.0, 1.5));
        assertEquals(8.5, arena.clampZ(40.0, 1.5));
        assertThrows(IllegalArgumentException.class, () -> new PachamamaArena(3, 2, 0, 0, 64));
    }
}
