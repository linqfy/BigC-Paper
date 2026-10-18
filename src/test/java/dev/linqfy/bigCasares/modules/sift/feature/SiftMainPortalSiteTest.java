package dev.linqfy.bigCasares.modules.sift.feature;

import dev.linqfy.bigCasares.modules.sift.feature.SiftMainPortalSite.Anchor;
import dev.linqfy.bigCasares.modules.sift.feature.SiftMainPortalSite.Candidate;
import dev.linqfy.bigCasares.modules.sift.feature.SiftMainPortalSite.Heights;
import dev.linqfy.bigCasares.modules.sift.feature.SiftMainPortalSite.Origin;
import dev.linqfy.bigCasares.modules.sift.feature.SiftMainPortalSite.Window;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiftMainPortalSiteTest {

    private static final Heights FLAT = (x, z) -> 70;

    @Test
    void flatTerrainPutsThePortalAtTheCentre() {
        Candidate candidate = SiftMainPortalSite.find(FLAT);

        assertEquals(new Candidate(0, 0, 70, 0.0), candidate);
    }

    @Test
    void aFlatShelfBeatsRoughGroundNearTheCentre() {
        // Noisy ground everywhere except a shelf at height 90 to the north-east of the centre.
        Heights heights = (x, z) -> {
            if (x >= 30 && x <= 100 && z >= 30 && z <= 110) {
                return 90;
            }
            return 60 + Math.floorMod(x * 7 + z * 13, 9);
        };

        Candidate candidate = SiftMainPortalSite.find(heights);

        assertEquals(90, candidate.groundY());
        assertEquals(0.0, candidate.roughness());
        assertTrue(candidate.anchorX() - 15 >= 30 && candidate.anchorX() + 17 <= 100, "footprint and ring on the shelf: " + candidate);
        assertTrue(candidate.anchorZ() - 31 >= 30 && candidate.anchorZ() + 32 <= 110, "footprint and ring on the shelf: " + candidate);
    }

    @Test
    void ofTwoEquallyFlatAreasTheNearerOneWins() {
        Heights heights = (x, z) -> x >= 0 ? 80 : 50;
        Heights east = (x, z) -> x >= 20 ? 80 : 50 + Math.floorMod(x + z, 5);

        Candidate near = SiftMainPortalSite.find(heights);
        Candidate far = SiftMainPortalSite.find(east);

        assertTrue(Math.hypot(near.anchorX(), near.anchorZ()) <= Math.hypot(far.anchorX(), far.anchorZ()));
        assertEquals(80, far.groundY());
    }

    @Test
    void theFineSearchRefinesAroundTheCoarseWinnerInSteps() {
        // A flat pocket 3 blocks wide of margin around anchor (36, 20): only a step-4 grid point reaches it.
        Heights heights = (x, z) -> {
            boolean pocket = x >= 36 - 15 && x <= 36 + 17 && z >= 20 - 31 && z <= 20 + 32;
            return pocket ? 77 : 77 + 1 + Math.floorMod(x * 3 + z * 5, 4);
        };

        Candidate candidate = SiftMainPortalSite.find(heights);

        assertEquals(77, candidate.groundY());
        assertEquals(0.0, candidate.roughness());
        assertEquals(0, Math.floorMod(candidate.anchorX(), 4), "refined positions are on the fine grid");
        assertEquals(0, Math.floorMod(candidate.anchorZ(), 4), "refined positions are on the fine grid");
    }

    @Test
    void groundOutsideTheBuildableHeightsIsAvoided() {
        // Everything near the centre is at y=5 (below the minimum of 12); a plateau at y=100 lies far away.
        Heights heights = (x, z) -> x >= 80 && z >= 60 ? 100 : 5;

        Candidate candidate = SiftMainPortalSite.find(heights);

        assertEquals(100, candidate.groundY());
    }

    @Test
    void whenNothingIsFlatTheLeastBadCandidateIsStillChosen() {
        Random random = new Random(7);
        int[][] noise = new int[400][400];
        for (int[] row : noise) {
            for (int index = 0; index < row.length; index++) {
                row[index] = 40 + random.nextInt(40);
            }
        }
        Heights heights = (x, z) -> noise[x + 200][z + 200];

        Candidate candidate = SiftMainPortalSite.find(heights);

        assertTrue(candidate.roughness() > 0.0);
        assertTrue(Math.abs(candidate.anchorX()) <= 112 && Math.abs(candidate.anchorZ()) <= 112);
    }

    @Test
    void theSampleUsesNineFootprintHeightsAndAnOuterRing() {
        List<int[]> asked = new ArrayList<>();
        Heights recording = (x, z) -> {
            asked.add(new int[]{x, z});
            return 64;
        };

        SiftMainPortalSite.TerrainSample sample = SiftMainPortalSite.sample(recording, 100, 200);

        assertEquals(17, asked.size());
        assertEquals(64, sample.medianHeight());
        assertEquals(0, sample.range());
        assertEquals(0, sample.outerRange());
        // Footprint: x 97..105, z 181..220; its middles are 101 and 200 (the sum 401 truncates).
        assertTrue(contains(asked, 97, 181) && contains(asked, 101, 181) && contains(asked, 105, 181));
        assertTrue(contains(asked, 97, 200) && contains(asked, 101, 200) && contains(asked, 105, 200));
        assertTrue(contains(asked, 97, 220) && contains(asked, 101, 220) && contains(asked, 105, 220));
        // Ring 12 blocks out: x 85..117, z 169..232, middles 101 and 200; the centre of the ring is not sampled.
        assertTrue(contains(asked, 85, 169) && contains(asked, 101, 169) && contains(asked, 117, 169));
        assertTrue(contains(asked, 85, 200) && contains(asked, 117, 200));
        assertTrue(contains(asked, 85, 232) && contains(asked, 101, 232) && contains(asked, 117, 232));
        assertEquals(false, contains(asked, 101, 201));
    }

    @Test
    void middlesTruncateTowardsZeroLikeTheMods() {
        List<int[]> asked = new ArrayList<>();

        SiftMainPortalSite.sample((x, z) -> {
            asked.add(new int[]{x, z});
            return 64;
        }, 0, -1);

        // Footprint z -20..19: (-20 + 19) / 2 is 0 in Java, not the -1 that floor division gives.
        assertTrue(contains(asked, -3, 0));
        assertEquals(false, contains(asked, -3, -1));
    }

    @Test
    void roughnessAndRangesFollowTheMedian() {
        // Core heights 60 except one corner at 70; outer ring flat at 60 except one point at 66.
        Heights heights = (x, z) -> {
            if (x == 97 && z == 181) {
                return 70;
            }
            if (x == 85 && z == 169) {
                return 66;
            }
            return 60;
        };

        SiftMainPortalSite.TerrainSample sample = SiftMainPortalSite.sample(heights, 100, 200);

        assertEquals(60, sample.medianHeight());
        assertEquals(10, sample.range());
        assertEquals(10, sample.outerRange());
        assertEquals((10 + 6 * 0.45) / (9 + 8 * 0.45), sample.roughness(), 1e-12);
    }

    @Test
    void scoreCountsRoughnessAndDistanceFromTheCentre() {
        Candidate candidate = new Candidate(30, 40, 64, 2.0);

        assertEquals(2.0 * 18.0 + 50.0 * 0.8, SiftMainPortalSite.score(candidate, 30, 40), 1e-12);
    }

    @Test
    void everyColumnTheSearchReadsLiesInsideTheAnnouncedWindow() {
        List<int[]> asked = new ArrayList<>();
        Random random = new Random(3);

        SiftMainPortalSite.find((x, z) -> {
            asked.add(new int[]{x, z});
            return 60 + random.nextInt(30);
        });

        Window window = SiftMainPortalSite.searchWindow();
        for (int[] column : asked) {
            assertTrue(column[0] >= window.minX() && column[0] <= window.maxX(), "x " + column[0]);
            assertTrue(column[1] >= window.minZ() && column[1] <= window.maxZ(), "z " + column[1]);
        }
        assertEquals(17 * 19, window.chunkCount());
        assertEquals(-8, window.minChunkX());
        assertEquals(8, window.maxChunkX());
    }

    @Test
    void originPutsTheFrameRowOnTheGroundAndTheAnchorInThePortal() {
        Candidate candidate = new Candidate(50, -30, 72, 0.5);

        Origin origin = SiftMainPortalSite.origin(candidate, 0, 256);

        assertEquals(new Origin(47, 72, -49), origin);
        assertEquals(new Anchor(50, 77, -30), origin.anchor());
        assertEquals(origin, origin.anchor().origin());
    }

    @Test
    void originStaysInsideTheBuildHeight() {
        assertEquals(237, SiftMainPortalSite.origin(new Candidate(0, 0, 250, 0.0), 0, 256).y());
        assertEquals(0, SiftMainPortalSite.origin(new Candidate(0, 0, -5, 0.0), 0, 256).y());
    }

    @Test
    void buildWindowCoversTheFootprintAndTheBlendRadius() {
        Window window = SiftMainPortalSite.buildWindow(new Origin(47, 72, -49));

        assertEquals(47 - 52, window.minX());
        assertEquals(47 + 8 + 52, window.maxX());
        assertEquals(-49 - 52, window.minZ());
        assertEquals(-49 + 39 + 52, window.maxZ());
    }

    @Test
    void generatedSurfaceSkipsEverythingThatIsNotTerrain() {
        TestTerrain terrain = TestTerrain.flat(-16, 16, -16, 16, 64);
        terrain.put(0, 65, 0, SiftTerrain.OVERGROWN_WILLOW_LOG).put(0, 66, 0, SiftTerrain.OVERGROWN_WILLOW_LOG)
            .put(0, 67, 0, SiftTerrain.OVERGROWN_WILLOW_FOLIAGE)
            .put(1, 65, 0, dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec.of("minecraft:short_grass"))
            .put(2, 65, 0, dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec.of("minecraft:water"));
        OverlayRegion region = new OverlayRegion(terrain, 1L);

        assertEquals(64, SiftMainPortalSite.generatedSurface(region, 0, 0));
        assertEquals(64, SiftMainPortalSite.generatedSurface(region, 1, 0));
        assertEquals(64, SiftMainPortalSite.generatedSurface(region, 2, 0));
        assertEquals(64, SiftMainPortalSite.generatedSurface(region, 5, 5));
    }

    private static boolean contains(List<int[]> columns, int x, int z) {
        return columns.stream().anyMatch(column -> column[0] == x && column[1] == z);
    }
}
