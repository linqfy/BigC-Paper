package dev.linqfy.bigCasares.modules.sift;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SculkflowerPlantsTest {

    @Test
    void aPositionRoundTripsThroughItsPackedForm() {
        for (int y : new int[]{-64, 0, 63, 255, 319, 2031}) {
            for (int x = 0; x < 16; x += 5) {
                for (int z = 0; z < 16; z += 7) {
                    long packed = SculkflowerPlants.pack(x, y, z);
                    assertEquals(x, SculkflowerPlants.localX(packed));
                    assertEquals(y, SculkflowerPlants.y(packed));
                    assertEquals(z, SculkflowerPlants.localZ(packed));
                }
            }
        }
    }

    @Test
    void positionsOutsideAChunkColumnAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> SculkflowerPlants.pack(16, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> SculkflowerPlants.pack(0, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> SculkflowerPlants.pack(0, 40_000, 0));
    }

    @Test
    void differentPositionsHaveDifferentKeys() {
        Set<Long> seen = new TreeSet<>();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = -64; y < 64; y += 9) {
                    assertTrue(seen.add(SculkflowerPlants.pack(x, y, z)));
                }
            }
        }
    }

    @Test
    void theStoredFormIsSortedAndDecodesToTheSameSet() {
        Set<Long> positions = new TreeSet<>(Set.of(SculkflowerPlants.pack(3, 70, 4), SculkflowerPlants.pack(0, 10, 15),
            SculkflowerPlants.pack(15, 200, 0)));
        long[] stored = SculkflowerPlants.encode(positions);
        assertEquals(3, stored.length);
        long[] sorted = stored.clone();
        java.util.Arrays.sort(sorted);
        assertArrayEquals(sorted, stored);
        assertEquals(positions, SculkflowerPlants.decode(stored));
        assertEquals(Set.of(), SculkflowerPlants.decode(null));
        assertArrayEquals(stored, SculkflowerPlants.encode(SculkflowerPlants.decode(stored)));
    }
}
