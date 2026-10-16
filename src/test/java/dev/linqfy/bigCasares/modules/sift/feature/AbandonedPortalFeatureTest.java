package dev.linqfy.bigCasares.modules.sift.feature;

import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;
import dev.linqfy.bigCasares.modules.sift.SiftBlocks;
import dev.linqfy.bigCasares.modules.sift.SiftData;
import dev.linqfy.bigCasares.modules.sift.feature.SiftTemplates.Mirror;
import dev.linqfy.bigCasares.modules.sift.feature.SiftTemplates.Rotation;
import dev.linqfy.bigCasares.modules.sift.feature.SiftTemplates.Template;
import dev.linqfy.bigCasares.modules.sift.feature.SiftTemplates.TemplateBlock;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbandonedPortalFeatureTest {

    private static final SiftTemplates TEMPLATES = new SiftTemplates(SiftData.fromClasspath());
    private static final AbandonedMainPortalFeature FEATURE = new AbandonedMainPortalFeature(TEMPLATES);
    private static final int SURFACE_Y = 64;

    /** The chunk the mod selects for the portal of grid cell (0, 0). */
    private static SiftLandmarkPlacement.ChunkPos selectedChunk(long seed) {
        return SiftLandmarkPlacement.candidateChunk(seed, 0, 0, SiftLandmarkPlacement.ABANDONED_PORTAL_CELL_CHUNKS,
            SiftLandmarkPlacement.ABANDONED_PORTAL_SALT, 0);
    }

    private static InMemoryRegion flatAt(SiftLandmarkPlacement.ChunkPos chunk, long seed) {
        return InMemoryRegion.flat(chunk.x(), chunk.z(), SURFACE_Y, seed);
    }

    private static boolean placeAt(InMemoryRegion region, SiftLandmarkPlacement.ChunkPos chunk, long randomSeed) {
        return FEATURE.place(region, new Random(randomSeed), chunk.x() << 4, SURFACE_Y, chunk.z() << 4);
    }

    /** Raises the ground by {@code height} blocks over the columns where {@code area} is true. */
    private static void raise(InMemoryRegion region, int height, java.util.function.BiPredicate<Integer, Integer> area) {
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                if (!area.test(x, z)) {
                    continue;
                }
                region.put(x, SURFACE_Y - 1, z, SiftTerrain.SIFTSLATE);
                for (int y = SURFACE_Y; y < SURFACE_Y + height; y++) {
                    region.put(x, y, z, y == SURFACE_Y + height - 1 ? SiftTerrain.SIFTSLATE_GROWTH : SiftTerrain.SIFTSLATE);
                }
            }
        }
    }

    private static List<int[]> chests(InMemoryRegion region) {
        List<int[]> chests = new ArrayList<>();
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                for (int y = 0; y < 256; y++) {
                    if (region.blockId(x, y, z).equals("minecraft:chest")) {
                        chests.add(new int[]{x, y, z});
                    }
                }
            }
        }
        return chests;
    }

    @Test
    void featureIsRegisteredUnderTheModsType() {
        assertTrue(SiftFeatures.create(SiftData.fromClasspath()).find("the_sift:abandoned_main_portal").isPresent());
    }

    @Test
    void portalIsRaisedInTheSelectedChunkWithLootInItsChests() {
        for (long seed : new long[]{1L, 42L, -7L, 123456789L}) {
            SiftLandmarkPlacement.ChunkPos chunk = selectedChunk(seed);
            InMemoryRegion region = flatAt(chunk, seed);
            InMemoryRegion untouched = flatAt(chunk, seed);
            assertTrue(placeAt(region, chunk, seed), "no portal for seed " + seed);
            // The templates hold 1.7-2.3k solid blocks; a portal is never a few stray blocks.
            assertTrue(region.countDifferences(untouched) > 1500, "portal too small for seed " + seed);
            assertEquals(0, region.rejectedWrites(), "wrote outside the region for seed " + seed);

            List<int[]> chests = chests(region);
            assertTrue(chests.size() == 1 || chests.size() == 2, "unexpected chest count " + chests.size());
            assertEquals(chests.size(), region.containers().size(), "every chest gets loot");
            for (int[] chest : chests) {
                List<WorldRegion.ItemSpec> items = region.containers().get(chest[0] + "," + chest[1] + "," + chest[2]);
                assertNotNull(items, "chest without loot at " + chest[0] + "," + chest[1] + "," + chest[2]);
                assertTrue(items.size() >= 4, "too little loot: " + items);
                assertEquals(items.size(), items.stream().map(WorldRegion.ItemSpec::slot).distinct().count(), "slots repeat");
                for (WorldRegion.ItemSpec item : items) {
                    assertTrue(item.slot() < 27, "slot outside a chest: " + item);
                    assertValidItem(item.item());
                }
            }
        }
    }

    private static void assertValidItem(String id) {
        if (id.startsWith("the_sift:")) {
            assertTrue(SiftLootItems.isContentItem(id), id + " is not a content item of the port");
        } else {
            assertNotNull(Material.matchMaterial(id), id + " is not a vanilla item");
        }
    }

    @Test
    void portalStandsOnTheFlattenedGroundAndIsCoveredInSiftBlocks() {
        long seed = 5;
        SiftLandmarkPlacement.ChunkPos chunk = selectedChunk(seed);
        InMemoryRegion region = flatAt(chunk, seed);
        assertTrue(placeAt(region, chunk, seed));
        int centerX = (chunk.x() << 4) + 8;
        int centerZ = (chunk.z() << 4) + 8;
        // Lowest template layer sits on the old surface (SURFACE_Y), and the structure rises well above the ground.
        int top = region.height(WorldRegion.Heightmap.WORLD_SURFACE_WG, centerX, centerZ);
        assertTrue(top >= SURFACE_Y, "centre column must not be dug away");
        int tallest = 0;
        for (int x = centerX - 25; x <= centerX + 25; x++) {
            for (int z = centerZ - 25; z <= centerZ + 25; z++) {
                tallest = Math.max(tallest, region.height(WorldRegion.Heightmap.WORLD_SURFACE_WG, x, z));
            }
        }
        assertTrue(tallest >= SURFACE_Y + 12, "the ruin is about 19 blocks tall, tallest column ends at " + tallest);
        // Only the mod's terrain family, its plant stand-ins, the chest and air; no foreign blocks appear.
        Set<String> allowed = new java.util.HashSet<>(Set.of("minecraft:air", "minecraft:chest", "minecraft:crimson_roots",
            "minecraft:short_dry_grass", "minecraft:fern", "minecraft:short_grass", "minecraft:bush"));
        for (BlockStateSpec terrain : List.of(SiftTerrain.SIFTSLATE, SiftTerrain.SIFTSLATE_GROWTH, SiftTerrain.HEALTHY_SCULK,
            SiftTerrain.DRY_HEALTHY_SCULK, SiftTerrain.DRY_HEALTHY_SCULK_GROWTH)) {
            allowed.add(terrain.block().asString());
        }
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                for (int y = SURFACE_Y; y < 100; y++) {
                    String id = region.blockId(x, y, z);
                    assertTrue(allowed.contains(id), "unexpected " + id + " at " + x + "," + y + "," + z);
                }
            }
        }
    }

    @Test
    void otherChunksGetNothing() {
        long seed = 1;
        SiftLandmarkPlacement.ChunkPos selected = selectedChunk(seed);
        for (int[] offset : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {3, 3}}) {
            SiftLandmarkPlacement.ChunkPos other = new SiftLandmarkPlacement.ChunkPos(selected.x() + offset[0], selected.z() + offset[1]);
            InMemoryRegion region = flatAt(other, seed);
            assertFalse(placeAt(region, other, seed));
            assertEquals(0, region.countDifferences(flatAt(other, seed)));
        }
    }

    @Test
    void thePortalIsDeterministicForTheSameWorldAndRandom() {
        for (long seed : new long[]{1L, 2L, 3L}) {
            SiftLandmarkPlacement.ChunkPos chunk = selectedChunk(seed);
            InMemoryRegion first = flatAt(chunk, seed);
            InMemoryRegion second = flatAt(chunk, seed);
            assertTrue(placeAt(first, chunk, seed + 100));
            assertTrue(placeAt(second, chunk, seed + 100));
            assertEquals(first.snapshot(), second.snapshot());
            assertEquals(first.containers(), second.containers());
        }
    }

    @Test
    void differentRandomsGiveDifferentRuins() {
        long seed = 1;
        SiftLandmarkPlacement.ChunkPos chunk = selectedChunk(seed);
        Set<Map<String, String>> variants = new java.util.HashSet<>();
        for (long randomSeed = 0; randomSeed < 8; randomSeed++) {
            InMemoryRegion region = flatAt(chunk, seed);
            assertTrue(placeAt(region, chunk, randomSeed));
            variants.add(region.snapshot());
        }
        assertTrue(variants.size() >= 6, "templates, rotations and mirrors should vary, got " + variants.size());
    }

    @Test
    void neverWritesOutsideTheRegionWhateverTheRotation() {
        SiftLandmarkPlacement.ChunkPos chunk = selectedChunk(9);
        for (long randomSeed = 0; randomSeed < 24; randomSeed++) {
            InMemoryRegion region = flatAt(chunk, 9);
            placeAt(region, chunk, randomSeed);
            assertEquals(0, region.rejectedWrites(), "random " + randomSeed);
        }
    }

    @Test
    void overgrownBiomesAndIchorPeaksUseTheOvergrownRuinsEverythingElseTheWastesOnes() {
        for (String biome : List.of("the_sift:overgrown_forest", "the_sift:overgrown_clearing", "the_sift:overgrown_peaks",
            "the_sift:ichor_snowy_peaks")) {
            assertTrue(AbandonedMainPortalFeature.templatePath(biome, new Random(1)).startsWith("abandoned_portal_overgrown_"), biome);
        }
        for (String biome : List.of("the_sift:sift_wastes", "the_sift:siftslate_slopes", "the_sift:siftslate_peaks",
            "the_sift:sift_deep_dark", "minecraft:plains")) {
            assertTrue(AbandonedMainPortalFeature.templatePath(biome, new Random(1)).startsWith("abandoned_portal_wastes_"), biome);
        }
        Set<String> seen = new java.util.HashSet<>();
        Random random = new Random(0);
        for (int run = 0; run < 200; run++) {
            seen.add(AbandonedMainPortalFeature.templatePath("the_sift:sift_wastes", random));
        }
        assertEquals(5, seen.size(), "all five variants are reachable");
        for (String path : seen) {
            assertTrue(TEMPLATES.template(path).isPresent(), path);
        }
    }

    @Test
    void unevenGroundWithinTheEarthworkLimitStillGetsAPortalAndIsReshaped() {
        long seed = 3;
        SiftLandmarkPlacement.ChunkPos chunk = selectedChunk(seed);
        int centerX = (chunk.x() << 4) + 8;
        InMemoryRegion region = flatAt(chunk, seed);
        raise(region, 6, (x, z) -> x >= centerX);
        InMemoryRegion untouched = flatAt(chunk, seed);
        raise(untouched, 6, (x, z) -> x >= centerX);
        assertTrue(placeAt(region, chunk, seed), "a 6-block step is within the mod's limits");
        assertEquals(0, region.rejectedWrites());
        assertTrue(region.countDifferences(untouched) > 1500);
    }

    @Test
    void aStepBeyondTheEightBlockEarthworkLimitRefusesAPortalEvenBelowTheReliefLimit() {
        long seed = 3;
        SiftLandmarkPlacement.ChunkPos chunk = selectedChunk(seed);
        int centerX = (chunk.x() << 4) + 8;
        // 12 blocks of relief pass the 18-block pre-check, but the footprint floor sits at the median
        // and the mod never moves a footprint column by more than 8 blocks.
        InMemoryRegion region = flatAt(chunk, seed);
        raise(region, 12, (x, z) -> x >= centerX - 30 && x <= centerX);
        InMemoryRegion untouched = flatAt(chunk, seed);
        raise(untouched, 12, (x, z) -> x >= centerX - 30 && x <= centerX);
        assertFalse(placeAt(region, chunk, seed));
        assertEquals(0, region.countDifferences(untouched));
    }

    @Test
    void cliffsHigherThanTheReliefLimitRefuseAPortal() {
        long seed = 3;
        SiftLandmarkPlacement.ChunkPos chunk = selectedChunk(seed);
        int centerX = (chunk.x() << 4) + 8;
        int centerZ = (chunk.z() << 4) + 8;
        InMemoryRegion region = flatAt(chunk, seed);
        // A 30-block plateau under the whole centre: every rotation's footprint crosses its edge.
        raise(region, 30, (x, z) -> Math.abs(x - centerX) <= 2 && Math.abs(z - centerZ) <= 2);
        InMemoryRegion before = flatAt(chunk, seed);
        raise(before, 30, (x, z) -> Math.abs(x - centerX) <= 2 && Math.abs(z - centerZ) <= 2);
        assertFalse(placeAt(region, chunk, seed));
        assertEquals(0, region.countDifferences(before), "a refused portal must not touch the ground");
    }

    @Test
    void groundCoveredBySomethingElseRefusesAPortal() {
        long seed = 3;
        SiftLandmarkPlacement.ChunkPos chunk = selectedChunk(seed);
        InMemoryRegion region = flatAt(chunk, seed);
        // A stone roof 3 blocks above the ground (a cave ceiling or a tree canopy): not ground, not replaceable.
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                region.put(x, SURFACE_Y + 3, z, BlockStateSpec.of("minecraft:stone"));
            }
        }
        InMemoryRegion before = flatAt(chunk, seed);
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                before.put(x, SURFACE_Y + 3, z, BlockStateSpec.of("minecraft:stone"));
            }
        }
        assertFalse(placeAt(region, chunk, seed));
        assertEquals(0, region.countDifferences(before));
    }

    @Test
    void plantsAndSnowOnTheGroundAreBuiltOver() {
        long seed = 3;
        SiftLandmarkPlacement.ChunkPos chunk = selectedChunk(seed);
        InMemoryRegion region = flatAt(chunk, seed);
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                region.put(x, SURFACE_Y, z, ((x + z) & 1) == 0 ? SiftBlocks.remap("overgrown_fronds", Map.of()).orElseThrow()
                    : BlockStateSpec.of("minecraft:snow", Map.of("layers", "1")));
            }
        }
        assertTrue(placeAt(region, chunk, seed), "replaceable plants and snow must not block a portal");
        assertEquals(0, region.rejectedWrites());
    }

    @Test
    void waterOnTheGroundRefusesAPortal() {
        long seed = 3;
        SiftLandmarkPlacement.ChunkPos chunk = selectedChunk(seed);
        InMemoryRegion region = flatAt(chunk, seed);
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                region.put(x, SURFACE_Y, z, BlockStateSpec.of("minecraft:water", Map.of("level", "0")));
            }
        }
        assertFalse(placeAt(region, chunk, seed));
    }

    @Test
    void everyTemplateMapsToBackingStatesAndNamesItsChestsLootTable() {
        for (String family : List.of("overgrown", "wastes")) {
            for (int variant = 1; variant <= 5; variant++) {
                String path = "abandoned_portal_" + family + "_" + variant;
                Template template = TEMPLATES.template(path).orElseThrow();
                Map<Integer, String> loot = TEMPLATES.lootTables(path);
                assertFalse(loot.isEmpty(), path + " has no chest loot");
                int chests = 0;
                for (int index = 0; index < template.blocks().size(); index++) {
                    TemplateBlock block = template.blocks().get(index);
                    if (block.block().equals("minecraft:chest")) {
                        chests++;
                        assertEquals(AbandonedMainPortalFeature.LOOT_TABLE, loot.get(index), path);
                    } else if (block.block().startsWith("the_sift:")) {
                        String name = block.block().substring("the_sift:".length());
                        assertTrue(SiftBlocks.remap(name, block.properties()).isPresent(), path + " has unmapped " + block.block());
                    } else {
                        assertEquals("minecraft:air", block.block(), path);
                    }
                }
                assertEquals(chests, loot.size(), path);
                assertEquals(List.copyOf(loot.keySet()).stream().sorted().toList(), List.copyOf(loot.keySet()),
                    "loot indices must iterate in ascending order for determinism");
            }
        }
    }

    @Test
    void chestFacingIsMirroredThenRotatedClockwise() {
        assertEquals("north", AbandonedMainPortalFeature.transformFacing("north", Rotation.NONE, Mirror.NONE));
        assertEquals("east", AbandonedMainPortalFeature.transformFacing("north", Rotation.CLOCKWISE_90, Mirror.NONE));
        assertEquals("south", AbandonedMainPortalFeature.transformFacing("north", Rotation.CLOCKWISE_180, Mirror.NONE));
        assertEquals("west", AbandonedMainPortalFeature.transformFacing("north", Rotation.COUNTERCLOCKWISE_90, Mirror.NONE));
        assertEquals("south", AbandonedMainPortalFeature.transformFacing("north", Rotation.NONE, Mirror.LEFT_RIGHT));
        assertEquals("east", AbandonedMainPortalFeature.transformFacing("east", Rotation.NONE, Mirror.LEFT_RIGHT));
        assertEquals("west", AbandonedMainPortalFeature.transformFacing("east", Rotation.NONE, Mirror.FRONT_BACK));
        assertEquals("west", AbandonedMainPortalFeature.transformFacing("north", Rotation.CLOCKWISE_90, Mirror.LEFT_RIGHT));
    }

    @Test
    void placedChestsAreSingleDryChestsAgainstTheirOwnFacing() {
        for (long seed = 0; seed < 8; seed++) {
            SiftLandmarkPlacement.ChunkPos chunk = selectedChunk(seed);
            InMemoryRegion region = flatAt(chunk, seed);
            assertTrue(placeAt(region, chunk, seed));
            for (int[] chest : chests(region)) {
                BlockStateSpec state = region.state(chest[0], chest[1], chest[2]);
                assertEquals("single", state.properties().get("type"));
                assertEquals("false", state.properties().get("waterlogged"));
                assertFalse(state.properties().containsKey("ichorlogged"), "not a vanilla property: " + state);
                assertTrue(Set.of("north", "east", "south", "west").contains(state.properties().get("facing")), state.toString());
            }
        }
    }

    @Test
    void chestLootIsRolledFromTheTableWithMappedItems() {
        Set<String> seen = new java.util.HashSet<>();
        for (long seed = 0; seed < 300; seed++) {
            for (WorldRegion.ItemSpec spec : FEATURE.rollLoot(AbandonedMainPortalFeature.LOOT_TABLE, new Random(seed))) {
                seen.add(spec.item());
                assertValidItem(spec.item());
            }
        }
        assertTrue(seen.contains("the_sift:charoite"));
        assertTrue(seen.contains("the_sift:siftslate"));
        assertTrue(seen.contains("minecraft:diamond"));
        assertTrue(seen.contains("minecraft:short_grass"), "Sift plants arrive as their stand-in items");
        assertTrue(seen.contains("minecraft:music_disc_otherside"));
        assertFalse(seen.contains("the_sift:siftite_upgrade_smithing_template"));
        assertFalse(seen.contains("the_sift:overgrown_fronds"));
        assertEquals(List.of(), FEATURE.rollLoot("the_sift:chests/does_not_exist", new Random(1)));
        assertEquals(List.of(), FEATURE.rollLoot("minecraft:chests/simple_dungeon", new Random(1)));
    }
}
