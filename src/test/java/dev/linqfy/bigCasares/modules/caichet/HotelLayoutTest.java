package dev.linqfy.bigCasares.modules.caichet;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El hotel sale de la semilla: mismo lugar, mismos bloques, y todo con techo. */
class HotelLayoutTest {

    private static final class Capture implements HotelLayout.Sink {
        final Map<Long, Material> blocks = new HashMap<>();

        @Override
        public void set(int x, int y, int z, Material material) {
            blocks.put(((long) x << 40) | ((long) y << 20) | z, material);
        }

        Material at(int x, int y, int z) {
            return blocks.get(((long) x << 40) | ((long) y << 20) | z);
        }
    }

    private static Capture run(long seed, int cx, int cz) {
        Capture capture = new Capture();
        HotelLayout.generate(seed, cx, cz, capture);
        return capture;
    }

    @Test
    void theSameSeedAndChunkGiveTheSameBlocks() {
        for (int cx = -2; cx <= 2; cx++) {
            assertEquals(run(42L, cx, 3).blocks, run(42L, cx, 3).blocks, "chunk " + cx);
        }
    }

    @Test
    void differentSeedsDifferSomewhere() {
        boolean differs = false;
        for (int cx = -3; cx <= 3 && !differs; cx++) {
            for (int cz = -3; cz <= 3 && !differs; cz++) {
                differs = !run(1L, cx, cz).blocks.equals(run(2L, cx, cz).blocks);
            }
        }
        assertTrue(differs, "dos semillas dieron el mismo hotel en toda la muestra");
    }

    @Test
    void everyColumnHasAFloorAndARoof() {
        for (long seed : new long[] {7L, 123456789L}) {
            for (int cx = -2; cx <= 2; cx++) {
                for (int cz = -2; cz <= 2; cz++) {
                    Capture capture = run(seed, cx, cz);
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            String where = "semilla " + seed + " chunk " + cx + "," + cz + " col " + x + "," + z;
                            Material floor = capture.at(x, HotelLayout.FLOOR, z);
                            assertTrue(floor != null && floor != Material.AIR, "sin piso: " + where);
                            boolean roofed = false;
                            for (int y = HotelLayout.FLOOR + 3; y < HotelLayout.FLOOR + 40; y++) {
                                Material m = capture.at(x, y, z);
                                if (m != null && m != Material.AIR) {
                                    roofed = true;
                                    break;
                                }
                            }
                            assertTrue(roofed, "sin techo: " + where);
                        }
                    }
                }
            }
        }
    }

    @Test
    void siteAndHashAreDeterministic() {
        assertEquals(HotelLayout.site(5L, 2, -3), HotelLayout.site(5L, 2, -3));
        assertEquals(HotelLayout.hash(5L, 1, 2, 3), HotelLayout.hash(5L, 1, 2, 3));
        assertNotEquals(HotelLayout.hash(5L, 1, 2, 3), HotelLayout.hash(5L, 1, 2, 4));
        assertNotEquals(HotelLayout.hash(5L, 1, 2, 3), HotelLayout.hash(6L, 1, 2, 3));
    }

    @Test
    void theSpawnZoneIsAlwaysLevelZero() {
        assertEquals(HotelLayout.Theme.LEVEL0, HotelLayout.site(1L, 0, 0).theme());
        assertEquals(HotelLayout.Theme.LEVEL0, HotelLayout.site(999L, 0, 0).theme());
    }
}
