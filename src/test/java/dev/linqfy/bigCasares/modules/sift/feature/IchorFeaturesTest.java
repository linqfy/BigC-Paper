package dev.linqfy.bigCasares.modules.sift.feature;

import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;
import dev.linqfy.bigCasares.modules.sift.SiftBlocks;
import dev.linqfy.bigCasares.modules.sift.SiftData;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Random;
import java.util.function.IntBinaryOperator;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ichor family: lakes, cave springs, snow and the lava floor, on small synthetic terrain. */
class IchorFeaturesTest {

    private static final SiftFeatures FEATURES = SiftFeatures.create(SiftData.fromClasspath());
    private static final BlockStateSpec FALLING_ICHOR = SiftTerrain.ICHOR.with("level", "8");
    private static final String SNOWY_PEAKS = "the_sift:ichor_snowy_peaks";
    private static final String[] HORIZONTAL = {"1,0", "-1,0", "0,1", "0,-1"};

    private static SiftFeature feature(String type) {
        return FEATURES.find(type).orElseThrow(() -> new AssertionError(type + " is not registered"));
    }

    /** Siftslate ground with a growth top layer whose surface height follows {@code height}. */
    private static InMemoryRegion terrain(long seed, IntBinaryOperator height) {
        InMemoryRegion region = new InMemoryRegion(0, 0, 0, 256, seed);
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                int surface = height.applyAsInt(x, z);
                for (int y = 0; y < surface - 1; y++) {
                    region.put(x, y, z, SiftTerrain.SIFTSLATE);
                }
                region.put(x, surface - 1, z, SiftTerrain.SIFTSLATE_GROWTH);
            }
        }
        return region;
    }

    private static int count(InMemoryRegion region, Predicate<BlockStateSpec> matches) {
        int count = 0;
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                for (int y = region.minY(); y < region.maxY(); y++) {
                    if (matches.test(region.state(x, y, z))) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static int ichor(InMemoryRegion region) {
        return count(region, state -> state.block().asString().equals("minecraft:water"));
    }

    /** Ichor blocks that touch air sideways, i.e. water that would pour out of its basin. */
    private static int leakingIchor(InMemoryRegion region) {
        int leaks = 0;
        for (int x = region.minX() + 1; x < region.maxX(); x++) {
            for (int z = region.minZ() + 1; z < region.maxZ(); z++) {
                for (int y = region.minY(); y < region.maxY(); y++) {
                    if (!region.blockId(x, y, z).equals("minecraft:water")) {
                        continue;
                    }
                    for (String offset : HORIZONTAL) {
                        String[] parts = offset.split(",");
                        if (region.isAir(x + Integer.parseInt(parts[0]), y, z + Integer.parseInt(parts[1]))) {
                            leaks++;
                            break;
                        }
                    }
                }
            }
        }
        return leaks;
    }

    /** Mean water depth of the lake columns within 3 blocks of the chunk centre ({@code centre}) or touching the shore. */
    private static double averageDepth(InMemoryRegion region, boolean centre) {
        double total = 0;
        int columns = 0;
        for (int x = region.minX() + 1; x < region.maxX(); x++) {
            for (int z = region.minZ() + 1; z < region.maxZ(); z++) {
                int depth = waterDepth(region, x, z);
                if (depth == 0) {
                    continue;
                }
                boolean shore = waterDepth(region, x + 1, z) == 0 || waterDepth(region, x - 1, z) == 0
                    || waterDepth(region, x, z + 1) == 0 || waterDepth(region, x, z - 1) == 0;
                boolean middle = Math.abs(x - 9) <= 3 && Math.abs(z - 9) <= 3;
                if (centre ? middle : shore) {
                    total += depth;
                    columns++;
                }
            }
        }
        return columns == 0 ? 0 : total / columns;
    }

    private static int waterDepth(InMemoryRegion region, int x, int z) {
        int depth = 0;
        for (int y = region.minY(); y < region.maxY(); y++) {
            if (region.blockId(x, y, z).equals("minecraft:water")) {
                depth++;
            }
        }
        return depth;
    }

    @Test
    void allIchorFeaturesAreRegistered() {
        for (String type : new String[]{"the_sift:ichor_lake", "the_sift:ichor_cave_spring", "the_sift:ichor_snow", "the_sift:lava_floor"}) {
            assertTrue(FEATURES.find(type).isPresent(), type);
        }
    }

    @Test
    void lakesFillBasinsWithIchorOnFlatGround() {
        SiftFeature lake = feature("the_sift:ichor_lake");
        int placed = 0;
        for (long seed = 0; seed < 12; seed++) {
            InMemoryRegion region = terrain(seed, (x, z) -> 70);
            if (!lake.place(region, new Random(seed), 8, 70, 8)) {
                continue;
            }
            placed++;
            assertTrue(ichor(region) >= 300, "lake holds " + ichor(region) + " ichor blocks, seed " + seed);
            assertEquals(0, leakingIchor(region), "ichor must be contained by its shore, seed " + seed);
            assertEquals(0, region.rejectedWrites(), "lake wrote outside the region, seed " + seed);
            // The basin is lined with Sift soil and the water is the water stand-in (level 0 source).
            assertTrue(count(region, state -> state.equals(SiftTerrain.ICHOR)) > 0);
        }
        assertTrue(placed >= 10, "lakes should place on flat Sift ground, placed " + placed + "/12");
    }

    @Test
    void lakesFollowGentleTerrainAndAreDeeperInTheMiddle() {
        SiftFeature lake = feature("the_sift:ichor_lake");
        int placed = 0;
        for (long seed = 0; seed < 24; seed++) {
            // A shallow bowl: the ground rises a block every five blocks away from the chunk centre.
            InMemoryRegion region = terrain(seed, (x, z) -> 66 + (int) (Math.sqrt((x - 9) * (x - 9) + (z - 9) * (z - 9)) / 5));
            if (!lake.place(region, new Random(seed), 8, 66, 8)) {
                continue;
            }
            placed++;
            assertEquals(0, region.rejectedWrites());
            assertEquals(0, leakingIchor(region), "seed " + seed);
            assertTrue(averageDepth(region, true) > averageDepth(region, false),
                "water is deeper near the centre than at the shore, seed " + seed);
        }
        assertTrue(placed >= 5, "lakes should place in a gentle bowl, placed " + placed + "/24");
    }

    @Test
    void lakesRefuseRuggedGroundAndPlaceNothing() {
        SiftFeature lake = feature("the_sift:ichor_lake");
        for (long seed = 0; seed < 8; seed++) {
            IntBinaryOperator rugged = (x, z) -> 70 + (((x * 7 + z * 13) & 3) * 3);
            InMemoryRegion region = terrain(seed, rugged);
            InMemoryRegion untouched = terrain(seed, rugged);
            assertFalse(lake.place(region, new Random(seed), 8, 70, 8));
            assertEquals(0, region.countDifferences(untouched));
        }
    }

    @Test
    void lakesNeedSiftTerrainUnderTheirCentre() {
        SiftFeature lake = feature("the_sift:ichor_lake");
        // Plain stone in the top twelve blocks: the mod only looks eight blocks down for Sift terrain.
        InMemoryRegion region = terrain(3, (x, z) -> 70);
        InMemoryRegion untouched = terrain(3, (x, z) -> 70);
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                for (int y = 58; y < 70; y++) {
                    region.put(x, y, z, BlockStateSpec.of("minecraft:stone"));
                    untouched.put(x, y, z, BlockStateSpec.of("minecraft:stone"));
                }
            }
        }
        assertFalse(lake.place(region, new Random(3), 8, 70, 8));
        assertEquals(0, region.countDifferences(untouched));
    }

    @Test
    void lakesDoNotOverlapAnExistingLakeNextToThem() {
        SiftFeature lake = feature("the_sift:ichor_lake");
        for (long seed = 0; seed < 6; seed++) {
            InMemoryRegion region = terrain(seed, (x, z) -> 70);
            if (!lake.place(region, new Random(seed), 8, 70, 8)) {
                continue;
            }
            int before = ichor(region);
            assertFalse(lake.place(region, new Random(seed + 100), 8, 70, 8), "a second lake on top of the first one is refused");
            assertEquals(before, ichor(region));
            return;
        }
        throw new AssertionError("no lake placed to test against");
    }

    @Test
    void cascadesPourFromAMountainPondIntoALowerLake() {
        SiftFeature lake = feature("the_sift:ichor_lake");
        int cascades = 0;
        for (long seed = 0; seed < 40; seed++) {
            InMemoryRegion region = terrain(seed, IchorFeaturesTest::cascadeTerrain);
            if (!lake.place(region, new Random(seed), 8, 90, 8)) {
                continue;
            }
            assertEquals(0, region.rejectedWrites(), "seed " + seed);
            int falling = count(region, FALLING_ICHOR::equals);
            assertTrue(falling > 0, "the cascade terrain only admits cascades, seed " + seed);
            cascades++;
            // The upper pond sits on the shelf (upper centre at (12, 96, 15)), the lower lake in the pit below it.
            assertEquals(SiftTerrain.ICHOR, region.state(12, 96, 15), "upper pond, seed " + seed);
            int lowerTop = -1;
            for (int y = 0; y < 96; y++) {
                if (region.blockId(6, y, 1).equals("minecraft:water")) {
                    lowerTop = y;
                }
            }
            assertTrue(lowerTop > 80 && lowerTop < 96, "lower lake below the shelf, seed " + seed + ", top " + lowerTop);
            // The falling columns hang from the pond (level 96) down to the lower lake's surface.
            int highest = -1;
            int lowest = Integer.MAX_VALUE;
            for (int x = region.minX(); x <= region.maxX(); x++) {
                for (int z = region.minZ(); z <= region.maxZ(); z++) {
                    for (int y = 0; y < 256; y++) {
                        if (region.state(x, y, z).equals(FALLING_ICHOR)) {
                            highest = Math.max(highest, y);
                            lowest = Math.min(lowest, y);
                        }
                    }
                }
            }
            assertEquals(95, highest, "falling ichor starts right below the pond, seed " + seed);
            // The basin's ground is at surface 92, so its water surface is around Y 90-92.
            assertTrue(lowest >= 85 && lowest <= 92, "falling ichor reaches the lower lake, lowest " + lowest + ", seed " + seed);
        }
        assertTrue(cascades >= 10, "cascades should be placed on a plateau above a dipped lower basin, placed " + cascades + "/40");
    }

    /**
     * The one shape a cascade accepts: a plateau at surface 97 stepping down to a basin at 92 (about forward = 247.5
     * degrees from the chunk centre, upper site (12, 15), lower site (6, 1)), with a pit in the basin's centre so the
     * drop between the two sites is at least ten while the lake and its shore stay flat.
     */
    private static int cascadeTerrain(int x, int z) {
        double forwardX = Math.cos(11 * Math.PI * 2 / 16);
        double forwardZ = Math.sin(11 * Math.PI * 2 / 16);
        if (Math.abs(x - 6) + Math.abs(z - 1) <= 1) {
            return 87;
        }
        double s = (x - 9) * forwardX + (z - 9) * forwardZ;
        if (s <= -7) {
            return 97;
        }
        if (s >= -2) {
            return 92;
        }
        return (int) Math.round(97 + (92 - 97) * (s + 7) / 5.0);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Cave springs

    /** Solid siftslate up to y 40 with the surface at 41, so openings below y 40 count as buried. */
    private static InMemoryRegion buriedRock(long seed) {
        return terrain(seed, (x, z) -> 41);
    }

    private static void carve(InMemoryRegion region, int x, int y, int z) {
        region.set(x, y, z, SiftTerrain.AIR);
    }

    @Test
    void springsOpenWhereExactlyOneSideOfAWallBlockIsAHole() {
        SiftFeature spring = feature("the_sift:ichor_cave_spring");
        for (long seed = 0; seed < 5; seed++) {
            InMemoryRegion region = buriedRock(seed);
            for (int z = 0; z <= 15; z++) {
                carve(region, 8, 30, z);
            }
            assertTrue(spring.place(region, new Random(seed), 9, 30, 8), "seed " + seed);
            assertEquals(SiftTerrain.ICHOR, region.state(9, 30, 8));
            assertEquals(1, ichor(region));
            assertEquals(0, region.rejectedWrites());
        }
    }

    @Test
    void springsAlsoReplaceAnExistingHoleOnTheWallLine() {
        SiftFeature spring = feature("the_sift:ichor_cave_spring");
        InMemoryRegion region = buriedRock(1);
        // The origin itself is air with rock above, below and on three sides: a dead-end nook.
        carve(region, 9, 30, 8);
        carve(region, 8, 30, 8);
        assertTrue(spring.place(region, new Random(1), 9, 30, 8));
        assertEquals(SiftTerrain.ICHOR, region.state(9, 30, 8));
    }

    @Test
    void springsRefuseWallsWithTwoHolesOrNone() {
        SiftFeature spring = feature("the_sift:ichor_cave_spring");
        InMemoryRegion twoHoles = buriedRock(2);
        carve(twoHoles, 8, 30, 8);
        carve(twoHoles, 10, 30, 8);
        assertFalse(spring.place(twoHoles, new Random(2), 9, 30, 8));
        assertEquals(0, ichor(twoHoles));

        InMemoryRegion solid = buriedRock(3);
        assertFalse(solid.isAir(9, 30, 8));
        assertFalse(spring.place(solid, new Random(3), 9, 30, 8));
        assertEquals(0, ichor(solid));
    }

    @Test
    void springsRefuseHolesOpenToTheSurface() {
        SiftFeature spring = feature("the_sift:ichor_cave_spring");
        InMemoryRegion region = buriedRock(4);
        // A hole at y 40, directly below the surface at 41: not buried (opening.y < surface - 1 is false).
        carve(region, 8, 40, 8);
        assertFalse(spring.place(region, new Random(4), 9, 40, 8));
        // The same hole one block lower is buried.
        InMemoryRegion buried = buriedRock(4);
        carve(buried, 8, 39, 8);
        assertTrue(spring.place(buried, new Random(4), 9, 39, 8));
    }

    @Test
    void springsNeedSiftRockAboveAndBelow() {
        SiftFeature spring = feature("the_sift:ichor_cave_spring");
        InMemoryRegion region = buriedRock(5);
        carve(region, 8, 30, 8);
        region.put(9, 31, 8, BlockStateSpec.of("minecraft:stone"));
        assertFalse(spring.place(region, new Random(5), 9, 30, 8));
        assertEquals(0, ichor(region));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Lava floor

    @Test
    void lavaFloorFillsOnlyTheGeneratingChunksLowCaves() {
        SiftFeature lava = feature("the_sift:lava_floor");
        for (long seed = 0; seed < 3; seed++) {
            InMemoryRegion region = buriedRock(seed);
            // A low pocket and a high pocket inside the chunk, and a low pocket in the neighbouring chunk.
            for (int y = 3; y <= 8; y++) {
                carve(region, 4, y, 4);
                carve(region, 5, y, 4);
            }
            carve(region, 6, 11, 6);
            carve(region, 6, 12, 6);
            carve(region, 7, 20, 7);
            carve(region, -4, 5, 4);
            assertTrue(lava.place(region, new Random(seed), 4, 5, 4));
            for (int y = 3; y <= 8; y++) {
                assertEquals(SiftTerrain.LAVA, region.state(4, y, 4));
                assertEquals(SiftTerrain.LAVA, region.state(5, y, 4));
            }
            assertEquals(SiftTerrain.LAVA, region.state(6, 11, 6), "Y 11 is the lava surface");
            assertTrue(region.isAir(6, 12, 6), "above Y 11 stays air");
            assertTrue(region.isAir(7, 20, 7), "caves above the floor stay dry");
            assertTrue(region.isAir(-4, 5, 4), "other chunks are generated by their own feature call");
            assertEquals(13, count(region, SiftTerrain.LAVA::equals));
            assertEquals(0, region.rejectedWrites());
        }
    }

    @Test
    void lavaFloorReportsNothingPlacedInSolidRock() {
        InMemoryRegion region = buriedRock(1);
        InMemoryRegion untouched = buriedRock(1);
        assertFalse(feature("the_sift:lava_floor").place(region, new Random(1), 0, 5, 0));
        assertEquals(0, region.countDifferences(untouched));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Snow

    private static InMemoryRegion snowyPeaks(long seed, int surface) {
        return terrain(seed, (x, z) -> surface).biomes(position -> SNOWY_PEAKS);
    }

    private static BlockStateSpec snow(int layers) {
        return SiftBlocks.remap("ichor_snow", Map.of("layers", Integer.toString(layers))).orElseThrow();
    }

    private static boolean isSnowLayer(BlockStateSpec state) {
        return state.equals(snow(1)) || state.equals(snow(2));
    }

    @Test
    void snowCoversTheGeneratingChunkOfTheSnowyPeaks() {
        SiftFeature snow = feature("the_sift:ichor_snow");
        for (long seed = 0; seed < 4; seed++) {
            InMemoryRegion region = snowyPeaks(seed, 90);
            assertTrue(snow.place(region, new Random(seed), 0, 0, 0), "seed " + seed);
            int layerOne = 0;
            int layerTwo = 0;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    BlockStateSpec top = region.state(x, 90, z);
                    assertTrue(isSnowLayer(top), "snow at " + x + "," + z + ": " + top);
                    if (top.equals(snow(2))) {
                        layerTwo++;
                    } else {
                        layerOne++;
                    }
                    assertEquals(SiftTerrain.SIFTSLATE_GROWTH_SNOWY, region.state(x, 89, z), "growth under snow turns snowy");
                }
            }
            assertTrue(layerOne > 0, "most of the cover is a single layer");
            assertEquals(256, layerOne + layerTwo);
            // Neighbouring chunks are covered by their own call.
            assertTrue(region.isAir(-1, 90, 0));
            assertTrue(region.isAir(16, 90, 5));
            assertEquals(0, region.rejectedWrites());
        }
    }

    /** Found on a live server: the populator's heightmap pointed above the peaks, so none got snow. */
    @Test
    void snowFindsTheGroundUnderAStaleHeightmap() {
        SiftFeature snow = feature("the_sift:ichor_snow");
        for (int stale : new int[]{1, 4}) {
            InMemoryRegion region = snowyPeaks(3, 90).withStaleMotionHeight(stale);
            assertTrue(snow.place(region, new Random(3), 0, 0, 0), "stale by " + stale);
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    assertTrue(isSnowLayer(region.state(x, 90, z)), "stale by " + stale + ": no snow at " + x + "," + z);
                }
            }
        }
    }

    @Test
    void snowDependsOnlyOnTheWorldSeedAndColumn() {
        SiftFeature snow = feature("the_sift:ichor_snow");
        InMemoryRegion first = snowyPeaks(7, 90);
        InMemoryRegion second = snowyPeaks(7, 90);
        InMemoryRegion otherSeed = snowyPeaks(8, 90);
        snow.place(first, new Random(1), 0, 0, 0);
        snow.place(second, new Random(999), 0, 5, 0);
        snow.place(otherSeed, new Random(1), 0, 0, 0);
        assertEquals(0, first.countDifferences(second), "the random argument does not matter");
        assertNotEquals(0, first.countDifferences(otherSeed), "the thickness pattern follows the world seed");
    }

    @Test
    void snowStaysOutOfOtherBiomesAndOffFluids() {
        SiftFeature snow = feature("the_sift:ichor_snow");
        InMemoryRegion forest = terrain(1, (x, z) -> 90);
        InMemoryRegion untouched = terrain(1, (x, z) -> 90);
        assertFalse(snow.place(forest, new Random(1), 0, 0, 0));
        assertEquals(0, forest.countDifferences(untouched));

        InMemoryRegion pond = snowyPeaks(2, 90);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                pond.put(x, 89, z, SiftTerrain.ICHOR);
            }
        }
        assertFalse(snow.place(pond, new Random(2), 0, 0, 0), "water surfaces are not natural mountain surface");
    }

    @Test
    void snowReplacesLowVegetationAndTopsWillowCanopies() {
        SiftFeature snow = feature("the_sift:ichor_snow");
        InMemoryRegion region = snowyPeaks(3, 90);
        region.put(3, 90, 3, BlockStateSpec.of("minecraft:short_grass"));
        region.put(4, 90, 3, BlockStateSpec.of("minecraft:fern"));
        region.put(5, 90, 5, SiftTerrain.OVERGROWN_WILLOW_FOLIAGE);
        region.put(5, 91, 5, SiftTerrain.OVERGROWN_WILLOW_FOLIAGE);
        region.put(8, 90, 8, SiftTerrain.OVERGROWN_WILLOW_LOG);
        assertTrue(snow.place(region, new Random(3), 0, 0, 0));
        assertTrue(isSnowLayer(region.state(3, 90, 3)), "short grass is replaced by snow");
        assertTrue(isSnowLayer(region.state(4, 90, 3)), "ferns are replaced by snow");
        assertEquals(SiftTerrain.OVERGROWN_WILLOW_FOLIAGE, region.state(5, 91, 5));
        assertTrue(isSnowLayer(region.state(5, 92, 5)), "the canopy top carries snow");
        assertEquals(SiftTerrain.OVERGROWN_WILLOW_FOLIAGE, region.state(5, 90, 5), "foliage below the top is untouched");
        assertTrue(isSnowLayer(region.state(8, 91, 8)), "a log top carries snow");
        assertEquals(SiftTerrain.OVERGROWN_WILLOW_LOG, region.state(8, 90, 8));
        assertEquals(0, region.rejectedWrites());
    }
}
