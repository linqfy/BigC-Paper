package dev.linqfy.bigCasares.modules.mythicdungeons.boss.iblis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IblisRoomTest {

    /**
     * A 33x33 room (interior x, z 1..33) with walls at 0 and 34, floor block at y 64 (players stand at 65), a pillar
     * in front of the altar and a door gap in the north wall; the altar (boss spawn) is at (17, 66, 20).
     */
    private static boolean passable(int x, int y, int z) {
        if (y <= 64) {
            return false;
        }
        if (x == 17 && z == 20 && y == 65) {
            return false; // the altar block
        }
        if (x >= 16 && x <= 18 && z >= 14 && z <= 15) {
            return false; // pillar between the altar and the north wall
        }
        boolean wallX = x == 0 || x == 34;
        boolean wallZ = z == 0 || z == 34;
        if (z == 0 && x >= 16 && x <= 18 && y <= 67) {
            return true; // door
        }
        return !(wallX || wallZ) || x < 0 || x > 34 || z < 0 || z > 34;
    }

    @Test
    void measuresTheRoomFromTheAltarDespitePillarAndAltar() {
        int floor = IblisRoom.floorUnder(17, 66, 20, IblisRoomTest::passable);
        assertEquals(65, floor);
        IblisRoom room = IblisRoom.probe(17, 20, floor, IblisRoomTest::passable);
        assertEquals(1, room.minX());
        assertEquals(33, room.maxX());
        assertEquals(33, room.maxZ());
        assertTrue(room.minZ() <= 1, "the pillar does not shrink the room: " + room);
    }

    @Test
    void onlyPlayersInTheRoomCount() {
        IblisRoom room = new IblisRoom(1, 33, 1, 33, 65);
        assertTrue(room.contains(17.5, 65.0, 17.5));
        assertTrue(room.contains(1.2, 68.5, 33.9), "a jump near the corner");
        assertFalse(room.contains(17.5, 65.0, 36.0), "corridor behind the wall");
        assertFalse(room.contains(-3.0, 65.0, 17.0));
        assertFalse(room.contains(17.5, 80.0, 17.5), "another level above");
        assertFalse(room.contains(17.5, 58.0, 17.5), "another level below");
    }

    @Test
    void vesselRingStaysInsideTheRoom() {
        IblisRoom room = new IblisRoom(1, 33, 1, 33, 65);
        for (IblisPoint point : room.ring(8, 30.0, 0.3)) {
            assertTrue(point.x() >= 3.0 && point.x() <= 32.0 && point.z() >= 3.0 && point.z() <= 32.0, "" + point);
        }
        assertEquals(17.5, room.centerX());
    }
}
