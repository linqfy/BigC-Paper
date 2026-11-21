package dev.linqfy.bigCasares.modules.alexscaves.core.worldgen;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoronoiRarityTest {
    private static final VoronoiRarity.RarityParameters DEFAULTS = VoronoiRarity.RarityParameters.MOD_DEFAULTS;

    @Test
    void sameSeedGivesSameAnswer() {
        VoronoiRarity first = new VoronoiRarity(42L, DEFAULTS);
        VoronoiRarity second = new VoronoiRarity(42L, DEFAULTS);
        for (int x = -20000; x <= 20000; x += 997) {
            for (int z = -20000; z <= 20000; z += 1009) {
                assertEquals(first.sample(x, z), second.sample(x, z));
            }
        }
    }

    @Test
    void differentSeedsPlaceBiomesDifferently() {
        VoronoiRarity first = new VoronoiRarity(1L, DEFAULTS);
        VoronoiRarity second = new VoronoiRarity(2L, DEFAULTS);
        int differences = 0;
        for (int x = 0; x < 40000; x += 400) {
            if (!first.sample(x, 1234).equals(second.sample(x, 1234))) {
                differences++;
            }
        }
        assertTrue(differences > 0);
    }

    @Test
    void coverageMatchesRadiusAndSeparation() {
        VoronoiRarity rarity = new VoronoiRarity(99L, DEFAULTS);
        int inside = 0;
        int total = 0;
        for (int x = -60000; x <= 60000; x += 211) {
            for (int z = -60000; z <= 60000; z += 223) {
                total++;
                if (rarity.sample(x, z).isPresent()) {
                    inside++;
                }
            }
        }
        // Circles of radius 300 on a 1200-block lattice cover pi*300^2/1200^2 ~ 19.6% of the world.
        double coverage = inside / (double) total;
        assertTrue(coverage > 0.13 && coverage < 0.27, "coverage " + coverage);
    }

    @Test
    void sampledPointIsWithinRadiusOfItsCentre() {
        VoronoiRarity rarity = new VoronoiRarity(7L, new VoronoiRarity.RarityParameters(300, 900, 0, 0.45));
        for (int step = -30000; step <= 30000; step += 313) {
            final int x = step;
            Optional<VoronoiRarity.CellSample> sample = rarity.sample(x, 777);
            sample.ifPresent(cell -> {
                // The mod tests whole quarts: up to a quart diagonal (about 6 blocks) past the radius.
                assertTrue(cell.distance() < 306.0, "distance " + cell.distance());
                double dx = cell.centerX() - x;
                double dz = cell.centerZ() - 777;
                assertEquals(cell.distance(), Math.sqrt(dx * dx + dz * dz), 1e-6);
            });
        }
    }

    @Test
    void allSixRarityIdsAppearRoughlyUniformly() {
        Map<Integer, Integer> counts = new HashMap<>();
        for (long cellX = -60; cellX < 60; cellX++) {
            for (long cellZ = -60; cellZ < 60; cellZ++) {
                counts.merge(VoronoiRarity.rarityId(ModVoronoi.get2(5L, 0.45, cellX, cellZ).hash()), 1, Integer::sum);
            }
        }
        assertEquals(CaveBiomeId.count(), counts.size());
        int expected = 120 * 120 / CaveBiomeId.count();
        for (int count : counts.values()) {
            assertTrue(Math.abs(count - expected) < expected * 0.15, "count " + count);
        }
    }

    @Test
    void largerSeparationMakesBiomesRarer() {
        VoronoiRarity dense = new VoronoiRarity(3L, new VoronoiRarity.RarityParameters(300, 300, 0.15, 0.45));
        VoronoiRarity sparse = new VoronoiRarity(3L, new VoronoiRarity.RarityParameters(300, 3000, 0.15, 0.45));
        int denseHits = 0;
        int sparseHits = 0;
        for (int x = -50000; x <= 50000; x += 257) {
            for (int z = -50000; z <= 50000; z += 2531) {
                denseHits += dense.sample(x, z).isPresent() ? 1 : 0;
                sparseHits += sparse.sample(x, z).isPresent() ? 1 : 0;
            }
        }
        assertTrue(denseHits > sparseHits * 3);
    }

    @Test
    void invalidParametersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new VoronoiRarity.RarityParameters(0, 900, 0.1, 0.1));
        assertThrows(IllegalArgumentException.class, () -> new VoronoiRarity.RarityParameters(300, -1, 0.1, 0.1));
        assertThrows(IllegalArgumentException.class, () -> new VoronoiRarity.RarityParameters(300, 900, 1.5, 0.1));
        assertThrows(IllegalArgumentException.class, () -> new VoronoiRarity.RarityParameters(300, 900, 0.1, -0.1));
        assertThrows(IllegalArgumentException.class, () -> new VoronoiRarity.RarityParameters(Double.NaN, 900, 0.1, 0.1));
    }

    @Test
    void noiseStaysInRange() {
        SeededValueNoise noise = new SeededValueNoise(11L);
        double previous = noise.sample(0.0, 0.0);
        boolean varied = false;
        for (double x = -50; x < 50; x += 0.37) {
            double value = noise.sample(x, x * 0.5);
            assertTrue(value >= -1.0 && value <= 1.0);
            varied |= value != previous;
        }
        assertTrue(varied);
        assertNotEquals(new SeededValueNoise(1L).sample(3.3, 4.4), new SeededValueNoise(2L).sample(3.3, 4.4));
        assertFalse(Double.isNaN(noise.sample(1e9, -1e9)));
    }
}
