package dev.linqfy.bigCasares.modules.mythicdungeons.boss.pachamama;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PachamamaWaterSweepTest {

    private static long key(int x, int z) {
        return PachamamaWaterSweep.key(x, z);
    }

    @Test
    void keysRoundTripNegativeCoordinates() {
        long key = key(-1205, 37);
        assertEquals(-1205, PachamamaWaterSweep.x(key));
        assertEquals(37, PachamamaWaterSweep.z(key));
        assertEquals(-8, PachamamaWaterSweep.z(key(4, -8)));
    }

    @Test
    void theAreaIsThePaintedColumnsAndABorder() {
        Set<Long> area = PachamamaWaterSweep.area(Set.of(key(0, 0)));
        assertEquals(9, area.size());
        assertTrue(area.contains(key(-1, 1)));
        assertTrue(area.contains(key(1, -1)));
    }

    @Test
    void onlyNewWaterInsideTheRoomIsDried() {
        // two diagonal painted columns (a chessboard corner): the rule made a source at (1, 0)
        Set<Long> painted = Set.of(key(0, 0), key(1, 1));
        Set<Long> area = PachamamaWaterSweep.area(painted);
        Set<Long> before = Set.of(key(2, 2));
        Set<Long> waterNow = new HashSet<>(Set.of(key(1, 0), key(2, 2), key(-1, -1), key(5, 5)));
        List<Long> strays = PachamamaWaterSweep.strays(area, before, waterNow::contains,
            column -> PachamamaWaterSweep.x(column) >= 0);
        // (2, 2) was already there, (-1, -1) is outside the room, (5, 5) is far from the painted area
        assertEquals(List.of(key(1, 0)), strays);
    }
}
