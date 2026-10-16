package dev.linqfy.bigCasares.modules.sift.feature;

import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;
import dev.linqfy.bigCasares.modules.sift.SiftData;
import dev.linqfy.bigCasares.modules.sift.feature.SiftLandmarkPlacement.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The soul canyon, the covered-growth cleanup and the two plant patches: each feature
 * changes suitable terrain according to the mod's rules, leaves everything else
 * alone and never writes outside its region.
 */
class CanyonAndPatchFeaturesTest {

    private static final SiftFeatures FEATURES = SiftFeatures.create(SiftData.fromClasspath());
    private static final int SURFACE = 64;
    private static final BlockStateSpec ICHOR_SNOW = SiftTerrain.block("ichor_snow");
    private static final Set<String> FLOWER_TYPES = Set.of("minecraft:cornflower", "minecraft:torchflower",
        "minecraft:lily_of_the_valley", "minecraft:wildflowers");

    private record Change(int x, int y, int z, BlockStateSpec before, BlockStateSpec after) {
    }

    private static SiftFeature feature(String type) {
        return FEATURES.find(type).orElseThrow(() -> new AssertionError(type + " is not registered"));
    }

    private static ChunkPos selectedCanyonChunk(long seed) {
        return SiftLandmarkPlacement.candidateChunk(seed, 0, 0, SiftLandmarkPlacement.SOUL_CANYON_CELL_CHUNKS,
            SiftLandmarkPlacement.SOUL_CANYON_SALT, 0);
    }

    private static boolean place(String type, InMemoryRegion region, long seed, int chunkX, int chunkZ) {
        return feature(type).place(region, new Random(seed), chunkX << 4, 0, chunkZ << 4);
    }

    private static List<Change> changes(InMemoryRegion before, InMemoryRegion after) {
        return changes(before, after, after.minX(), after.maxX(), after.minZ(), after.maxZ());
    }

    /** Changed blocks in a column window (the tests only build below y=96, which keeps the scan cheap). */
    private static List<Change> changes(InMemoryRegion before, InMemoryRegion after, int minX, int maxX, int minZ, int maxZ) {
        List<Change> changes = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = after.minY(); y < Math.min(after.maxY(), 96); y++) {
                    BlockStateSpec was = before.state(x, y, z);
                    BlockStateSpec now = after.state(x, y, z);
                    if (!was.equals(now)) {
                        changes.add(new Change(x, y, z, was, now));
                    }
                }
            }
        }
        return changes;
    }

    private static void fillSolid(InMemoryRegion region, int y, BlockStateSpec state) {
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                region.put(x, y, z, state);
            }
        }
    }

    // ---- soul canyon --------------------------------------------------------------------------------------

    @Test
    void soulCanyonCarvesWalkableSoulFilledCanyonsInSelectedChunks() {
        int carved = 0;
        for (long seed = 1; seed <= 24; seed++) {
            ChunkPos chunk = selectedCanyonChunk(seed);
            InMemoryRegion region = InMemoryRegion.flat(chunk.x(), chunk.z(), SURFACE, seed);
            InMemoryRegion untouched = InMemoryRegion.flat(chunk.x(), chunk.z(), SURFACE, seed);
            boolean placed = place("the_sift:soul_canyon", region, seed, chunk.x(), chunk.z());
            assertEquals(0, region.rejectedWrites(), "canyon wrote outside its region for seed " + seed);
            assertEquals(placed, region.countDifferences(untouched) > 0, "return value must match the change for seed " + seed);
            if (placed) {
                carved++;
                assertCanyon(region, untouched, seed);
            }
        }
        assertTrue(carved >= 20, "a canyon should fit on flat Sift ground for nearly every seed, carved " + carved);
    }

    private static void assertCanyon(InMemoryRegion region, InMemoryRegion untouched, long seed) {
        List<Change> changes = changes(untouched, region);
        int soulBlocks = 0;
        int deepest = SURFACE;
        for (Change change : changes) {
            assertTrue(region.isWritable(change.x(), change.y(), change.z()), "change escaped the region, seed " + seed);
            if (change.after().equals(SiftTerrain.SOUL_BLOCK)) {
                soulBlocks++;
                // Soul blocks are the floor of the walkable canyon: three free blocks above.
                for (int dy = 1; dy <= 3; dy++) {
                    assertTrue(region.isAir(change.x(), change.y() + dy, change.z()),
                        "soul block without headroom at " + change.x() + "," + change.y() + "," + change.z() + " seed " + seed);
                }
            }
            if (change.after().equals(SiftTerrain.AIR)) {
                deepest = Math.min(deepest, change.y());
            }
        }
        Set<Long> walkable = walkableFeet(region);
        for (Change change : changes) {
            if (change.after().equals(SiftTerrain.SOUL_BLOCK)) {
                assertTrue(walkable.contains(cell(change.x(), change.y() + 1, change.z())),
                    "soul block not reachable on foot from the surface at " + change.x() + "," + change.y() + "," + change.z()
                        + " seed " + seed);
            }
        }
        assertTrue(soulBlocks >= 8, "soul canyon needs at least eight soul blocks, got " + soulBlocks + " for seed " + seed);
        assertTrue(deepest <= SURFACE - 14, "canyon should reach deep below the surface, lowest air " + deepest + " seed " + seed);
        // Only carving and soul blocks: air replaces ground or plants, siftslate fills the floor.
        for (Change change : changes) {
            BlockStateSpec after = change.after();
            assertTrue(after.equals(SiftTerrain.AIR) || after.equals(SiftTerrain.SOUL_BLOCK) || after.equals(SiftTerrain.SIFTSLATE),
                "unexpected canyon block " + after + " seed " + seed);
        }
    }

    private static long cell(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private static boolean standable(InMemoryRegion region, int x, int y, int z) {
        return region.isAir(x, y, z) && region.isAir(x, y + 1, z) && region.isSolid(x, y - 1, z);
    }

    /**
     * Feet positions a one-block-wide walker reaches from the surface in the region's corner,
     * stepping at most one block up or down per move and needing two blocks of headroom.
     */
    private static Set<Long> walkableFeet(InMemoryRegion region) {
        Set<Long> visited = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        int startX = region.minX() + 1;
        int startZ = region.minZ() + 1;
        queue.add(new int[]{startX, SURFACE, startZ});
        visited.add(cell(startX, SURFACE, startZ));
        int[][] horizontal = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] current = queue.removeFirst();
            for (int[] direction : horizontal) {
                int x = current[0] + direction[0];
                int z = current[2] + direction[1];
                if (!region.isWritable(x, SURFACE, z)) {
                    continue;
                }
                for (int dy = 1; dy >= -1; dy--) {
                    int y = current[1] + dy;
                    // Climbing one block also needs headroom above the block we leave.
                    if (standable(region, x, y, z) && (dy <= 0 || region.isAir(current[0], current[1] + 2, current[2]))
                        && visited.add(cell(x, y, z))) {
                        queue.add(new int[]{x, y, z});
                        break;
                    }
                }
            }
        }
        return visited;
    }

    @Test
    void soulCanyonIsDeterministic() {
        for (long seed = 1; seed <= 6; seed++) {
            ChunkPos chunk = selectedCanyonChunk(seed);
            InMemoryRegion first = InMemoryRegion.flat(chunk.x(), chunk.z(), SURFACE, seed);
            InMemoryRegion second = InMemoryRegion.flat(chunk.x(), chunk.z(), SURFACE, seed);
            assertEquals(place("the_sift:soul_canyon", first, seed, chunk.x(), chunk.z()),
                place("the_sift:soul_canyon", second, seed, chunk.x(), chunk.z()));
            assertEquals(first.snapshot(), second.snapshot(), "seed " + seed);
        }
    }

    @Test
    void soulCanyonOnlyAppearsInTheSeedSelectedChunk() {
        for (long seed = 1; seed <= 6; seed++) {
            ChunkPos chunk = selectedCanyonChunk(seed);
            assertTrue(SiftLandmarkPlacement.isSelectedChunk(seed, chunk.x() << 4, chunk.z() << 4,
                SiftLandmarkPlacement.SOUL_CANYON_CELL_CHUNKS, SiftLandmarkPlacement.SOUL_CANYON_SALT, 1));
            for (int[] offset : new int[][]{{1, 0}, {0, 1}, {-1, -1}, {2, 0}}) {
                int chunkX = chunk.x() + offset[0];
                int chunkZ = chunk.z() + offset[1];
                InMemoryRegion region = InMemoryRegion.flat(chunkX, chunkZ, SURFACE, seed);
                InMemoryRegion untouched = InMemoryRegion.flat(chunkX, chunkZ, SURFACE, seed);
                assertFalse(place("the_sift:soul_canyon", region, seed, chunkX, chunkZ), "seed " + seed);
                assertEquals(0, region.countDifferences(untouched), "seed " + seed);
            }
        }
    }

    @Test
    void soulCanyonNeedsSiftGround() {
        long seed = 3;
        ChunkPos chunk = selectedCanyonChunk(seed);
        InMemoryRegion stone = new InMemoryRegion(chunk.x(), chunk.z(), 0, 256, seed);
        for (int y = 0; y < SURFACE; y++) {
            fillSolid(stone, y, BlockStateSpec.of("minecraft:stone"));
        }
        InMemoryRegion untouched = new InMemoryRegion(chunk.x(), chunk.z(), 0, 256, seed);
        for (int y = 0; y < SURFACE; y++) {
            fillSolid(untouched, y, BlockStateSpec.of("minecraft:stone"));
        }
        assertFalse(place("the_sift:soul_canyon", stone, seed, chunk.x(), chunk.z()));
        assertEquals(0, stone.countDifferences(untouched));
    }

    @Test
    void soulCanyonRefusesTerrainItCannotReplace() {
        // A layer of something that is neither ground nor replaceable on top of the Sift ground.
        long seed = 5;
        ChunkPos chunk = selectedCanyonChunk(seed);
        InMemoryRegion region = InMemoryRegion.flat(chunk.x(), chunk.z(), SURFACE, seed);
        InMemoryRegion untouched = InMemoryRegion.flat(chunk.x(), chunk.z(), SURFACE, seed);
        fillSolid(region, SURFACE, BlockStateSpec.of("minecraft:oak_log"));
        fillSolid(untouched, SURFACE, BlockStateSpec.of("minecraft:oak_log"));
        assertFalse(place("the_sift:soul_canyon", region, seed, chunk.x(), chunk.z()));
        assertEquals(0, region.countDifferences(untouched));
    }

    @Test
    void soulCanyonTerrainHelpersFollowTheMod() {
        assertTrue(SoulCanyonTerrain.isGround(SiftTerrain.SIFT_RUBBLE));
        assertTrue(SoulCanyonTerrain.isGround(SiftTerrain.SIFTSLATE_GROWTH_SNOWY));
        assertTrue(SoulCanyonTerrain.isGround(SiftTerrain.block("siftslate_diamond_ore")));
        assertTrue(SoulCanyonTerrain.isGround(BlockStateSpec.of("minecraft:bone_block", Map.of("axis", "x"))));
        assertFalse(SoulCanyonTerrain.isGround(SiftTerrain.SOUL_BLOCK));
        assertFalse(SoulCanyonTerrain.isGround(BlockStateSpec.of("minecraft:stone")));

        InMemoryRegion region = InMemoryRegion.flat(0, 0, SURFACE, 1);
        region.put(4, SURFACE, 4, SiftTerrain.block("overgrown_fronds"));
        region.put(5, SURFACE, 5, BlockStateSpec.of("minecraft:cornflower"));
        region.put(6, SURFACE, 6, ICHOR_SNOW);
        assertEquals(SURFACE - 1, SoulCanyonTerrain.surfaceBlockY(region, 3, 3));
        assertEquals(SURFACE - 1, SoulCanyonTerrain.surfaceBlockY(region, 4, 4), "plants are looked through");
        assertEquals(SURFACE - 1, SoulCanyonTerrain.surfaceBlockY(region, 6, 6), "ichor snow is looked through");
        assertEquals(SoulCanyonTerrain.NO_SURFACE, SoulCanyonTerrain.surfaceBlockY(region, 5, 5), "flowers are not replaceable");
        assertEquals(SoulCanyonTerrain.NO_SURFACE, SoulCanyonTerrain.surfaceBlockY(region, 500, 500), "outside the region");
    }

    // ---- covered growth cleanup -----------------------------------------------------------------------------

    @Test
    void cleanupLeavesHealthyTerrainAlone() {
        InMemoryRegion region = InMemoryRegion.flat(0, 0, SURFACE, 1);
        InMemoryRegion untouched = InMemoryRegion.flat(0, 0, SURFACE, 1);
        assertFalse(place("the_sift:covered_growth_cleanup", region, 1, 0, 0));
        assertEquals(0, region.countDifferences(untouched));
    }

    @Test
    void cleanupAppliesTheModsGrowthAndLakePlantRules() {
        InMemoryRegion region = InMemoryRegion.flat(0, 0, SURFACE, 1);
        int top = SURFACE - 1;
        BlockStateSpec fern = SiftTerrain.block("overgrown_chard");
        BlockStateSpec sprouts = SiftTerrain.block("dry_healthy_sculk_sprouts");

        // 2: growth under a full block turns back into siftslate.
        region.put(2, SURFACE, 2, SiftTerrain.SIFTSLATE);
        // 3: dry growth under a full block turns into dry healthy sculk.
        region.put(3, top, 2, SiftTerrain.DRY_HEALTHY_SCULK_GROWTH);
        region.put(3, SURFACE, 2, SiftTerrain.SIFTSLATE);
        // 4: growth over dry sculk under a full block also becomes dry sculk.
        region.put(4, top - 1, 2, SiftTerrain.DRY_HEALTHY_SCULK);
        region.put(4, SURFACE, 2, SiftTerrain.SIFTSLATE);
        // 5: uncovered growth over dry sculk becomes dry growth.
        region.put(5, top - 1, 2, SiftTerrain.DRY_HEALTHY_SCULK);
        // 6, 7: ichor snow (layer or block) makes the growth snowy instead of reverting it.
        region.put(6, SURFACE, 2, ICHOR_SNOW);
        region.put(7, SURFACE, 2, SiftTerrain.ICHOR_SNOW_BLOCK);
        // 8: snowy growth without a snow cover is reset.
        region.put(8, top, 2, SiftTerrain.SIFTSLATE_GROWTH_SNOWY);
        // 10: a lake plant over open air above ichor is removed.
        region.put(10, top, 2, SiftTerrain.AIR);
        region.put(10, top - 1, 2, SiftTerrain.ICHOR);
        region.put(10, SURFACE, 2, fern);
        // 11: a supported plant stays.
        region.put(11, SURFACE, 2, fern);
        // 12: only ichor counts, not lava.
        region.put(12, top, 2, SiftTerrain.AIR);
        region.put(12, top - 1, 2, SiftTerrain.LAVA);
        region.put(12, SURFACE, 2, fern);
        // 13: ichor seven blocks below (past six air blocks) is out of reach.
        for (int y = top - 5; y <= top; y++) {
            region.put(13, y, 2, SiftTerrain.AIR);
        }
        region.put(13, top - 6, 2, SiftTerrain.ICHOR);
        region.put(13, SURFACE, 2, sprouts);
        // 14: a lake plant above a solid block is kept even with ichor further down.
        region.put(14, top - 1, 2, SiftTerrain.ICHOR);
        region.put(14, SURFACE, 2, fern);
        // Outside the chunk the cleanup does nothing: a covered growth in the neighbour chunk stays.
        region.put(-5, SURFACE, 2, SiftTerrain.SIFTSLATE);
        InMemoryRegion before = copyOf(region);

        assertTrue(place("the_sift:covered_growth_cleanup", region, 1, 0, 0));
        assertEquals(0, region.rejectedWrites());

        assertEquals(SiftTerrain.SIFTSLATE, region.state(2, top, 2));
        assertEquals(SiftTerrain.DRY_HEALTHY_SCULK, region.state(3, top, 2));
        assertEquals(SiftTerrain.DRY_HEALTHY_SCULK, region.state(4, top, 2));
        assertEquals(SiftTerrain.DRY_HEALTHY_SCULK_GROWTH, region.state(5, top, 2));
        assertEquals(SiftTerrain.SIFTSLATE_GROWTH_SNOWY, region.state(6, top, 2));
        assertEquals(SiftTerrain.SIFTSLATE_GROWTH_SNOWY, region.state(7, top, 2));
        assertEquals(SiftTerrain.SIFTSLATE_GROWTH, region.state(8, top, 2));
        assertEquals(SiftTerrain.AIR, region.state(10, SURFACE, 2));
        assertEquals(fern, region.state(11, SURFACE, 2));
        assertEquals(fern, region.state(12, SURFACE, 2));
        assertEquals(sprouts, region.state(13, SURFACE, 2));
        assertEquals(fern, region.state(14, SURFACE, 2));
        assertEquals(SiftTerrain.SIFTSLATE_GROWTH, region.state(-5, top, 2));
        assertEquals(SiftTerrain.SIFTSLATE, region.state(-5, SURFACE, 2));

        // The pass only touches what it should: columns 2-8 and the removed plant at 10, nothing else.
        assertEquals(8, changes(before, region).size());
        // A second pass is a no-op.
        assertFalse(place("the_sift:covered_growth_cleanup", region, 1, 0, 0));
    }

    private static InMemoryRegion copyOf(InMemoryRegion source) {
        InMemoryRegion copy = new InMemoryRegion(0, 0, source.minY(), source.maxY(), 1);
        for (int x = source.minX(); x <= source.maxX(); x++) {
            for (int z = source.minZ(); z <= source.maxZ(); z++) {
                for (int y = source.minY(); y < source.maxY(); y++) {
                    BlockStateSpec state = source.state(x, y, z);
                    if (!state.equals(SiftTerrain.AIR)) {
                        copy.put(x, y, z, state);
                    }
                }
            }
        }
        return copy;
    }

    // ---- sift flower patch ----------------------------------------------------------------------------------

    @Test
    void flowerPatchPlantsOneSpeciesNearTheChunkCentre() {
        for (long seed = 0; seed < 40; seed++) {
            InMemoryRegion region = InMemoryRegion.flat(0, 0, SURFACE, seed);
            InMemoryRegion untouched = InMemoryRegion.flat(0, 0, SURFACE, seed);
            assertTrue(place("the_sift:sift_flower_patch", region, seed, 0, 0), "seed " + seed);
            assertEquals(0, region.rejectedWrites());
            List<Change> changes = changes(untouched, region);
            assertTrue(changes.size() >= 3 && changes.size() <= 4, "3-4 flowers, got " + changes.size() + " for seed " + seed);
            Set<String> species = new HashSet<>();
            for (Change change : changes) {
                assertEquals(SURFACE, change.y());
                assertTrue(Math.abs(change.x() - 8) <= 3 && Math.abs(change.z() - 8) <= 3, "flower too far from the centre");
                assertTrue(change.before().equals(SiftTerrain.AIR), "flowers go into free space");
                assertTrue(FLOWER_TYPES.contains(change.after().block().asString()), "unexpected " + change.after());
                species.add(change.after().block().asString());
            }
            assertEquals(1, species.size(), "one species per patch, seed " + seed);
        }
    }

    @Test
    void flowerPatchFavoursWildflowersInOvergrownBiomes() {
        int overgrownWild = wildflowerPatches("the_sift:overgrown_forest");
        int wastesWild = wildflowerPatches("the_sift:siftslate_wastes");
        assertTrue(overgrownWild >= 30 && overgrownWild <= 70, "about half of the overgrown patches, got " + overgrownWild + "/100");
        assertTrue(wastesWild <= 28, "about one in eight elsewhere, got " + wastesWild + "/100");
        assertTrue(overgrownWild > wastesWild * 2);
    }

    /** Runs 100 patches on fresh flat terrain with one random stream and counts the wildflower ones. */
    private static int wildflowerPatches(String biome) {
        Random random = new Random(2024);
        int wild = 0;
        for (int run = 0; run < 100; run++) {
            InMemoryRegion region = InMemoryRegion.flat(0, 0, SURFACE, run).biomes(position -> biome);
            feature("the_sift:sift_flower_patch").place(region, random, 0, 0, 0);
            List<BlockStateSpec> flowers = new ArrayList<>();
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    if (!region.isAir(x, SURFACE, z)) {
                        flowers.add(region.state(x, SURFACE, z));
                    }
                }
            }
            if (!flowers.isEmpty() && flowers.get(0).block().asString().equals("minecraft:wildflowers")) {
                wild++;
                for (BlockStateSpec flower : flowers) {
                    assertEquals("minecraft:wildflowers", flower.block().asString());
                    assertTrue(Set.of("north", "south", "east", "west").contains(flower.properties().get("facing")));
                    int amount = Integer.parseInt(flower.properties().get("flower_amount"));
                    assertTrue(amount >= 1 && amount <= 4);
                }
            }
        }
        return wild;
    }

    @Test
    void flowerPatchOnlyGrowsOnFlowerSoil() {
        InMemoryRegion bare = InMemoryRegion.flat(0, 0, SURFACE, 1);
        // Cobbled siftslate is not flower soil.
        fillSolid(bare, SURFACE - 1, SiftTerrain.COBBLED_SIFTSLATE);
        InMemoryRegion untouched = InMemoryRegion.flat(0, 0, SURFACE, 1);
        fillSolid(untouched, SURFACE - 1, SiftTerrain.COBBLED_SIFTSLATE);
        for (long seed = 0; seed < 10; seed++) {
            assertFalse(place("the_sift:sift_flower_patch", bare, seed, 0, 0));
        }
        assertEquals(0, bare.countDifferences(untouched));

        // Healthy sculk and dry sculk are flower soil, growth and siftslate too.
        for (BlockStateSpec soil : List.of(SiftTerrain.SIFTSLATE, SiftTerrain.HEALTHY_SCULK, SiftTerrain.DRY_HEALTHY_SCULK,
            SiftTerrain.SIFTSLATE_GROWTH_SNOWY)) {
            InMemoryRegion region = InMemoryRegion.flat(0, 0, SURFACE, 1);
            fillSolid(region, SURFACE - 1, soil);
            assertTrue(place("the_sift:sift_flower_patch", region, 2, 0, 0), soil.asString());
        }
    }

    // ---- siftslate plant patch ------------------------------------------------------------------------------

    @Test
    void plantPatchOnlyGrowsOnBareSiftslate() {
        // The default flat terrain is siftslate growth: no plants.
        InMemoryRegion growth = InMemoryRegion.flat(0, 0, SURFACE, 1);
        InMemoryRegion untouched = InMemoryRegion.flat(0, 0, SURFACE, 1);
        for (long seed = 0; seed < 10; seed++) {
            assertFalse(place("the_sift:siftslate_plant_patch", growth, seed, 0, 0));
        }
        assertEquals(0, growth.countDifferences(untouched));

        Set<String> types = new HashSet<>();
        for (long seed = 0; seed < 60; seed++) {
            InMemoryRegion region = InMemoryRegion.flat(0, 0, SURFACE, seed);
            fillSolid(region, SURFACE - 1, SiftTerrain.SIFTSLATE);
            InMemoryRegion bare = InMemoryRegion.flat(0, 0, SURFACE, seed);
            fillSolid(bare, SURFACE - 1, SiftTerrain.SIFTSLATE);
            assertTrue(place("the_sift:siftslate_plant_patch", region, seed, 0, 0), "seed " + seed);
            assertEquals(0, region.rejectedWrites());
            List<Change> changes = changes(bare, region);
            assertTrue(changes.size() >= 4 && changes.size() <= 7, "4-7 plants, got " + changes.size() + " for seed " + seed);
            Set<BlockStateSpec> species = new HashSet<>();
            for (Change change : changes) {
                assertEquals(SURFACE, change.y());
                assertTrue(Math.abs(change.x() - 8) <= 4 && Math.abs(change.z() - 8) <= 4, "plant too far from the centre");
                assertEquals(SiftTerrain.AIR, change.before());
                assertTrue(SiftTerrain.PLANT_TYPES.contains(change.after().block().asString()), "unexpected " + change.after());
                species.add(change.after());
            }
            assertEquals(1, species.size(), "one species per patch, seed " + seed);
            types.add(species.iterator().next().asString());
        }
        // Six species share five vanilla stand-ins (siftslate stalks and healthy sprouts are both crimson roots).
        assertEquals(5, types.size(), "all stand-ins should show up over 60 seeds: " + types);
    }
}
