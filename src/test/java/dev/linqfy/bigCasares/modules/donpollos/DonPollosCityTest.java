package dev.linqfy.bigCasares.modules.donpollos;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DonPollosCityTest {

    @Test
    void bundledCityLoadsWithItsSizeAndBlocks() throws IOException {
        DonPollosCityTemplate city = load();

        assertEquals(45, city.width());
        assertEquals(98, city.height());
        assertEquals(56, city.length());
        assertTrue(city.palette().contains("minecraft:quartz_block"));
        int stone = 0;
        for (int x = 0; x < city.width(); x++) {
            for (int z = 0; z < city.length(); z++) {
                if (city.blockAt(x, 0, z).equals("minecraft:stone")) {
                    stone++;
                }
            }
        }
        assertTrue(stone > city.width() * city.length() / 2, "la base de la ciudad es de piedra");
    }

    @Test
    void glassPanesKeepTheirConnections() throws IOException {
        DonPollosCityTemplate city = load();
        assertTrue(city.palette().stream()
            .anyMatch(state -> state.contains("glass_pane[") && state.contains("=true")));
    }

    @Test
    void layoutIsDeterministicAndStaysInsideItsCell() {
        DonPollosCityLayout layout = new DonPollosCityLayout(1234L, 1400, 1.0, 0, 45, 56);
        for (int cx = -3; cx <= 3; cx++) {
            for (int cz = -3; cz <= 3; cz++) {
                DonPollosCityLayout.CityPlan plan = layout.cityInCell(cx, cz).orElseThrow();
                assertEquals(plan, layout.cityInCell(cx, cz).orElseThrow());
                assertTrue(plan.x() >= cx * 1400 && plan.x() + plan.sizeX() <= (cx + 1) * 1400);
                assertTrue(plan.z() >= cz * 1400 && plan.z() + plan.sizeZ() <= (cz + 1) * 1400);
                assertEquals(Optional.of(plan), layout.cityAnchoredAt(plan.anchorChunkX(), plan.anchorChunkZ()));
            }
        }
    }

    @Test
    void rotatedCitiesSwapTheirFootprint() {
        DonPollosCityLayout layout = new DonPollosCityLayout(99L, 1400, 1.0, 0, 45, 56);
        for (int cx = 0; cx < 20; cx++) {
            DonPollosCityLayout.CityPlan plan = layout.cityInCell(cx, 0).orElseThrow();
            assertEquals(plan.rotation() % 2 == 0 ? 45 : 56, plan.sizeX());
        }
    }

    @Test
    void chanceAndSpawnDistanceSkipSomeCells() {
        DonPollosCityLayout none = new DonPollosCityLayout(1L, 1400, 0.0, 0, 45, 56);
        assertTrue(none.cityInCell(5, 5).isEmpty());
        DonPollosCityLayout farFromSpawn = new DonPollosCityLayout(1L, 1400, 1.0, 1_000_000, 45, 56);
        assertTrue(farFromSpawn.cityInCell(0, 0).isEmpty());
    }

    @Test
    void nearestFindsAPlannedCity() {
        DonPollosCityLayout layout = new DonPollosCityLayout(7L, 1400, 1.0, 0, 45, 56);
        DonPollosCityLayout.CityPlan plan = layout.nearest(100, 100, 1).orElseThrow();
        DonPollosCityLayout.CityPlan sameCell = layout.cityInCell(0, 0).orElseThrow();
        long best = (long) (plan.centerX() - 100) * (plan.centerX() - 100) + (long) (plan.centerZ() - 100) * (plan.centerZ() - 100);
        long cell = (long) (sameCell.centerX() - 100) * (sameCell.centerX() - 100)
            + (long) (sameCell.centerZ() - 100) * (sameCell.centerZ() - 100);
        assertTrue(best <= cell);
    }

    @Test
    void rejectsSpacingSmallerThanTheCity() {
        assertThrows(IllegalArgumentException.class, () -> new DonPollosCityLayout(1L, 60, 1.0, 0, 45, 56));
    }

    private static DonPollosCityTemplate load() throws IOException {
        try (InputStream stream = DonPollosCityTest.class.getClassLoader().getResourceAsStream("donpollos/structures/ciudad.json.gz")) {
            assertNotNull(stream);
            return DonPollosCityTemplate.load(stream);
        }
    }
}
