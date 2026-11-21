package dev.linqfy.bigCasares.modules.mythicdungeons.boss.diablo;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiabloArenaTest {

    /**
     * Una sala de 33x33 (bloques -16..16, piso en y=64) con paredes de y=65 a y=80 y una puerta de 3 de alto y 3 de
     * ancho en la pared este (x=17, z=-1..1, y=65..67) que da a un pasillo hacia +X.
     */
    private static boolean room(int x, int y, int z) {
        if (y <= 64) {
            return true;
        }
        if (y > 80) {
            return true;
        }
        boolean wall = Math.abs(x) == 17 || Math.abs(z) == 17;
        boolean door = x == 17 && Math.abs(z) <= 1 && y <= 67;
        if (x > 17) {
            // el pasillo: paredes a los costados, techo a y=68
            return Math.abs(z) >= 2 || y >= 68;
        }
        return wall && !door;
    }

    private static DiabloArena arena(DiabloArena.Box box) {
        double[] walls = DiabloArena.probeWalls(0.5, 65.0, 0.5, DiabloArenaTest::room);
        return new DiabloArena(0.5, 65.0, 0.5, walls, box);
    }

    @Test
    void probesFindTheWallsAndTheDoorDoesNotOpenTheSector() {
        double[] walls = DiabloArena.probeWalls(0.5, 65.0, 0.5, DiabloArenaTest::room);
        assertEquals(DiabloArena.SECTORS, walls.length);
        assertEquals(16.5, walls[0], 0.51, "east wall, through the door the wall above still counts");
        assertEquals(16.5, walls[DiabloArena.SECTORS / 4], 0.51, "south wall");
        assertTrue(Arrays.stream(walls).allMatch(distance -> distance <= DiabloArena.MAXIMUM_REACH));
    }

    @Test
    void playersInTheRoomCountAndPlayersInTheCorridorDoNot() {
        DiabloArena arena = arena(null);
        assertTrue(arena.contains(0.5, 65.0, 0.5));
        assertTrue(arena.contains(14.0, 65.0, 3.0));
        assertTrue(arena.contains(-10.0, 66.2, -10.0), "toward a corner, jumping");
        assertFalse(arena.contains(20.0, 65.0, 0.5), "in the corridor behind the door");
        assertFalse(arena.contains(40.0, 65.0, 0.5), "far down the corridor, still inside the audience radius");
        assertFalse(arena.contains(0.5, 58.0, 0.5), "a floor below");
        assertFalse(arena.contains(0.5, 75.0, 0.5), "a floor above");
    }

    @Test
    void theDungeonBoxAlsoLimits() {
        DiabloArena arena = arena(new DiabloArena.Box(-30, 60, -30, 5, 90, 30));
        assertTrue(arena.contains(4.5, 65.0, 0.0));
        assertFalse(arena.contains(6.0, 65.0, 0.0));
    }

    @Test
    void reachIsClampedAndInterpolated() {
        double[] walls = new double[DiabloArena.SECTORS];
        Arrays.fill(walls, 40.0);
        walls[0] = 1.0;
        walls[1] = Double.NaN;
        DiabloArena arena = new DiabloArena(0.0, 0.0, 0.0, walls, null);
        assertEquals(DiabloArena.MINIMUM_REACH, arena.reachAt(0.0), 1.0e-9);
        assertEquals(DiabloArena.MAXIMUM_REACH, arena.reachAt(DiabloArena.sectorAngle(1)), 1.0e-9);
        double halfway = (DiabloArena.MINIMUM_REACH + DiabloArena.MAXIMUM_REACH) / 2.0;
        assertEquals(halfway, arena.reachAt(DiabloArena.sectorAngle(1) / 2.0), 1.0e-9);
        assertEquals(DiabloArena.MAXIMUM_REACH, arena.reachAt(-Math.PI / 2.0), 1.0e-9, "negative angles wrap");
        assertThrows(IllegalArgumentException.class, () -> new DiabloArena(0, 0, 0, new double[3], null));
    }
}
