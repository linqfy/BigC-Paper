package dev.linqfy.bigCasares.modules.caichet;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** El hotel sale de la semilla: mismo lugar, mismos bloques, y todo con techo. */
class HotelLayoutTest {

    private static final class Capture implements HotelLayout.Sink {
        final Map<Long, HotelLayout.Cell> blocks = new HashMap<>();

        @Override
        public void set(int x, int y, int z, HotelLayout.Cell cell) {
            blocks.put(key(x, y, z), cell);
        }

        HotelLayout.Cell at(int x, int y, int z) {
            return blocks.get(key(x, y, z));
        }

        boolean isAir(int x, int y, int z) {
            HotelLayout.Cell cell = at(x, y, z);
            return cell == null || cell instanceof HotelLayout.Cell.Vanilla vanilla && vanilla.material() == Material.AIR;
        }

        private static long key(int x, int y, int z) {
            return ((long) x << 40) | ((long) y << 20) | z;
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
                            assertTrue(!capture.isAir(x, HotelLayout.FLOOR, z), "sin piso: " + where);
                            boolean roofed = false;
                            for (int y = HotelLayout.FLOOR + 3; y < HotelLayout.FLOOR + 40; y++) {
                                if (!capture.isAir(x, y, z)) {
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

    @Test
    void copperZonesUseOnlyCopperAndTheOthersOnlyTheirOwnBlocks() {
        int copperColumns = 0;
        int ownColumns = 0;
        for (int cx = -4; cx <= 4; cx++) {
            for (int cz = -4; cz <= 4; cz++) {
                Capture capture = run(11L, cx, cz);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        boolean copper = false;
                        boolean own = false;
                        for (int y = HotelLayout.FLOOR - 5; y < HotelLayout.FLOOR + 60; y++) {
                            HotelLayout.Cell cell = capture.at(x, y, z);
                            copper |= cell instanceof HotelLayout.Cell.Bulb
                                || cell instanceof HotelLayout.Cell.Vanilla vanilla && vanilla.material().name().contains("COPPER");
                            own |= cell instanceof HotelLayout.Cell.Custom || cell instanceof HotelLayout.Cell.Panel;
                        }
                        assertFalse(copper && own, "cobre y bloques propios en la misma columna " + cx + "," + cz);
                        copperColumns += copper ? 1 : 0;
                        ownColumns += own ? 1 : 0;
                    }
                }
            }
        }
        assertTrue(copperColumns > 0, "hay zonas de cobre");
        assertTrue(ownColumns > 0, "y zonas con los bloques propios");
    }

    @Test
    void everyOwnBlockHasItsCopperAndPanelsBecomeLitBulbs() {
        Set<Material> used = new HashSet<>();
        for (HotelBlock block : HotelBlock.values()) {
            Material copper = HotelLayout.copper(block);
            assertTrue(copper.name().startsWith("WAXED_"), block + " -> " + copper);
            if (block != HotelBlock.LIGHT_PANEL_OFF) {
                assertTrue(used.add(copper), "dos bloques con el mismo cobre: " + copper);
            }
        }
        assertEquals(new HotelLayout.Cell.Bulb(Material.WAXED_COPPER_BULB),
            HotelLayout.copper(HotelLayout.LIGHT_PANEL));
        assertEquals(new HotelLayout.Cell.Bulb(Material.WAXED_WEATHERED_COPPER_BULB),
            HotelLayout.copper(HotelLayout.LIGHT_DIM));
        assertFalse(HotelLayout.site(1L, 0, 0).copper(), "donde se aparece es el nivel 0 de siempre");
    }

    @Test
    void everyPanelWithAirBelowHasItsLightAndEveryLightHangsFromAPanel() {
        int panels = 0;
        int dim = 0;
        for (long seed : new long[] {7L, 99L}) {
            for (int cx = -3; cx <= 3; cx++) {
                for (int cz = -3; cz <= 3; cz++) {
                    Capture capture = run(seed, cx, cz);
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            for (int y = HotelLayout.FLOOR - 4; y < HotelLayout.FLOOR + 60; y++) {
                                HotelLayout.Cell cell = capture.at(x, y, z);
                                String where = "seed " + seed + " chunk " + cx + "," + cz + " col " + x + "," + z + " y " + y;
                                if (cell instanceof HotelLayout.Cell.Panel panel) {
                                    panels++;
                                    assertTrue(panel.level() == 15 || panel.level() == 8, where);
                                    dim += panel.level() == 8 ? 1 : 0;
                                    if (capture.isAir(x, y - 1, z)) {
                                        fail("panel sin luz sobre aire: " + where);
                                    }
                                }
                                if (cell instanceof HotelLayout.Cell.Light light) {
                                    HotelLayout.Cell above = capture.at(x, y + 1, z);
                                    assertTrue(above instanceof HotelLayout.Cell.Panel panel && panel.level() == light.level(),
                                        "luz sin panel arriba: " + where);
                                }
                            }
                        }
                    }
                }
            }
        }
        assertTrue(panels > 0 && dim > 0, "la muestra tiene que tener paneles y paneles tenues");
    }
}
