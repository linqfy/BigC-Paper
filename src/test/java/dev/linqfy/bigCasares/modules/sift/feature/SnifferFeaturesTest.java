package dev.linqfy.bigCasares.modules.sift.feature;

import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;
import dev.linqfy.bigCasares.modules.sift.SiftData;
import dev.linqfy.bigCasares.modules.sift.feature.SiftSurfaceSculkRegionFeature.SculkRegion;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour of the sniffer family: both sniffer caves, the sniffer plant patch and the surface
 * sculk regions, on flat Sift ground. The shared determinism and region-bounds checks for every
 * registered feature live in {@link SiftFeatureContractTest}.
 */
class SnifferFeaturesTest {

    private static final SiftFeatures FEATURES = SiftFeatures.create(SiftData.fromClasspath());
    private static final int SURFACE = 64;
    private static final BlockStateSpec SCULK = BlockStateSpec.of("minecraft:sculk");

    private record Cave(String type, int cellChunks, long salt) {
    }

    private static final List<Cave> CAVES = List.of(
        new Cave("the_sift:sniffer_cave_overgrown", SiftLandmarkPlacement.SNIFFER_CAVE_OVERGROWN_CELL_CHUNKS,
            SiftLandmarkPlacement.SNIFFER_CAVE_OVERGROWN_SALT),
        new Cave("the_sift:sniffer_cave_wastes", SiftLandmarkPlacement.SNIFFER_CAVE_WASTES_CELL_CHUNKS,
            SiftLandmarkPlacement.SNIFFER_CAVE_WASTES_SALT));

    // ---- registration ----

    @Test
    void everySnifferFeatureIsRegistered() {
        for (String type : List.of("the_sift:sniffer_cave_overgrown", "the_sift:sniffer_cave_wastes",
            "the_sift:sniffer_plant_patch", "the_sift:surface_sculk_region")) {
            assertTrue(FEATURES.find(type).isPresent(), type + " is not registered");
        }
    }

    // ---- sniffer caves ----

    @Test
    void cavesAreCarvedInSelectedChunksWithAnIchorPoolANestAndASnifferFamily() {
        boolean sawThirdSniffer = false;
        for (Cave cave : CAVES) {
            SiftFeature feature = FEATURES.find(cave.type()).orElseThrow();
            for (long seed = 1; seed <= 20; seed++) {
                SiftLandmarkPlacement.ChunkPos chunk = selectedChunk(cave, seed);
                InMemoryRegion region = InMemoryRegion.flat(chunk.x(), chunk.z(), SURFACE, seed);
                String label = cave.type() + " seed " + seed;
                assertTrue(feature.place(region, new Random(seed), chunk.x() * 16, SURFACE, chunk.z() * 16),
                    label + " should fit on flat Sift ground");

                int carved = count(region, 30, SURFACE - 1, state -> state.block().asString().equals("minecraft:air"));
                assertTrue(carved > 300, label + " carved only " + carved + " blocks");
                assertTrue(region.isAir(chunk.x() * 16 + 8, SURFACE - 1, chunk.z() * 16 + 8), label + " has no open entrance");
                assertTrue(count(region, 30, SURFACE, state -> state.equals(SiftTerrain.ICHOR)) > 0, label + " has no ichor pool");
                assertTrue(count(region, 30, SURFACE, state -> state.equals(SiftTerrain.HEALTHY_SCULK)
                    || state.equals(SiftTerrain.DRY_HEALTHY_SCULK)) > 50, label + " has no sculk lining");
                int nest = count(region, 30, SURFACE, state -> state.equals(SiftTerrain.OVERGROWN_WILLOW_FOLIAGE));
                assertTrue(nest > 5, label + " has no willow nest");
                assertTrue(count(region, 30, SURFACE, state -> isBulb(state) || state.block().asString().equals("minecraft:torchflower")) > 0,
                    label + " has no sniffer plants");
                assertEquals(0, region.rejectedWrites(), label + " wrote outside its region");

                List<InMemoryRegion.Spawn> spawns = region.spawns();
                assertTrue(spawns.size() == 2 || spawns.size() == 3, label + " spawned " + spawns.size() + " sniffers");
                sawThirdSniffer |= spawns.size() == 3;
                Set<List<Integer>> blocks = new HashSet<>();
                for (InMemoryRegion.Spawn spawn : spawns) {
                    assertEquals("minecraft:sniffer", spawn.type());
                    assertEquals(null, spawn.marker(), "cave sniffers are ordinary sniffers");
                    int x = (int) Math.floor(spawn.x());
                    int y = (int) Math.floor(spawn.y());
                    int z = (int) Math.floor(spawn.z());
                    assertEquals(x + 0.5, spawn.x(), 1.0E-9);
                    assertEquals(z + 0.5, spawn.z(), 1.0E-9);
                    assertEquals(SiftTerrain.OVERGROWN_WILLOW_FOLIAGE, region.state(x, y - 1, z), label + " sniffer is not on the nest");
                    assertTrue(region.isAir(x, y, z) && region.isAir(x, y + 1, z), label + " sniffer has no room");
                    assertTrue(blocks.add(List.of(x, y, z)), label + " stacked two sniffers on one block");
                }
            }
        }
        assertTrue(sawThirdSniffer, "some families should have the third (baby) sniffer");
    }

    @Test
    void cavesRepeatExactlyForTheSameSeed() {
        for (Cave cave : CAVES) {
            SiftFeature feature = FEATURES.find(cave.type()).orElseThrow();
            for (long seed : new long[]{3L, 11L, -9L}) {
                InMemoryRegion first = placeCave(feature, cave, seed, seed);
                InMemoryRegion second = placeCave(feature, cave, seed, seed);
                assertEquals(first.snapshot(), second.snapshot(), cave.type() + " seed " + seed);
                assertEquals(first.spawns(), second.spawns(), cave.type() + " seed " + seed);
                // Same world, same chunk, another random stream: another cave.
                InMemoryRegion other = placeCave(feature, cave, seed, seed + 1000);
                assertFalse(first.snapshot().equals(other.snapshot()), "different random streams should give different caves");
            }
        }
    }

    @Test
    void cavesAreRegionSafeAtEveryPositionOfTheirChunk() {
        for (Cave cave : CAVES) {
            SiftFeature feature = FEATURES.find(cave.type()).orElseThrow();
            long seed = 5;
            SiftLandmarkPlacement.ChunkPos chunk = selectedChunk(cave, seed);
            InMemoryRegion region = InMemoryRegion.flat(chunk.x(), chunk.z(), SURFACE, seed);
            Random random = new Random(seed);
            for (int attempt = 0; attempt < 6; attempt++) {
                feature.place(region, random, chunk.x() * 16 + random.nextInt(16), SURFACE, chunk.z() * 16 + random.nextInt(16));
            }
            assertEquals(0, region.rejectedWrites(), cave.type() + " wrote outside its region");
        }
    }

    @Test
    void cavesOnlyAppearInTheSelectedChunksOfTheirGrid() {
        for (Cave cave : CAVES) {
            SiftFeature feature = FEATURES.find(cave.type()).orElseThrow();
            long seed = 7;
            SiftLandmarkPlacement.ChunkPos selected = selectedChunk(cave, seed);
            int otherX = selected.x() + 1;
            while (SiftLandmarkPlacement.isSelectedChunk(seed, otherX * 16, selected.z() * 16, cave.cellChunks(), cave.salt(), candidates(cave))) {
                otherX++;
            }
            InMemoryRegion region = InMemoryRegion.flat(otherX, selected.z(), SURFACE, seed);
            InMemoryRegion untouched = InMemoryRegion.flat(otherX, selected.z(), SURFACE, seed);
            assertFalse(feature.place(region, new Random(seed), otherX * 16, SURFACE, selected.z() * 16));
            assertEquals(0, region.countDifferences(untouched));
            assertTrue(region.spawns().isEmpty());
        }
    }

    @Test
    void cavesDoNotCutThroughProtectedBlocks() {
        List<BlockStateSpec> protectedBlocks = List.of(
            SiftTerrain.REINFORCED_SIFTSLATE,
            SiftTerrain.SONOROUS_DEEPSLATE,
            SiftTerrain.OVERGROWN_WILLOW_WOOD,
            SiftTerrain.OVERGROWN_WILLOW_LOG,
            SiftTerrain.block("sift_portal"));
        for (Cave cave : CAVES) {
            SiftFeature feature = FEATURES.find(cave.type()).orElseThrow();
            long seed = 4;
            SiftLandmarkPlacement.ChunkPos chunk = selectedChunk(cave, seed);
            int entranceX = chunk.x() * 16 + 8;
            int entranceZ = chunk.z() * 16 + 8;
            InMemoryRegion open = InMemoryRegion.flat(chunk.x(), chunk.z(), SURFACE, seed);
            assertTrue(feature.place(open, new Random(seed), chunk.x() * 16, SURFACE, chunk.z() * 16), "control cave should place");
            for (BlockStateSpec block : protectedBlocks) {
                InMemoryRegion region = InMemoryRegion.flat(chunk.x(), chunk.z(), SURFACE, seed);
                InMemoryRegion untouched = InMemoryRegion.flat(chunk.x(), chunk.z(), SURFACE, seed);
                for (int[] offset : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    region.put(entranceX + offset[0], SURFACE - 1, entranceZ + offset[1], block);
                    untouched.put(entranceX + offset[0], SURFACE - 1, entranceZ + offset[1], block);
                }
                assertFalse(feature.place(region, new Random(seed), chunk.x() * 16, SURFACE, chunk.z() * 16),
                    cave.type() + " cut through " + block);
                assertEquals(0, region.countDifferences(untouched), cave.type() + " changed blocks despite refusing");
                assertTrue(region.spawns().isEmpty());
            }
        }
    }

    @Test
    void cavesNeedSiftGroundBelowTheEntrance() {
        for (Cave cave : CAVES) {
            SiftFeature feature = FEATURES.find(cave.type()).orElseThrow();
            long seed = 2;
            SiftLandmarkPlacement.ChunkPos chunk = selectedChunk(cave, seed);
            InMemoryRegion region = InMemoryRegion.flat(chunk.x(), chunk.z(), SURFACE, seed);
            InMemoryRegion untouched = InMemoryRegion.flat(chunk.x(), chunk.z(), SURFACE, seed);
            for (InMemoryRegion each : List.of(region, untouched)) {
                for (int x = each.minX(); x <= each.maxX(); x++) {
                    for (int z = each.minZ(); z <= each.maxZ(); z++) {
                        each.put(x, SURFACE - 1, z, BlockStateSpec.of("minecraft:stone"));
                    }
                }
            }
            assertFalse(feature.place(region, new Random(seed), chunk.x() * 16, SURFACE, chunk.z() * 16));
            assertEquals(0, region.countDifferences(untouched));
        }
    }

    // ---- sniffer plant patch ----

    @Test
    void plantPatchesPlantOneKindOfBulbOnSculkSoil() {
        SiftFeature patch = FEATURES.find("the_sift:sniffer_plant_patch").orElseThrow();
        int torchPatches = 0;
        int pitcherPatches = 0;
        for (long seed = 0; seed < 40; seed++) {
            InMemoryRegion region = InMemoryRegion.flat(0, 0, SURFACE, seed);
            assertTrue(patch.place(region, new Random(seed), 0, SURFACE, 0), "seed " + seed);
            int torchflowers = count(region, SURFACE - 2, SURFACE + 3, state -> state.block().asString().equals("minecraft:torchflower"));
            List<int[]> lowers = positions(region, state -> isBulb(state) && "lower".equals(state.properties().get("half")));
            assertEquals(0, region.rejectedWrites());
            // Exactly one plant kind per patch, 5 to 9 plants (28 tries are enough on open ground).
            assertTrue(torchflowers == 0 || lowers.isEmpty(), "mixed plants in seed " + seed);
            int plants = torchflowers + lowers.size();
            assertTrue(plants >= 5 && plants <= 9, "seed " + seed + " planted " + plants);
            if (torchflowers > 0) {
                torchPatches++;
            } else {
                pitcherPatches++;
            }
            for (int[] lower : lowers) {
                BlockStateSpec bulb = region.state(lower[0], lower[1], lower[2]);
                assertEquals("lower", bulb.properties().get("half"));
                assertEquals(bulb.with("half", "upper"), region.state(lower[0], lower[1] + 1, lower[2]), "missing upper half");
                assertTrue(SiftTerrain.isGrowth(region.state(lower[0], lower[1] - 1, lower[2])));
                // Within the 13 x 13 square around the chunk centre.
                assertTrue(Math.abs(lower[0] - 8) <= 6 && Math.abs(lower[2] - 8) <= 6);
            }
        }
        assertTrue(torchPatches > 5 && pitcherPatches > 5, "torchflower patches " + torchPatches + ", pitcher patches " + pitcherPatches);
    }

    @Test
    void plantPatchesAlsoGrowOnHealthyAndDryHealthySculk() {
        SiftFeature patch = FEATURES.find("the_sift:sniffer_plant_patch").orElseThrow();
        for (BlockStateSpec soil : List.of(SiftTerrain.HEALTHY_SCULK, SiftTerrain.DRY_HEALTHY_SCULK)) {
            InMemoryRegion region = InMemoryRegion.flat(2, -3, SURFACE, 9);
            for (int x = region.minX(); x <= region.maxX(); x++) {
                for (int z = region.minZ(); z <= region.maxZ(); z++) {
                    region.put(x, SURFACE - 1, z, soil);
                }
            }
            assertTrue(patch.place(region, new Random(9), 2 * 16, SURFACE, -3 * 16));
            assertEquals(0, region.rejectedWrites());
        }
    }

    @Test
    void plantPatchesNeedSculkOrGrowthSoil() {
        SiftFeature patch = FEATURES.find("the_sift:sniffer_plant_patch").orElseThrow();
        for (BlockStateSpec floor : List.of(SiftTerrain.SIFTSLATE, BlockStateSpec.of("minecraft:stone"))) {
            InMemoryRegion region = InMemoryRegion.flat(0, 0, SURFACE, 1);
            InMemoryRegion untouched = InMemoryRegion.flat(0, 0, SURFACE, 1);
            for (InMemoryRegion each : List.of(region, untouched)) {
                for (int x = each.minX(); x <= each.maxX(); x++) {
                    for (int z = each.minZ(); z <= each.maxZ(); z++) {
                        each.put(x, SURFACE - 1, z, floor);
                    }
                }
            }
            assertFalse(patch.place(region, new Random(1), 0, SURFACE, 0), "planted on " + floor);
            assertEquals(0, region.countDifferences(untouched));
        }
    }

    @Test
    void sprayedPlantsNeedFreeSpaceAndSiftSoil() {
        Random random = new Random(1);
        // Torchflower on Sift soil, but not on plain stone.
        InMemoryRegion region = InMemoryRegion.flat(0, 0, SURFACE, 1);
        assertTrue(SiftSnifferPlantPlacement.place(region, 4, SURFACE, 4, SiftSnifferPlantPlacement.PlantType.TORCHFLOWER, random));
        assertEquals("minecraft:torchflower", region.blockId(4, SURFACE, 4));
        assertFalse(SiftSnifferPlantPlacement.place(region, 4, SURFACE, 4, SiftSnifferPlantPlacement.PlantType.TORCHFLOWER, random),
            "occupied position");
        region.put(6, SURFACE - 1, 6, BlockStateSpec.of("minecraft:stone"));
        assertFalse(SiftSnifferPlantPlacement.place(region, 6, SURFACE, 6, SiftSnifferPlantPlacement.PlantType.TORCHFLOWER, random));
        // A bulb needs two free blocks.
        region.put(8, SURFACE + 1, 8, BlockStateSpec.of("minecraft:stone"));
        assertFalse(SiftSnifferPlantPlacement.place(region, 8, SURFACE, 8, SiftSnifferPlantPlacement.PlantType.PITCHER_CROP, random));
        assertEquals("minecraft:air", region.blockId(8, SURFACE, 8));
        assertTrue(SiftSnifferPlantPlacement.place(region, 9, SURFACE, 9, SiftSnifferPlantPlacement.PlantType.PITCHER_CROP, random));
        assertEquals("minecraft:pitcher_plant", region.blockId(9, SURFACE, 9));
        assertEquals("minecraft:pitcher_plant", region.blockId(9, SURFACE + 1, 9));
        // Nothing is placed outside the writable area.
        assertFalse(SiftSnifferPlantPlacement.place(region, 100, SURFACE, 100, SiftSnifferPlantPlacement.PlantType.TORCHFLOWER, random));
        assertEquals(0, region.rejectedWrites());
    }

    // ---- surface sculk regions ----

    @Test
    void sculkRegionsAreLaidOutPerCellFromTheSeed() {
        for (long seed : new long[]{1L, 2L, -5L}) {
            SculkRegion first = SiftSurfaceSculkRegionFeature.createRegion(seed, 3, -2);
            SculkRegion second = SiftSurfaceSculkRegionFeature.createRegion(seed, 3, -2);
            assertEquals(first, second);
            assertTrue(first.blobs().size() >= 6 && first.blobs().size() <= 11);
            assertTrue(first.majorRadius() >= 25.0 && first.majorRadius() <= 60.0);
            assertTrue(first.minorRadius() <= first.majorRadius());
            // The region stays inside its 512-block cell (the margin keeps neighbours from overlapping).
            assertTrue(first.centerX() - first.majorRadius() >= 3 * 512 && first.centerX() + first.majorRadius() < 4 * 512);
            assertTrue(first.centerZ() - first.majorRadius() >= -2 * 512 && first.centerZ() + first.majorRadius() < -1 * 512);
            assertFalse(first.equals(SiftSurfaceSculkRegionFeature.createRegion(seed + 1, 3, -2)));
        }
    }

    @Test
    void sculkRegionsTurnTheSurfaceToSculkWithAFringeOfVeinsAndTwoOrFewerDarkSniffers() {
        for (long seed = 1; seed <= 3; seed++) {
            SculkRun run = runRegion(seed, false);
            String label = "seed " + seed;
            assertTrue(run.changedChunks >= 6, label + " changed only " + run.changedChunks + " chunks");
            assertEquals(0, run.rejectedWrites, label + " wrote outside a region");
            assertEquals(0, run.outsideChunkWrites, label + " wrote outside the chunk it was generating");
            assertTrue(run.sculkColumns > 500, label + " has only " + run.sculkColumns + " sculk columns");
            assertEquals(0, run.badDepthColumns, label + " has sculk columns that are not 6 to 9 deep");
            assertTrue(run.fringeVeins > 20, label + " has no vein fringe");
            assertEquals(0, run.floatingVeins, label + " has veins that do not rest on terrain");
            assertTrue(run.decorations > 0, label + " has no sensors, shriekers or catalysts");
            assertTrue(run.sniffers >= 1 && run.sniffers <= 2, label + " has " + run.sniffers + " dark sniffers");
            assertEquals(0, run.misplacedSniffers, label + " has a sniffer off the sculk surface");
        }
    }

    @Test
    void sculkRegionsClearTheSiftPlantsOnTheirSurface() {
        SculkRun run = runRegion(2, true);
        assertTrue(run.sculkColumns > 500);
        assertEquals(0, run.plantsOnSculk, "plants were left standing on sculk");
        assertEquals(0, run.rejectedWrites);
    }

    @Test
    void sculkRegionsLeaveChunksOutsideAnyRegionAlone() {
        SiftFeature feature = FEATURES.find("the_sift:surface_sculk_region").orElseThrow();
        // Cell margins keep every region at least 16 blocks from the cell border, so chunk (0, 0) is empty.
        InMemoryRegion region = InMemoryRegion.flat(0, 0, SURFACE, 1);
        InMemoryRegion untouched = InMemoryRegion.flat(0, 0, SURFACE, 1);
        assertFalse(feature.place(region, new Random(1), 0, SURFACE, 0));
        assertEquals(0, region.countDifferences(untouched));
        assertTrue(region.spawns().isEmpty());
    }

    // ---- statelessness ----

    @Test
    void featuresGiveTheSameResultOnConcurrentWorldgenThreads() throws Exception {
        List<SiftFeature> features = new ArrayList<>();
        for (Cave cave : CAVES) {
            features.add(FEATURES.find(cave.type()).orElseThrow());
        }
        List<Map<String, String>> expected = new ArrayList<>();
        for (int index = 0; index < features.size(); index++) {
            expected.add(placeCave(features.get(index), CAVES.get(index), 8, 8).snapshot());
        }
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<Map<String, String>>> results = new ArrayList<>();
            for (int round = 0; round < 8; round++) {
                int index = round % features.size();
                results.add(pool.submit(() -> placeCave(features.get(index), CAVES.get(index), 8, 8).snapshot()));
            }
            for (int round = 0; round < results.size(); round++) {
                assertEquals(expected.get(round % features.size()), results.get(round).get(), "round " + round);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- helpers ----

    /** What running the sculk feature over every chunk of a region gave. */
    private static final class SculkRun {
        int changedChunks;
        int rejectedWrites;
        int outsideChunkWrites;
        int sculkColumns;
        int badDepthColumns;
        int fringeVeins;
        int floatingVeins;
        int decorations;
        int sniffers;
        int misplacedSniffers;
        int plantsOnSculk;
    }

    /** Runs the feature over all chunks around the (0, 0)-cell region of the seed on flat ground. */
    private static SculkRun runRegion(long seed, boolean withPlants) {
        SiftFeature feature = FEATURES.find("the_sift:surface_sculk_region").orElseThrow();
        SculkRegion sculk = SiftSurfaceSculkRegionFeature.createRegion(seed, 0, 0);
        int reach = (int) Math.ceil(sculk.majorRadius()) + 3;
        SculkRun run = new SculkRun();
        for (int chunkX = Math.floorDiv((int) sculk.centerX() - reach, 16); chunkX <= Math.floorDiv((int) sculk.centerX() + reach, 16); chunkX++) {
            for (int chunkZ = Math.floorDiv((int) sculk.centerZ() - reach, 16); chunkZ <= Math.floorDiv((int) sculk.centerZ() + reach, 16); chunkZ++) {
                InMemoryRegion region = InMemoryRegion.flat(chunkX, chunkZ, SURFACE, seed);
                InMemoryRegion untouched = InMemoryRegion.flat(chunkX, chunkZ, SURFACE, seed);
                if (withPlants) {
                    for (int x = region.minX(); x <= region.maxX(); x++) {
                        for (int z = region.minZ(); z <= region.maxZ(); z++) {
                            BlockStateSpec plant = (x + z) % 3 == 0 ? SiftTerrain.block("overgrown_fronds")
                                : (x + z) % 3 == 1 ? SiftTerrain.block("overgrown_stalks") : SiftTerrain.block("siftslate_stalks");
                            region.put(x, SURFACE, z, plant);
                            untouched.put(x, SURFACE, z, plant);
                        }
                    }
                }
                if (feature.place(region, new Random(seed), chunkX * 16, SURFACE, chunkZ * 16)) {
                    run.changedChunks++;
                }
                run.rejectedWrites += region.rejectedWrites();
                run.outsideChunkWrites += differencesOutsideChunk(region, untouched, chunkX, chunkZ);
                for (int x = chunkX * 16; x < chunkX * 16 + 16; x++) {
                    for (int z = chunkZ * 16; z < chunkZ * 16 + 16; z++) {
                        tally(run, region, x, z);
                    }
                }
                for (InMemoryRegion.Spawn spawn : region.spawns()) {
                    int x = (int) Math.floor(spawn.x());
                    int y = (int) Math.floor(spawn.y());
                    int z = (int) Math.floor(spawn.z());
                    run.sniffers++;
                    if (!spawn.type().equals("minecraft:sniffer") || !WorldRegion.DARK_SNIFFER_MARKER.equals(spawn.marker())
                        || !region.state(x, y - 1, z).equals(SCULK)
                        || !region.isAir(x, y, z) || !region.isAir(x, y + 1, z)) {
                        run.misplacedSniffers++;
                    }
                }
            }
        }
        return run;
    }

    private static void tally(SculkRun run, InMemoryRegion region, int x, int z) {
        BlockStateSpec top = region.state(x, SURFACE - 1, z);
        BlockStateSpec above = region.state(x, SURFACE, z);
        String aboveId = above.block().asString();
        if (top.equals(SCULK)) {
            run.sculkColumns++;
            int depth = 0;
            while (region.state(x, SURFACE - 1 - depth, z).equals(SCULK)) {
                depth++;
            }
            if (depth < 6 || depth > 9 || !SiftTerrain.SIFTSLATE.equals(region.state(x, SURFACE - 1 - depth, z))) {
                run.badDepthColumns++;
            }
            if (SiftTerrain.PLANT_TYPES.contains(aboveId)) {
                run.plantsOnSculk++;
            }
        }
        if (aboveId.equals("minecraft:sculk_vein")) {
            if (!top.equals(SCULK)) {
                run.fringeVeins++;
            }
            if (!SiftTerrain.isTerrain(region, x, SURFACE - 1, z) && !top.equals(SCULK)) {
                run.floatingVeins++;
            }
        }
        if (aboveId.equals("minecraft:sculk_sensor") || aboveId.equals("minecraft:sculk_shrieker")
            || aboveId.equals("minecraft:sculk_catalyst")) {
            run.decorations++;
        }
    }

    private static int differencesOutsideChunk(InMemoryRegion changed, InMemoryRegion untouched, int chunkX, int chunkZ) {
        int differences = 0;
        for (int x = changed.minX(); x <= changed.maxX(); x++) {
            for (int z = changed.minZ(); z <= changed.maxZ(); z++) {
                if (Math.floorDiv(x, 16) == chunkX && Math.floorDiv(z, 16) == chunkZ) {
                    continue;
                }
                for (int y = 50; y < 80; y++) {
                    if (!changed.state(x, y, z).equals(untouched.state(x, y, z))) {
                        differences++;
                    }
                }
            }
        }
        return differences;
    }

    private static InMemoryRegion placeCave(SiftFeature feature, Cave cave, long worldSeed, long randomSeed) {
        SiftLandmarkPlacement.ChunkPos chunk = selectedChunk(cave, worldSeed);
        InMemoryRegion region = InMemoryRegion.flat(chunk.x(), chunk.z(), SURFACE, worldSeed);
        feature.place(region, new Random(randomSeed), chunk.x() * 16, SURFACE, chunk.z() * 16);
        return region;
    }

    /** The first candidate chunk of grid cell (0, 0): a chunk the cave feature accepts for this seed. */
    private static SiftLandmarkPlacement.ChunkPos selectedChunk(Cave cave, long seed) {
        SiftLandmarkPlacement.ChunkPos chunk = SiftLandmarkPlacement.candidateChunk(seed, 0, 0, cave.cellChunks(), cave.salt(), 0);
        assertTrue(SiftLandmarkPlacement.isSelectedChunk(seed, chunk.x() * 16, chunk.z() * 16, cave.cellChunks(), cave.salt(),
            candidates(cave)));
        return chunk;
    }

    private static int candidates(Cave cave) {
        return cave.cellChunks() == SiftLandmarkPlacement.SNIFFER_CAVE_OVERGROWN_CELL_CHUNKS
            ? SiftLandmarkPlacement.SNIFFER_CAVE_OVERGROWN_CANDIDATES : SiftLandmarkPlacement.SNIFFER_CAVE_WASTES_CANDIDATES;
    }

    private static boolean isBulb(BlockStateSpec state) {
        return state.block().asString().equals("minecraft:pitcher_plant");
    }

    private static int count(InMemoryRegion region, int minY, int maxY, Predicate<BlockStateSpec> matches) {
        return positions(region, minY, maxY, matches).size();
    }

    private static List<int[]> positions(InMemoryRegion region, Predicate<BlockStateSpec> matches) {
        return positions(region, SURFACE - 2, SURFACE + 3, matches);
    }

    private static List<int[]> positions(InMemoryRegion region, int minY, int maxY, Predicate<BlockStateSpec> matches) {
        List<int[]> found = new ArrayList<>();
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                for (int y = minY; y <= maxY; y++) {
                    if (matches.test(region.state(x, y, z))) {
                        found.add(new int[]{x, y, z});
                    }
                }
            }
        }
        return found;
    }
}
