package dev.linqfy.bigCasares.modules.sift.feature;

import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverlayRegionTest {

    private static final BlockStateSpec STONE = BlockStateSpec.of("minecraft:stone");
    private static final BlockStateSpec AIR = BlockStateSpec.of("minecraft:air");

    private static OverlayRegion region() {
        return new OverlayRegion(TestTerrain.flat(0, 31, 0, 31, 64), 99L);
    }

    @Test
    void readsComeFromTheSourceUntilWritten() {
        OverlayRegion region = region();

        assertEquals(SiftTerrain.SIFTSLATE_GROWTH, region.state(5, 64, 5));
        assertEquals(SiftTerrain.SIFTSLATE, region.state(5, 10, 5));
        assertTrue(region.isAir(5, 65, 5));
        assertEquals(99L, region.seed());
    }

    @Test
    void writesAreVisibleToLaterReadsButLeaveTheSourceAlone() {
        TestTerrain source = TestTerrain.flat(0, 31, 0, 31, 64);
        OverlayRegion region = new OverlayRegion(source, 1L);

        assertTrue(region.set(5, 65, 5, STONE));

        assertEquals(STONE, region.state(5, 65, 5));
        assertEquals("minecraft:stone", region.blockId(5, 65, 5));
        assertEquals(AIR, source.state(5, 65, 5));
    }

    @Test
    void changesAreTheNetDifferenceToTheSource() {
        OverlayRegion region = region();
        region.set(5, 65, 5, STONE);
        region.set(6, 64, 6, SiftTerrain.SIFTSLATE_GROWTH);
        region.set(7, 64, 7, SiftTerrain.SIFTSLATE);
        region.set(8, 64, 8, STONE);
        region.set(8, 64, 8, SiftTerrain.SIFTSLATE_GROWTH);

        List<OverlayRegion.Change> changes = region.changes();

        assertEquals(List.of(new OverlayRegion.Change(7, 64, 7, SiftTerrain.SIFTSLATE), new OverlayRegion.Change(5, 65, 5, STONE)),
            changes);
    }

    @Test
    void changesAreGroupedByChunkThenBottomUp() {
        OverlayRegion region = region();
        region.set(20, 70, 2, STONE);
        region.set(3, 80, 3, STONE);
        region.set(3, 70, 3, STONE);
        region.set(2, 70, 20, STONE);

        List<OverlayRegion.Change> changes = region.changes();

        // Chunk (0,0) first, bottom up; then chunk (0,1) (z 16..31); then chunk (1,0) (x 16..31).
        assertEquals(List.of(new OverlayRegion.Change(3, 70, 3, STONE), new OverlayRegion.Change(3, 80, 3, STONE),
            new OverlayRegion.Change(2, 70, 20, STONE), new OverlayRegion.Change(20, 70, 2, STONE)), changes);
    }

    @Test
    void outsideTheAreaReadsAsAirAndRefusesWrites() {
        OverlayRegion region = region();

        assertFalse(region.set(40, 64, 5, STONE));
        assertFalse(region.set(5, 300, 5, STONE));
        assertFalse(region.set(5, -1, 5, STONE));
        assertEquals(AIR, region.state(40, 64, 5));
        assertFalse(region.isWritable(40, 64, 5));
        assertTrue(region.changes().isEmpty());
    }

    @Test
    void heightFollowsWritesOnTheColumn() {
        OverlayRegion region = region();
        assertEquals(65, region.height(WorldRegion.Heightmap.WORLD_SURFACE_WG, 5, 5));

        region.set(5, 70, 5, STONE);
        assertEquals(71, region.height(WorldRegion.Heightmap.WORLD_SURFACE_WG, 5, 5));

        region.set(5, 70, 5, AIR);
        assertEquals(65, region.height(WorldRegion.Heightmap.WORLD_SURFACE_WG, 5, 5));

        region.set(5, 64, 5, AIR);
        region.set(5, 63, 5, AIR);
        assertEquals(63, region.height(WorldRegion.Heightmap.WORLD_SURFACE_WG, 5, 5));
        assertEquals(0, region.height(WorldRegion.Heightmap.WORLD_SURFACE_WG, 99, 5), "outside the area: minY");
    }

    @Test
    void plantsAndLightAreNotSolidButStoneIs() {
        OverlayRegion region = region();
        region.set(5, 65, 5, BlockStateSpec.of("minecraft:short_grass"));
        region.set(6, 65, 6, BlockStateSpec.of("minecraft:light", java.util.Map.of("level", "12")));
        region.set(7, 65, 7, STONE);

        assertFalse(region.isSolid(5, 65, 5));
        assertFalse(region.isSolid(6, 65, 6));
        assertTrue(region.isSolid(7, 65, 7));
        assertTrue(region.isSolid(5, 64, 5), "source blocks answer from the source");
    }

    @Test
    void entitiesAndContainersAreRefused() {
        OverlayRegion region = region();

        assertFalse(region.spawn("minecraft:sniffer", 5, 65, 5));
        assertFalse(region.fillContainer(5, 65, 5, List.of()));
    }
}
