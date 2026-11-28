package dev.linqfy.bigCasares.modules.caichet;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Que casas llevan cofre y que lleva cada uno: sale de la semilla y cae adentro de la casa. */
class DreamChestsTest {

    private static final long SEED = 2024L;
    private static final int RANGE = 40;
    private static final int SAMPLES_PER_THEME = 6;

    /**
     * Los bloques que arma DreamLayout en una zona: lo que no esta anotado es aire. El generador usa coordenadas del
     * chunk (0..15); aca se pasan a las del mundo.
     */
    private static final class Grid implements DreamLayout.Sink {
        final Map<Long, Material> blocks = new HashMap<>();
        final List<int[]> doors = new ArrayList<>();
        int originX;
        int originZ;

        static long key(int x, int y, int z) {
            return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (y & 0xFFF);
        }

        @Override
        public void set(int x, int y, int z, Material material) {
            blocks.put(key(originX + x, y, originZ + z), material);
        }

        @Override
        public void door(int x, int y, int z, Material door, int facing) {
            blocks.put(key(originX + x, y, originZ + z), door);
            blocks.put(key(originX + x, y + 1, originZ + z), door);
            doors.add(new int[] {originX + x, y, originZ + z});
        }

        boolean solid(int x, int y, int z) {
            Material material = blocks.get(key(x, y, z));
            return material != null && material != Material.AIR;
        }
    }

    private static Grid around(int chunkX, int chunkZ) {
        Grid grid = new Grid();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                grid.originX = (chunkX + dx) * 16;
                grid.originZ = (chunkZ + dz) * 16;
                DreamLayout.generate(SEED, chunkX + dx, chunkZ + dz, grid);
            }
        }
        return grid;
    }

    private static boolean wallWithin(Grid grid, DreamLayout.ChestSpot spot, int dx, int dz) {
        for (int step = 1; step <= 12; step++) {
            if (grid.solid(spot.x() + dx * step, spot.y(), spot.z() + dz * step)) {
                return true;
            }
        }
        return false;
    }

    private static boolean roofWithin(Grid grid, DreamLayout.ChestSpot spot) {
        for (int step = 2; step <= 12; step++) {
            if (grid.solid(spot.x(), spot.y() + step, spot.z())) {
                return true;
            }
        }
        return false;
    }

    @Test
    void spotsAreDeterministicAndInsideTheirChunk() {
        int total = 0;
        for (int cx = -RANGE; cx <= RANGE; cx++) {
            for (int cz = -RANGE; cz <= RANGE; cz++) {
                List<DreamLayout.ChestSpot> spots = DreamLayout.chestSpotsInChunk(SEED, cx, cz);
                assertEquals(spots, DreamLayout.chestSpotsInChunk(SEED, cx, cz));
                for (DreamLayout.ChestSpot spot : spots) {
                    total++;
                    assertEquals(cx, spot.x() >> 4);
                    assertEquals(cz, spot.z() >> 4);
                    assertTrue(spot.facing() >= 0 && spot.facing() <= 3);
                    assertTrue(spot.y() > DreamLayout.FLOOR);
                }
            }
        }
        assertTrue(total > 0, "no hay ningun cofre en los chunks -" + RANGE + ".." + RANGE);
    }

    @Test
    void differentSeedsPickDifferentHouses() {
        Set<String> a = new HashSet<>();
        Set<String> b = new HashSet<>();
        for (int cx = -15; cx <= 15; cx++) {
            for (int cz = -15; cz <= 15; cz++) {
                DreamLayout.chestSpotsInChunk(SEED, cx, cz).forEach(s -> a.add(s.x() + "," + s.z()));
                DreamLayout.chestSpotsInChunk(SEED + 1, cx, cz).forEach(s -> b.add(s.x() + "," + s.z()));
            }
        }
        assertFalse(a.equals(b));
    }

    @Test
    void everyChestSitsOnTheHouseFloorInsideClosedWallsAndNeverOnTheDoor() {
        Map<DreamLayout.Theme, Integer> checked = new EnumMap<>(DreamLayout.Theme.class);
        for (int cx = -RANGE; cx <= RANGE; cx++) {
            for (int cz = -RANGE; cz <= RANGE; cz++) {
                List<DreamLayout.ChestSpot> spots = DreamLayout.chestSpotsInChunk(SEED, cx, cz);
                if (spots.isEmpty()) {
                    continue;
                }
                for (DreamLayout.ChestSpot spot : spots) {
                    if (checked.getOrDefault(spot.theme(), 0) >= SAMPLES_PER_THEME) {
                        continue;
                    }
                    checked.merge(spot.theme(), 1, Integer::sum);
                    Grid grid = around(cx, cz);
                    String where = spot.theme() + " " + spot.x() + " " + spot.y() + " " + spot.z();
                    assertEquals(spot.floor(), grid.blocks.get(Grid.key(spot.x(), spot.y() - 1, spot.z())),
                        "piso: " + where);
                    assertFalse(grid.solid(spot.x(), spot.y(), spot.z()), "celda ocupada: " + where);
                    assertFalse(grid.solid(spot.x(), spot.y() + 1, spot.z()), "sin aire arriba: " + where);
                    assertTrue(wallWithin(grid, spot, 1, 0) && wallWithin(grid, spot, -1, 0)
                        && wallWithin(grid, spot, 0, 1) && wallWithin(grid, spot, 0, -1), "sin paredes: " + where);
                    assertTrue(roofWithin(grid, spot), "sin techo: " + where);
                    for (int[] door : grid.doors) {
                        if (door[1] == spot.y()) {
                            int distance = Math.abs(door[0] - spot.x()) + Math.abs(door[2] - spot.z());
                            assertTrue(distance >= 2, "tapa una puerta: " + where);
                        }
                    }
                }
            }
        }
        for (DreamLayout.Theme theme : List.of(DreamLayout.Theme.OJOS, DreamLayout.Theme.BARRIO,
            DreamLayout.Theme.CAMPO)) {
            assertTrue(checked.getOrDefault(theme, 0) > 0, "ningun cofre de " + theme + " en la zona probada");
        }
    }

    @Test
    void aboutThirtyPercentOfOjosHousesGetAChest() {
        // se cuentan las casas de OJOS y los cofres de OJOS en la misma zona
        int houses = 0;
        int chests = 0;
        for (int cx = -60; cx <= 60; cx++) {
            for (int cz = -60; cz <= 60; cz++) {
                for (DreamLayout.ChestSpot spot : DreamLayout.chestSpotsInChunk(SEED, cx, cz)) {
                    if (spot.theme() == DreamLayout.Theme.OJOS) {
                        chests++;
                    }
                }
            }
        }
        // los chunks -60..60 son los bloques -960..975: unas 30 parcelas de 64 por lado
        for (int ix = -15; ix <= 14; ix++) {
            for (int iz = -15; iz <= 14; iz++) {
                if (DreamLayout.theme(SEED, ix * 64 + 30, iz * 64 + 30) == DreamLayout.Theme.OJOS) {
                    houses++;
                }
            }
        }
        // cada parcela de OJOS tiene casa un 55 % de las veces; con 30 % de cofres es 16,5 % de las parcelas
        assertTrue(chests > 0 && houses > 0);
        double ratio = (double) chests / houses;
        assertTrue(ratio > 0.05 && ratio < 0.30, "proporcion de cofres por parcela de OJOS: " + ratio);
    }

    @Test
    void lootIsDeterministicAndOnlyUsesExistingItems() {
        assertEquals(DreamChests.loot(new Random(5)), DreamChests.loot(new Random(5)));
        Set<String> paintings = new HashSet<>();
        int withBottles = 0;
        Random random = new Random(11);
        int rolls = 4000;
        for (int i = 0; i < rolls; i++) {
            DreamChests.Loot loot = DreamChests.loot(random);
            paintings.add(loot.painting());
            assertTrue(loot.bottles() >= 0 && loot.bottles() <= 2);
            if (loot.bottles() > 0) {
                withBottles++;
            }
        }
        assertEquals(new HashSet<>(DreamPaintings.ids()), paintings);
        double rate = withBottles * 100.0 / rolls;
        assertTrue(Math.abs(rate - DreamChests.BOTTLE_PERCENT) < 5, "botellas: " + rate + " %");
    }
}
