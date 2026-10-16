package dev.linqfy.bigCasares.modules.sift.feature;

import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.IntBinaryOperator;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour of the monolith family ports: bare/lush monoliths, dry sculk spikes
 * and cliff arches. Terrain is built on {@link InMemoryRegion}; the generic
 * determinism and region-bounds contract lives in {@link SiftFeatureContractTest}.
 */
class MonolithFeaturesTest {

    private static final SiftFeature BARE = new SiftMonolithFeature(false);
    private static final SiftFeature LUSH = new SiftMonolithFeature(true);
    private static final SiftFeature SPIKES = new SiftDrySpikeFeature();
    private static final SiftFeature ARCH = new SiftArchFeature();
    private static final int SURFACE = 64;
    private static final int SEEDS = 24;

    // ---- monoliths -------------------------------------------------------------------------

    @Test
    void bareMonolithRisesFromFlatGround() {
        int placed = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            InMemoryRegion region = InMemoryRegion.flat(0, 0, SURFACE, seed);
            if (!BARE.place(region, new Random(seed), 8, SURFACE, 8)) {
                continue;
            }
            placed++;
            // base y is surface - 2, main height 24..44 (surface plants may add one more block).
            int top = highestSolid(region);
            assertTrue(top >= SURFACE - 2 + 24, "bare monolith too short: top " + top + " seed " + seed);
            assertTrue(top <= SURFACE - 2 + 44 + 1, "bare monolith too tall: top " + top + " seed " + seed);
            assertEquals(0, region.rejectedWrites(), "wrote outside the region, seed " + seed);
            // The bare variant never uses growth on its pillar: growth above the ground layer is only from plants.
            assertEquals(0, countState(region, SiftTerrain.SIFTSLATE_GROWTH, SURFACE + 1, 255));
        }
        assertEquals(SEEDS, placed, "bare monoliths should place on flat Sift ground");
    }

    @Test
    void lushMonolithIsTallerAndCappedWithGrowthAndPlants() {
        int withPlants = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            InMemoryRegion region = InMemoryRegion.flat(0, 0, SURFACE, seed);
            assertTrue(LUSH.place(region, new Random(seed), 8, SURFACE, 8), "lush monolith should place, seed " + seed);
            int top = highestSolid(region);
            assertTrue(top >= SURFACE - 2 + 31, "lush monolith too short: top " + top + " seed " + seed);
            assertTrue(top <= SURFACE - 2 + 55 + 1, "lush monolith too tall: top " + top + " seed " + seed);
            assertTrue(countState(region, SiftTerrain.SIFTSLATE_GROWTH, SURFACE + 20, 255) > 0,
                "lush monolith needs a growth cap, seed " + seed);
            if (countPlants(region, SURFACE + 20, 255) > 0) {
                withPlants++;
            }
        }
        assertTrue(withPlants > 0, "the surface decorator should plant some lush crowns");
    }

    @Test
    void monolithsDifferAcrossSeeds() {
        for (SiftFeature feature : List.of(BARE, LUSH)) {
            InMemoryRegion reference = InMemoryRegion.flat(0, 0, SURFACE, 0);
            assertTrue(feature.place(reference, new Random(0), 8, SURFACE, 8));
            for (long seed = 1; seed < 8; seed++) {
                InMemoryRegion region = InMemoryRegion.flat(0, 0, SURFACE, seed);
                feature.place(region, new Random(seed), 8, SURFACE, 8);
                assertTrue(reference.countDifferences(region) > 0, "seed " + seed + " produced the same monolith");
            }
        }
    }

    @Test
    void lushMonolithsSometimesGrowSeparateSatellitePillars() {
        int multiPillar = 0;
        for (long seed = 0; seed < 40; seed++) {
            InMemoryRegion region = InMemoryRegion.flat(0, 0, SURFACE, seed);
            LUSH.place(region, new Random(seed), 8, SURFACE, 8);
            if (pillarCount(region, SURFACE + 13) > 1) {
                multiPillar++;
            }
        }
        assertTrue(multiPillar > 0, "lush monoliths draw 1-3 satellites; some should stand apart from the main pillar");
    }

    @Test
    void monolithsNeedDryDeepSiftTerrain() {
        // Shallow terrain: fewer than the six supporting Sift blocks below the surface.
        InMemoryRegion shallow = ground(1, 256, (x, z) -> 3, SiftTerrain.SIFTSLATE_GROWTH, SiftTerrain.SIFTSLATE);
        InMemoryRegion shallowReference = ground(1, 256, (x, z) -> 3, SiftTerrain.SIFTSLATE_GROWTH, SiftTerrain.SIFTSLATE);
        for (SiftFeature feature : List.of(BARE, LUSH, SPIKES)) {
            assertFalse(feature.place(shallow, new Random(1), 8, 3, 8));
        }
        assertEquals(0, shallow.countDifferences(shallowReference));

        // A lake covering the ground.
        InMemoryRegion lake = InMemoryRegion.flat(0, 0, SURFACE, 2);
        fillLayer(lake, SURFACE, BlockStateSpec.of("minecraft:water"));
        InMemoryRegion lakeReference = InMemoryRegion.flat(0, 0, SURFACE, 2);
        fillLayer(lakeReference, SURFACE, BlockStateSpec.of("minecraft:water"));
        for (SiftFeature feature : List.of(BARE, LUSH, SPIKES)) {
            assertFalse(feature.place(lake, new Random(2), 8, SURFACE + 1, 8));
        }
        assertEquals(0, lake.countDifferences(lakeReference));

        // Foreign terrain is not Sift terrain.
        InMemoryRegion stone = ground(3, 256, (x, z) -> SURFACE, BlockStateSpec.of("minecraft:stone"),
            BlockStateSpec.of("minecraft:stone"));
        for (SiftFeature feature : List.of(BARE, LUSH, SPIKES, ARCH)) {
            assertFalse(feature.place(stone, new Random(3), 8, SURFACE, 8));
        }
    }

    @Test
    void monolithsRespectTheBuildLimit() {
        // maxY 80: the top block is y=79, leaving 12 blocks above y=64, below the 18-block minimum.
        InMemoryRegion capped = ground(4, 80, (x, z) -> SURFACE, SiftTerrain.SIFTSLATE_GROWTH, SiftTerrain.SIFTSLATE);
        assertFalse(BARE.place(capped, new Random(4), 8, SURFACE, 8));
        assertFalse(LUSH.place(capped, new Random(4), 8, SURFACE, 8));
        assertEquals(0, capped.rejectedWrites());

        // Tall enough: the pillar is trimmed to fit under the limit.
        InMemoryRegion tight = ground(5, 100, (x, z) -> SURFACE, SiftTerrain.SIFTSLATE_GROWTH, SiftTerrain.SIFTSLATE);
        assertTrue(LUSH.place(tight, new Random(5), 8, SURFACE, 8));
        assertEquals(0, tight.rejectedWrites());
        assertTrue(highestSolid(tight) <= 99);
    }

    // ---- dry sculk spikes ------------------------------------------------------------------

    @Test
    void drySculkSpikesRiseAsDrySculk() {
        int placed = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            InMemoryRegion region = InMemoryRegion.flat(0, 0, SURFACE, seed);
            if (!SPIKES.place(region, new Random(seed), 8, SURFACE, 8)) {
                continue;
            }
            placed++;
            int top = highestOf(region, SiftTerrain.DRY_HEALTHY_SCULK);
            // The main spike is 19..31 blocks tall from the surface.
            assertTrue(top >= SURFACE + 19, "main spike too short: top " + top + " seed " + seed);
            assertTrue(top <= SURFACE + 31, "main spike too tall: top " + top + " seed " + seed);
            assertEquals(0, region.rejectedWrites());
        }
        assertEquals(SEEDS, placed, "spikes should place on flat Sift ground");
    }

    @Test
    void drySculkSpikesDifferAcrossSeeds() {
        InMemoryRegion reference = InMemoryRegion.flat(0, 0, SURFACE, 0);
        assertTrue(SPIKES.place(reference, new Random(0), 8, SURFACE, 8));
        for (long seed = 1; seed < 8; seed++) {
            InMemoryRegion region = InMemoryRegion.flat(0, 0, SURFACE, seed);
            SPIKES.place(region, new Random(seed), 8, SURFACE, 8);
            assertTrue(reference.countDifferences(region) > 0, "seed " + seed + " produced the same spikes");
        }
    }

    @Test
    void drySculkSpikesClusterSatellitesAroundTheMainSpike() {
        int multiSpike = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            InMemoryRegion region = InMemoryRegion.flat(0, 0, SURFACE, seed);
            SPIKES.place(region, new Random(seed), 8, SURFACE, 8);
            if (pillarCount(region, SURFACE + 8) > 1) {
                multiSpike++;
            }
        }
        assertTrue(multiSpike > 0, "2-4 satellite spikes should stand apart from the main spike for some seeds");
    }

    // ---- cliff arches ----------------------------------------------------------------------

    @Test
    void archSpansAValley() {
        int placed = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            InMemoryRegion region = valley(seed, SiftTerrain.SIFTSLATE_GROWTH);
            if (!ARCH.place(region, new Random(seed), 8, SURFACE, 8)) {
                continue;
            }
            placed++;
            assertEquals(0, region.rejectedWrites(), "wrote outside the region, seed " + seed);
            // The valley floor around the centre is at most y=65; the arch hangs high above it.
            int bridge = 0;
            for (int x = 5; x <= 11; x++) {
                for (int z = 5; z <= 11; z++) {
                    for (int y = 74; y < 120; y++) {
                        if (!region.isAir(x, y, z) && !SiftTerrain.PLANT_TYPES.contains(region.blockId(x, y, z))) {
                            bridge++;
                        }
                    }
                }
            }
            assertTrue(bridge > 20, "no arch above the valley centre, seed " + seed + " (" + bridge + " blocks)");
        }
        assertTrue(placed >= SEEDS * 3 / 4, "arches should place across a clear valley, placed " + placed);
    }

    @Test
    void archesDifferAcrossSeeds() {
        InMemoryRegion reference = valley(0, SiftTerrain.SIFTSLATE_GROWTH);
        assertTrue(ARCH.place(reference, new Random(0), 8, SURFACE, 8));
        for (long seed = 1; seed < 8; seed++) {
            InMemoryRegion region = valley(seed, SiftTerrain.SIFTSLATE_GROWTH);
            ARCH.place(region, new Random(seed), 8, SURFACE, 8);
            assertTrue(reference.countDifferences(region) > 0, "seed " + seed + " produced the same arch");
        }
    }

    @Test
    void archBodyFollowsTheGroundItIsAnchoredTo() {
        int healthyOnHealthy = 0;
        int growthCoatOnGrowth = 0;
        int placedOnHealthy = 0;
        int placedOnGrowth = 0;
        int plantedCrowns = 0;
        for (long seed = 0; seed < 40; seed++) {
            InMemoryRegion healthyGround = valley(seed, SiftTerrain.HEALTHY_SCULK);
            if (ARCH.place(healthyGround, new Random(seed), 8, SURFACE, 8)) {
                placedOnHealthy++;
                if (countState(healthyGround, SiftTerrain.HEALTHY_SCULK, 80, 255) > 0) {
                    healthyOnHealthy++;
                }
            }
            InMemoryRegion growthGround = valley(seed, SiftTerrain.SIFTSLATE_GROWTH);
            if (ARCH.place(growthGround, new Random(seed), 8, SURFACE, 8)) {
                placedOnGrowth++;
                if (countState(growthGround, SiftTerrain.SIFTSLATE_GROWTH, 80, 255) > 0) {
                    growthCoatOnGrowth++;
                }
                if (countPlants(growthGround, 80, 255) > 0) {
                    plantedCrowns++;
                }
            }
        }
        // 78% of arches over healthy sculk are healthy sculk; 78% over growth are siftslate with a growth coat.
        assertTrue(healthyOnHealthy > placedOnHealthy / 2, healthyOnHealthy + "/" + placedOnHealthy);
        assertTrue(growthCoatOnGrowth > placedOnGrowth / 2, growthCoatOnGrowth + "/" + placedOnGrowth);
        assertTrue(plantedCrowns > 0, "arch crowns should receive some plants");
    }

    @Test
    void archesNeedAValley() {
        InMemoryRegion flat = InMemoryRegion.flat(0, 0, SURFACE, 7);
        InMemoryRegion reference = InMemoryRegion.flat(0, 0, SURFACE, 7);
        assertFalse(ARCH.place(flat, new Random(7), 8, SURFACE, 8));
        assertEquals(0, flat.countDifferences(reference));

        // A shallow dip (depth 4 < 6) is still not a valley.
        IntBinaryOperator dip = (x, z) -> SURFACE + Math.min(4, Math.abs(x - 8) / 2);
        InMemoryRegion shallow = ground(7, 256, dip, SiftTerrain.SIFTSLATE_GROWTH, SiftTerrain.SIFTSLATE);
        InMemoryRegion shallowReference = ground(7, 256, dip, SiftTerrain.SIFTSLATE_GROWTH, SiftTerrain.SIFTSLATE);
        assertFalse(ARCH.place(shallow, new Random(7), 8, SURFACE, 8));
        assertEquals(0, shallow.countDifferences(shallowReference));
    }

    @Test
    void archesStayInsideTheRegionAtEveryChunkOffset() {
        for (int offset = 0; offset < 16; offset += 5) {
            for (long seed = 0; seed < 6; seed++) {
                InMemoryRegion region = valley(seed, SiftTerrain.SIFTSLATE_GROWTH);
                Random random = new Random(seed);
                ARCH.place(region, random, offset, SURFACE, 15 - offset);
                SPIKES.place(region, random, 15 - offset, SURFACE, offset);
                LUSH.place(region, random, offset, SURFACE, offset);
                assertEquals(0, region.rejectedWrites(), "offset " + offset + " seed " + seed);
            }
        }
    }

    // ---- shared ----------------------------------------------------------------------------

    @Test
    void featuresAreThreadSafe() {
        List<SiftFeature> features = List.of(BARE, LUSH, SPIKES, ARCH);
        List<Integer> sequential = new ArrayList<>();
        for (int index = 0; index < 32; index++) {
            sequential.add(checksum(features.get(index % 4), index));
        }
        List<Integer> parallel = IntStream.range(0, 32).parallel()
            .mapToObj(index -> checksum(features.get(index % 4), index)).toList();
        assertEquals(sequential, parallel);
    }

    private static int checksum(SiftFeature feature, int index) {
        InMemoryRegion region = valley(index, SiftTerrain.SIFTSLATE_GROWTH);
        feature.place(region, new Random(index), 8, SURFACE, 8);
        int hash = 17;
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                for (int y = SURFACE - 4; y < 140; y++) {
                    hash = hash * 31 + region.state(x, y, z).hashCode();
                }
            }
        }
        return hash;
    }

    /** A valley along the x axis (floor at x=8, rims 20 blocks higher after 13 blocks). */
    private static InMemoryRegion valley(long seed, BlockStateSpec top) {
        return ground(seed, 256, (x, z) -> SURFACE - 4 + (int) Math.min(20, Math.round(Math.abs(x - 8) * 1.5)),
            top, SiftTerrain.SIFTSLATE);
    }

    /** Terrain over chunk 0,0 (plus buffer) up to the given per-column surface (first free y). */
    private static InMemoryRegion ground(long seed, int maxY, IntBinaryOperator surface, BlockStateSpec top,
                                         BlockStateSpec fill) {
        InMemoryRegion region = new InMemoryRegion(0, 0, 0, maxY, seed);
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                int height = Math.min(surface.applyAsInt(x, z), maxY - 1);
                for (int y = 0; y < height - 1; y++) {
                    region.put(x, y, z, fill);
                }
                region.put(x, height - 1, z, top);
            }
        }
        return region;
    }

    private static void fillLayer(InMemoryRegion region, int y, BlockStateSpec state) {
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                region.put(x, y, z, state);
            }
        }
    }

    private static int highestSolid(InMemoryRegion region) {
        int top = Integer.MIN_VALUE;
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                top = Math.max(top, region.height(WorldRegion.Heightmap.WORLD_SURFACE_WG, x, z) - 1);
            }
        }
        return top;
    }

    private static int highestOf(InMemoryRegion region, BlockStateSpec state) {
        int top = Integer.MIN_VALUE;
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                for (int y = region.maxY() - 1; y >= region.minY(); y--) {
                    if (region.is(x, y, z, state)) {
                        top = Math.max(top, y);
                        break;
                    }
                }
            }
        }
        return top;
    }

    private static int countState(InMemoryRegion region, BlockStateSpec state, int fromY, int toY) {
        int count = 0;
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                for (int y = fromY; y <= toY; y++) {
                    if (region.is(x, y, z, state)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static int countPlants(InMemoryRegion region, int fromY, int toY) {
        int count = 0;
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                for (int y = fromY; y <= toY; y++) {
                    if (SiftTerrain.PLANT_TYPES.contains(region.blockId(x, y, z))) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /** Number of 4-connected groups of columns that have a non-air block at the given height. */
    private static int pillarCount(InMemoryRegion region, int y) {
        int width = region.maxX() - region.minX() + 1;
        int depth = region.maxZ() - region.minZ() + 1;
        boolean[][] filled = new boolean[width][depth];
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                filled[x][z] = !region.isAir(region.minX() + x, y, region.minZ() + z);
            }
        }
        int groups = 0;
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                if (!filled[x][z]) {
                    continue;
                }
                groups++;
                List<int[]> stack = new ArrayList<>();
                stack.add(new int[]{x, z});
                filled[x][z] = false;
                while (!stack.isEmpty()) {
                    int[] cell = stack.remove(stack.size() - 1);
                    for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int nx = cell[0] + step[0];
                        int nz = cell[1] + step[1];
                        if (nx >= 0 && nz >= 0 && nx < width && nz < depth && filled[nx][nz]) {
                            filled[nx][nz] = false;
                            stack.add(new int[]{nx, nz});
                        }
                    }
                }
            }
        }
        return groups;
    }
}
