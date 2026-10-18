package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.dimension.travel.BlockGrid;
import dev.linqfy.bigCasares.dimension.travel.PortalDefinition;
import dev.linqfy.bigCasares.dimension.travel.PortalShape;
import dev.linqfy.bigCasares.dimension.travel.PortalShapeDetector;
import dev.linqfy.bigCasares.modules.sift.feature.OverlayRegion;
import dev.linqfy.bigCasares.modules.sift.feature.SiftMainPortalBuilder;
import dev.linqfy.bigCasares.modules.sift.feature.SiftMainPortalSite;
import dev.linqfy.bigCasares.modules.sift.feature.SiftMainPortalSite.Origin;
import dev.linqfy.bigCasares.modules.sift.feature.SiftTemplates;
import dev.linqfy.bigCasares.modules.sift.feature.TestTerrain;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The portal definition the Sift registers for the main portal against the structure the builder produces. */
class MainPortalStructureTest {

    private static final Origin ORIGIN = new Origin(10, 70, -20);
    private static PortalDefinition mainPortal;
    private static OverlayRegion built;

    @BeforeAll
    static void build() {
        mainPortal = portal(SiftSettings.defaults(), SiftDimension.MAIN_PORTAL).orElseThrow();
        built = new OverlayRegion(TestTerrain.flat(-300, 300, -300, 300, 70), 42L);
        SiftMainPortalBuilder.build(built, new SiftTemplates(SiftData.fromClasspath()).template(SiftMainPortalSite.TEMPLATE).orElseThrow(),
            ORIGIN);
    }

    private static Optional<PortalDefinition> portal(SiftSettings settings, String id) {
        return SiftDimension.travel(settings).portals().stream().filter(portal -> portal.id().equals(id)).findFirst();
    }

    private static BlockGrid grid(OverlayRegion region) {
        return new BlockGrid() {
            @Override
            public String blockId(int x, int y, int z) {
                return region.blockId(x, y, z);
            }

            @Override
            public boolean isSolidFloor(int x, int y, int z) {
                return region.isSolid(x, y, z);
            }

            @Override
            public boolean isPassable(int x, int y, int z) {
                return !region.isSolid(x, y, z);
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public int minY() {
                return 0;
            }

            @Override
            public int maxY() {
                return 255;
            }
        };
    }

    @Test
    void theMainPortalIsLitByCodeAndLeadsOutWithoutAReturnFrame() {
        assertFalse(mainPortal.itemActivated(), "nobody lights it with an item");
        assertFalse(mainPortal.createExitPortal(), "arriving through it would build a frame, leaving builds none");
        assertEquals(12, mainPortal.interiorLight(), "the mod's sift_portal light level");
        assertEquals(java.util.Set.of("minecraft:brown_mushroom_block"), mainPortal.frameBlocks());
    }

    @Test
    void theFrameworkDetectsTheBuiltPortalFromItsInteriorAndFromItsFrame() {
        PortalShapeDetector detector = mainPortal.detector();
        PortalShape expected = new PortalShape(PortalShape.Axis.Z, ORIGIN.x() + 3, ORIGIN.y() + 5, ORIGIN.z() + 10,
            ORIGIN.x() + 3, ORIGIN.y() + 10, ORIGIN.z() + 29);

        // The frame block SiftMainPortal lights through, the arrival point, and frame blocks all around the ring.
        List<int[]> starts = List.of(
            new int[]{ORIGIN.x() + 3, ORIGIN.y() + 4, ORIGIN.z() + 12},
            new int[]{ORIGIN.x() + 3, ORIGIN.y() + 5, ORIGIN.z() + 19},
            new int[]{ORIGIN.x() + 3, ORIGIN.y() + 11, ORIGIN.z() + 29},
            new int[]{ORIGIN.x() + 3, ORIGIN.y() + 7, ORIGIN.z() + 9},
            new int[]{ORIGIN.x() + 3, ORIGIN.y() + 8, ORIGIN.z() + 30});
        for (int[] start : starts) {
            assertEquals(Optional.of(expected), detector.detect(grid(built), start[0], start[1], start[2]),
                "from " + start[0] + "," + start[1] + "," + start[2]);
        }
    }

    @Test
    void theArrivalPointLiesInsideTheDetectedPortal() {
        PortalShape shape = mainPortal.detector().detect(grid(built), ORIGIN.x() + 3, ORIGIN.y() + 4, ORIGIN.z() + 12).orElseThrow();
        SiftMainPortalSite.Anchor anchor = ORIGIN.anchor();

        assertTrue(shape.contains(anchor.x(), anchor.y(), anchor.z()));
        // The framework's "lit portal within 24 blocks of the arrival" test, which keeps it from building a return frame.
        assertTrue(Math.abs(shape.centerX() - (anchor.x() + 0.5)) < 24 && Math.abs(shape.centerZ() - (anchor.z() + 0.5)) < 24);
    }

    @Test
    void aBrokenFrameIsNotDetected() {
        OverlayRegion damaged = new OverlayRegion(TestTerrain.flat(-300, 300, -300, 300, 70), 42L);
        SiftMainPortalBuilder.build(damaged, new SiftTemplates(SiftData.fromClasspath()).template(SiftMainPortalSite.TEMPLATE).orElseThrow(),
            ORIGIN);
        damaged.set(ORIGIN.x() + 3, ORIGIN.y() + 11, ORIGIN.z() + 20, dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec.of("minecraft:air"));

        assertEquals(Optional.empty(), mainPortal.detector().detect(grid(damaged), ORIGIN.x() + 3, ORIGIN.y() + 4, ORIGIN.z() + 12));
    }

    @Test
    void theAncientCityPortalKeepsItsReturnFrameAsTheFallbackBeforeTheMainPortalIsReady() {
        PortalDefinition ancientCity = portal(SiftSettings.defaults(), SiftDimension.ANCIENT_CITY_PORTAL).orElseThrow();
        PortalDefinition crafted = portal(SiftSettings.defaults(), "rift_frame").orElseThrow();

        assertTrue(ancientCity.createExitPortal());
        assertTrue(crafted.createExitPortal());
        assertEquals(java.util.Set.of("minecraft:reinforced_deepslate"), ancientCity.frameBlocks());
        assertTrue(crafted.itemActivated());
    }

    @Test
    void theMainPortalIsNotRegisteredWhenSwitchedOff() {
        SiftSettings defaults = SiftSettings.defaults();
        SiftSettings off = new SiftSettings(defaults.seedSalt(), defaults.worldBorderDiameter(), defaults.entryPermission(),
            defaults.travelCooldownSeconds(), defaults.craftedPortal(), defaults.ancientCityRitual(), defaults.tides(),
            defaults.ichor(), defaults.blubs(), defaults.rifts(), defaults.sifters(), defaults.singers(),
            defaults.echoGolems(), defaults.darkSniffers(), defaults.sculkflowers(), false, List.of());

        assertEquals(Optional.empty(), portal(off, SiftDimension.MAIN_PORTAL));
        assertTrue(portal(off, SiftDimension.ANCIENT_CITY_PORTAL).isPresent());
    }
}
