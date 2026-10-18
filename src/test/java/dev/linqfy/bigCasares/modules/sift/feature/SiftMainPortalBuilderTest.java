package dev.linqfy.bigCasares.modules.sift.feature;

import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;
import dev.linqfy.bigCasares.modules.sift.SiftBlocks;
import dev.linqfy.bigCasares.modules.sift.SiftData;
import dev.linqfy.bigCasares.modules.sift.feature.SiftMainPortalSite.Anchor;
import dev.linqfy.bigCasares.modules.sift.feature.SiftMainPortalSite.Origin;
import dev.linqfy.bigCasares.modules.sift.feature.SiftMainPortalSite.Window;
import dev.linqfy.bigCasares.modules.sift.feature.SiftTemplates.Template;
import dev.linqfy.bigCasares.modules.sift.feature.SiftTemplates.TemplateBlock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiftMainPortalBuilderTest {

    private static final Template TEMPLATE = new SiftTemplates(SiftData.fromClasspath())
        .template(SiftMainPortalSite.TEMPLATE).orElseThrow();
    private static final BlockStateSpec LIGHT = SiftBlocks.remap("sift_portal", Map.of()).orElseThrow();
    private static final Set<String> PLANTS = Set.of("minecraft:fern", "minecraft:bush", "minecraft:short_grass",
        "minecraft:crimson_roots", "minecraft:short_dry_grass");
    private static final int GROUND = 70;

    private static TestTerrain area(int height) {
        return TestTerrain.flat(-400, 400, -400, 400, height);
    }

    private static Origin originOn(int groundY) {
        return SiftMainPortalSite.origin(new SiftMainPortalSite.Candidate(10, -20, groundY, 0.0), 0, 256);
    }

    private static BlockStateSpec expected(TemplateBlock block) {
        return SiftMainPortalBuilder.stateOf(block);
    }

    @Test
    void theTemplateIsTheModsNineByNineteenByForty() {
        assertEquals(9, TEMPLATE.sizeX());
        assertEquals(19, TEMPLATE.sizeY());
        assertEquals(40, TEMPLATE.sizeZ());
        assertEquals(2424, TEMPLATE.blocks().size());
    }

    @Test
    void everyTemplateBlockIsPlacedWithTheModsBlocksMapped() {
        OverlayRegion region = new OverlayRegion(area(GROUND), 5L);
        Origin origin = originOn(GROUND);

        SiftMainPortalBuilder.Result result = SiftMainPortalBuilder.build(region, TEMPLATE, origin);

        assertEquals(0, result.rejectedWrites());
        assertEquals(TEMPLATE.blocks().size(), result.placedBlocks());
        for (TemplateBlock block : TEMPLATE.blocks()) {
            BlockStateSpec actual = region.state(origin.x() + block.x(), origin.y() + block.y(), origin.z() + block.z());
            BlockStateSpec wanted = expected(block);
            if (SiftTerrain.isGrowth(wanted)) {
                // Growth with a full block above it becomes plain siftslate.
                assertTrue(actual.equals(wanted) || actual.equals(SiftTerrain.SIFTSLATE), "growth at " + block);
            } else {
                assertEquals(wanted, actual, "template block " + block);
            }
        }
    }

    @Test
    void thePortalInteriorIsTheLightStandInAndTheMarkerIsReinforcedSiftslate() {
        OverlayRegion region = new OverlayRegion(area(GROUND), 5L);
        Origin origin = originOn(GROUND);

        SiftMainPortalBuilder.build(region, TEMPLATE, origin);

        int lights = 0;
        for (int y = 0; y < 19; y++) {
            for (int z = 0; z < 40; z++) {
                for (int x = 0; x < 9; x++) {
                    if (region.state(origin.x() + x, origin.y() + y, origin.z() + z).equals(LIGHT)) {
                        lights++;
                        assertEquals(3, x);
                        assertTrue(y >= 5 && y <= 10 && z >= 10 && z <= 29, "light at " + x + "," + y + "," + z);
                    }
                }
            }
        }
        assertEquals(120, lights, "a 20 wide, 6 high portal");
        assertEquals(SiftTerrain.REINFORCED_SIFTSLATE, region.state(origin.x() + 3, origin.y() + 4, origin.z() + 19),
            "the jigsaw marker");
        assertFalse(region.blockId(origin.x() + 3, origin.y() + 4, origin.z() + 19).contains("jigsaw"));
    }

    @Test
    void theArrivalPointIsInsideThePortalOnItsFloorFrame() {
        Origin origin = originOn(GROUND);
        Anchor anchor = origin.anchor();
        OverlayRegion region = new OverlayRegion(area(GROUND), 5L);

        SiftMainPortalBuilder.build(region, TEMPLATE, origin);

        assertEquals(LIGHT, region.state(anchor.x(), anchor.y(), anchor.z()));
        assertEquals(LIGHT, region.state(anchor.x(), anchor.y() + 1, anchor.z()), "room to stand");
        assertEquals(SiftTerrain.REINFORCED_SIFTSLATE, region.state(anchor.x(), anchor.y() - 1, anchor.z()));
    }

    @Test
    void groundAboveTheProfileIsCutDownInsideTheFootprint() {
        // The terrain is 8 blocks above the portal's floor, so the ground profile has to be cut into it.
        TestTerrain terrain = area(GROUND + 8);
        OverlayRegion region = new OverlayRegion(terrain, 5L);
        Origin origin = new Origin(10 - 3, GROUND, -20 - 19);

        SiftMainPortalBuilder.build(region, TEMPLATE, origin);

        int checked = 0;
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 40; z++) {
                int profile = SiftMainPortalBuilder.groundHeight(x, z);
                if (profile < 0 || templateReaches(x, z, profile)) {
                    continue;
                }
                assertEquals(origin.y() + profile, topTerrain(region, origin.x() + x, origin.z() + z, origin.y() + 40),
                    "terrain top at " + x + "," + z);
                checked++;
            }
        }
        assertTrue(checked > 100, "the check covers the open ground of the footprint: " + checked);
    }

    @Test
    void columnsOutsideTheProfileAreLeftAlone() {
        OverlayRegion region = new OverlayRegion(area(GROUND + 8), 5L);
        Origin origin = new Origin(10 - 3, GROUND, -20 - 19);

        SiftMainPortalBuilder.build(region, TEMPLATE, origin);

        int untouched = 0;
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 40; z++) {
                if (SiftMainPortalBuilder.groundHeight(x, z) >= 0 || templateHasBlockAbove(x, z, -1)) {
                    continue;
                }
                assertEquals(GROUND + 8, topTerrain(region, origin.x() + x, origin.z() + z, 255), "column " + x + "," + z);
                untouched++;
            }
        }
        assertTrue(untouched > 0);
    }

    @Test
    void lowGroundIsRaisedWithoutGapsUnderTheBase() {
        TestTerrain terrain = area(GROUND - 12);
        OverlayRegion region = new OverlayRegion(terrain, 5L);
        Origin origin = new Origin(10 - 3, GROUND, -20 - 19);

        SiftMainPortalBuilder.build(region, TEMPLATE, origin);

        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 40; z++) {
                if (!SiftMainPortalBuilder.isPortalBaseColumn(x, z)) {
                    continue;
                }
                for (int y = GROUND - 12; y < origin.y(); y++) {
                    assertFalse(region.isAir(origin.x() + x, y, origin.z() + z), "gap under the base at " + x + "," + y + "," + z);
                }
            }
        }
    }

    @Test
    void theBlendRaisesTheSurroundingsAndErodesThemWithDistance() {
        OverlayRegion region = new OverlayRegion(area(GROUND - 12), 5L);
        Origin origin = new Origin(10 - 3, GROUND, -20 - 19);

        SiftMainPortalBuilder.build(region, TEMPLATE, origin);

        // Beside the structure the ground has been raised towards the portal; far away it is untouched.
        int near = topTerrain(region, origin.x() - 3, origin.z() + 20, 255);
        int far = topTerrain(region, origin.x() - 51, origin.z() + 20, 255);
        assertTrue(near > GROUND - 12, "near the portal: " + near);
        assertTrue(far <= near, "erodes with distance: " + far + " vs " + near);
        assertEquals(GROUND - 12, topTerrain(region, origin.x() - 60, origin.z() + 20, 255), "beyond the blend radius");
    }

    @Test
    void treesAndPlantsAroundThePortalAreCleared() {
        // High ground, so that the blending does not build over the trees afterwards.
        int top = GROUND + 30;
        TestTerrain terrain = area(top);
        int treeX = 10 - 3 - 10;
        int treeZ = -20 - 19 + 20;
        for (int y = top + 1; y <= top + 6; y++) {
            terrain.put(treeX, y, treeZ, SiftTerrain.OVERGROWN_WILLOW_LOG);
        }
        terrain.put(treeX, top + 7, treeZ, SiftTerrain.OVERGROWN_WILLOW_FOLIAGE);
        terrain.put(treeX + 1, top + 1, treeZ, BlockStateSpec.of("minecraft:fern"));
        terrain.put(treeX + 2, top + 1, treeZ, BlockStateSpec.of("minecraft:vine", Map.of("north", "true")));
        OverlayRegion region = new OverlayRegion(terrain, 5L);

        SiftMainPortalBuilder.build(region, TEMPLATE, new Origin(10 - 3, GROUND, -20 - 19));

        for (int y = top + 1; y <= top + 8; y++) {
            assertEquals("minecraft:air", region.blockId(treeX, y, treeZ), "tree block at y " + y);
        }
        assertEquals("minecraft:air", region.blockId(treeX + 1, top + 1, treeZ));
        assertEquals("minecraft:air", region.blockId(treeX + 2, top + 1, treeZ));
        assertEquals(SiftTerrain.SIFTSLATE_GROWTH, region.state(treeX, top, treeZ), "the ground under the tree stays");
    }

    @Test
    void aBlockThatIsNeitherTerrainNorPlantStopsTheClearingOfItsColumn() {
        int top = GROUND + 30;
        TestTerrain terrain = area(top);
        int x = 10 - 3 - 8;
        int z = -20 - 19 + 25;
        terrain.put(x, top + 1, z, SiftTerrain.OVERGROWN_WILLOW_LOG);
        terrain.put(x, top + 2, z, BlockStateSpec.of("minecraft:chest"));
        terrain.put(x, top + 3, z, SiftTerrain.OVERGROWN_WILLOW_LOG);
        OverlayRegion region = new OverlayRegion(terrain, 5L);

        SiftMainPortalBuilder.build(region, TEMPLATE, new Origin(10 - 3, GROUND, -20 - 19));

        assertEquals("minecraft:air", region.blockId(x, top + 3, z), "plants above are cleared");
        assertEquals("minecraft:chest", region.blockId(x, top + 2, z), "the chest stays");
        assertEquals(SiftTerrain.OVERGROWN_WILLOW_LOG, region.state(x, top + 1, z), "so does what lies below it");
    }

    @Test
    void thePlantingSkipsTheArrivalStripButCoversTheRestOfTheFootprint() {
        OverlayRegion region = new OverlayRegion(area(GROUND), 11L);
        Origin origin = originOn(GROUND);
        Anchor anchor = origin.anchor();

        SiftMainPortalBuilder.build(region, TEMPLATE, origin);

        int inStrip = 0;
        int elsewhere = 0;
        for (int x = origin.x(); x < origin.x() + 9; x++) {
            for (int z = origin.z(); z < origin.z() + 40; z++) {
                for (int y = origin.y(); y < origin.y() + 19; y++) {
                    if (!PLANTS.contains(region.blockId(x, y, z))) {
                        continue;
                    }
                    if (SiftMainPortalBuilder.isPortalApproach(x, z, anchor)) {
                        inStrip++;
                    } else {
                        elsewhere++;
                    }
                }
            }
        }
        assertEquals(0, inStrip);
        assertTrue(elsewhere > 20, "plants on the ruin: " + elsewhere);
    }

    @Test
    void growthUnderAFullBlockBecomesSiftslate() {
        OverlayRegion region = new OverlayRegion(area(GROUND), 5L);
        Origin origin = originOn(GROUND);

        SiftMainPortalBuilder.build(region, TEMPLATE, origin);

        for (int x = origin.x(); x < origin.x() + 9; x++) {
            for (int z = origin.z(); z < origin.z() + 40; z++) {
                for (int y = origin.y(); y < origin.y() + 18; y++) {
                    if (SiftTerrain.isGrowth(region.state(x, y, z))) {
                        assertFalse(region.isSolid(x, y + 1, z), "covered growth at " + x + "," + y + "," + z);
                    }
                }
            }
        }
    }

    @Test
    void buildingIsDeterministicAndDependsOnTheSeed() {
        Origin origin = originOn(GROUND);
        OverlayRegion first = new OverlayRegion(area(GROUND - 6), 77L);
        OverlayRegion second = new OverlayRegion(area(GROUND - 6), 77L);
        OverlayRegion other = new OverlayRegion(area(GROUND - 6), 78L);

        SiftMainPortalBuilder.build(first, TEMPLATE, origin);
        SiftMainPortalBuilder.build(second, TEMPLATE, origin);
        SiftMainPortalBuilder.build(other, TEMPLATE, origin);

        assertEquals(first.changes(), second.changes());
        assertNotEquals(first.changes(), other.changes());
    }

    @Test
    void theBuildStaysInsideItsAnnouncedWindowAndLeavesTheSourceUntouched() {
        TestTerrain terrain = area(GROUND - 6);
        TestTerrain pristine = area(GROUND - 6);
        OverlayRegion region = new OverlayRegion(terrain, 5L);
        Origin origin = originOn(GROUND);
        Window window = SiftMainPortalSite.buildWindow(origin);

        SiftMainPortalBuilder.build(region, TEMPLATE, origin);

        List<OverlayRegion.Change> changes = region.changes();
        assertTrue(changes.size() > TEMPLATE.blocks().size(), "terrain work as well as the template: " + changes.size());
        for (OverlayRegion.Change change : changes) {
            assertTrue(change.x() >= window.minX() && change.x() <= window.maxX() && change.z() >= window.minZ()
                && change.z() <= window.maxZ(), "change outside the window: " + change);
        }
        for (int x = window.minX(); x <= window.maxX(); x += 7) {
            for (int z = window.minZ(); z <= window.maxZ(); z += 7) {
                for (int y = 0; y < 256; y += 5) {
                    assertEquals(pristine.state(x, y, z), terrain.state(x, y, z));
                }
            }
        }
    }

    @Test
    void blocksThatDoNotFitTheAreaAreReportedNotThrown() {
        // An area that ends in the middle of the portal.
        TestTerrain small = TestTerrain.flat(-5, 12, -60, -20, GROUND);
        OverlayRegion region = new OverlayRegion(small, 5L);

        SiftMainPortalBuilder.Result result = SiftMainPortalBuilder.build(region, TEMPLATE, new Origin(10 - 3, GROUND, -20 - 19));

        assertTrue(result.rejectedWrites() > 0);
        assertTrue(result.placedBlocks() + result.rejectedWrites() == TEMPLATE.blocks().size());
    }

    @Test
    void aTemplateOfTheWrongSizeIsRefused() {
        Template wrong = new Template(9, 19, 39, List.of());

        assertThrows(IllegalArgumentException.class,
            () -> SiftMainPortalBuilder.build(new OverlayRegion(area(GROUND), 1L), wrong, originOn(GROUND)));
    }

    @Test
    void theGroundProfileIsTheModsOwn() {
        // The mod's GROUND_PROFILE[x][z]: nothing at the corners, a low rim, the portal's floor at 3, the cliffs at 17.
        assertEquals(-1, SiftMainPortalBuilder.groundHeight(0, 0));
        assertEquals(0, SiftMainPortalBuilder.groundHeight(0, 4));
        assertEquals(1, SiftMainPortalBuilder.groundHeight(0, 5));
        assertEquals(17, SiftMainPortalBuilder.groundHeight(2, 5));
        assertEquals(3, SiftMainPortalBuilder.groundHeight(3, 20));
        assertEquals(-1, SiftMainPortalBuilder.groundHeight(-1, 5));
        assertEquals(-1, SiftMainPortalBuilder.groundHeight(9, 5));
        assertTrue(SiftMainPortalBuilder.isPortalBaseColumn(3, 20));
        assertFalse(SiftMainPortalBuilder.isPortalBaseColumn(0, 0));
    }

    @Test
    void theTerrainEnvelopeDecaysWithDistanceFromTheCliffProfile() {
        double beside = SiftMainPortalBuilder.portalTerrainEnvelope(-1, 20, 5L);
        double middle = SiftMainPortalBuilder.portalTerrainEnvelope(-20, 20, 5L);
        double far = SiftMainPortalBuilder.portalTerrainEnvelope(-50, 20, 5L);

        assertTrue(beside > 14.0, "the cliffs reach about 17 blocks: " + beside);
        assertTrue(beside > middle && middle > far, beside + " > " + middle + " > " + far);
        assertTrue(far < 4.0, "and have eroded at the blend radius: " + far);
        assertEquals(SiftMainPortalBuilder.portalTerrainEnvelope(-20, 20, 5L), middle, "pure function of position and seed");
    }

    @Test
    void theApproachMaskIsOneBesideThePortalAndZeroBesideTheMiddle() {
        assertEquals(0.0, SiftMainPortalBuilder.portalApproachMask(4, 5), "columns of the footprint have no forecourt");
        assertEquals(1.0, SiftMainPortalBuilder.portalApproachMask(-1, 20), 1e-12, "beside the portal's length");
        assertEquals(1.0, SiftMainPortalBuilder.portalApproachMask(-2, 9), 1e-12, "up to two blocks out");
        assertEquals(0.5, SiftMainPortalBuilder.portalApproachMask(-1, 6), 1e-12, "halfway along the fade past the portal's end");
        assertEquals(0.0, SiftMainPortalBuilder.portalApproachMask(-1, 3), 1e-12, "six blocks past its end");
        assertEquals(0.0, SiftMainPortalBuilder.portalApproachMask(-21, 20), 1e-12, "twenty blocks to the side");
    }

    private static boolean templateReaches(int x, int z, int profile) {
        return templateHasBlockAbove(x, z, profile);
    }

    private static boolean templateHasBlockAbove(int x, int z, int y) {
        return TEMPLATE.blocks().stream().anyMatch(block -> block.x() == x && block.z() == z && block.y() > y);
    }

    private static int topTerrain(OverlayRegion region, int x, int z, int from) {
        for (int y = Math.min(255, from); y >= 0; y--) {
            if (SiftTerrain.TERRAIN.contains(region.state(x, y, z))) {
                return y;
            }
        }
        return -1;
    }
}
