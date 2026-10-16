package dev.linqfy.bigCasares.modules.sift.feature;

import dev.linqfy.bigCasares.modules.sift.SiftData;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract every ported feature must keep: identical inputs give identical
 * blocks (determinism across runs and threads), and nothing is written outside
 * the populator's region (chunk-border safety).
 */
class SiftFeatureContractTest {

    private static final SiftFeatures FEATURES = SiftFeatures.create(SiftData.fromClasspath());

    @Test
    void sharedBlockSetsResolve() {
        assertNotNull(SiftTerrain.SIFTSLATE);
        assertEquals(6, SiftTerrain.TERRAIN.size());
        assertTrue(SiftTerrain.isOvergrownBiome("the_sift:overgrown_forest"));
    }

    @Test
    void everyPortedFeatureIsDeterministicAndStaysInItsRegion() {
        for (var entry : FEATURES.all().entrySet()) {
            for (long seed : new long[]{1L, 42L, -7L}) {
                InMemoryRegion first = run(entry.getValue(), seed);
                InMemoryRegion second = run(entry.getValue(), seed);
                assertEquals(first.snapshot(), second.snapshot(), entry.getKey() + " is not deterministic");
                assertEquals(first.spawns(), second.spawns(), entry.getKey() + " spawns are not deterministic");
                assertEquals(0, first.rejectedWrites(), entry.getKey() + " wrote outside its region");
            }
        }
    }

    @Test
    void willowTreesGrowOnSiftGround() {
        int placed = 0;
        for (long seed = 0; seed < 20; seed++) {
            InMemoryRegion region = InMemoryRegion.flat(0, 0, 64, seed);
            InMemoryRegion untouched = InMemoryRegion.flat(0, 0, 64, seed);
            FEATURES.find("the_sift:overgrown_willow_tree").orElseThrow()
                .place(region, new Random(seed), 8, 64, 8);
            if (region.countDifferences(untouched) > 0) {
                placed++;
            }
        }
        assertTrue(placed > 10, "willow trees should place on flat growth in most attempts, placed " + placed);
    }

    private static InMemoryRegion run(SiftFeature feature, long seed) {
        InMemoryRegion region = InMemoryRegion.flat(0, 0, 64, seed);
        Random random = new Random(seed);
        for (int attempt = 0; attempt < 8; attempt++) {
            feature.place(region, random, random.nextInt(16), 64, random.nextInt(16));
        }
        return region;
    }
}
