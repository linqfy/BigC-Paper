package dev.linqfy.bigCasares.dimension.travel;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TravelTest {

    private static PortalShapeDetector obsidianDetector(int maxWidth, int maxHeight) {
        return new PortalDefinition("test", Set.of("minecraft:obsidian"), "minecraft:flint_and_steel", false,
            2, maxWidth, 3, maxHeight, false, 0, null, null).detector();
    }

    /** A frame in the X/Y plane at z = 0 with the given interior size, interior starting at (1, 1). */
    private static GridFixture frame(int width, int height) {
        GridFixture grid = new GridFixture(-64, 320);
        for (int x = 0; x <= width + 1; x++) {
            grid.set(x, 0, 0, "minecraft:obsidian").set(x, height + 1, 0, "minecraft:obsidian");
        }
        for (int y = 0; y <= height + 1; y++) {
            grid.set(0, y, 0, "minecraft:obsidian").set(width + 1, y, 0, "minecraft:obsidian");
        }
        return grid;
    }

    @Test
    void detectsAFrameFromAnyFrameBlockOrInteriorBlock() {
        GridFixture grid = frame(3, 4);
        PortalShape expected = new PortalShape(PortalShape.Axis.X, 1, 1, 0, 3, 4, 0);
        assertEquals(Optional.of(expected), obsidianDetector(21, 21).detect(grid, 0, 2, 0));
        assertEquals(Optional.of(expected), obsidianDetector(21, 21).detect(grid, 2, 0, 0));
        assertEquals(Optional.of(expected), obsidianDetector(21, 21).detect(grid, 2, 3, 0));
        assertEquals(3, expected.width());
        assertEquals(4, expected.height());
    }

    @Test
    void rejectsIncompleteBlockedOrOversizedFrames() {
        assertTrue(obsidianDetector(21, 21).detect(frame(3, 4).set(4, 2, 0, "minecraft:air"), 0, 2, 0).isEmpty());
        assertTrue(obsidianDetector(21, 21).detect(frame(3, 4).set(2, 2, 0, "minecraft:stone"), 0, 2, 0).isEmpty());
        assertTrue(obsidianDetector(2, 21).detect(frame(3, 4), 0, 2, 0).isEmpty());
    }

    @Test
    void anAncientCitySizedFrameWithSculkVeinsIsDetected() {
        GridFixture grid = new GridFixture(-64, 320);
        for (int z = 0; z <= 21; z++) {
            grid.set(0, 0, z, "minecraft:reinforced_deepslate").set(0, 7, z, "minecraft:reinforced_deepslate");
        }
        for (int y = 0; y <= 7; y++) {
            grid.set(0, y, 0, "minecraft:reinforced_deepslate").set(0, y, 21, "minecraft:reinforced_deepslate");
        }
        grid.set(0, 1, 5, "minecraft:sculk_vein").set(0, 6, 12, "minecraft:glow_lichen");
        PortalShapeDetector detector = new PortalDefinition("ancient_city", Set.of("minecraft:reinforced_deepslate"), "",
            false, 3, 21, 3, 21, true, 11, null, null).detector();
        PortalShape shape = detector.detect(grid, 0, 0, 10).orElseThrow();
        assertEquals(PortalShape.Axis.Z, shape.axis());
        assertEquals(20, shape.width());
        assertEquals(6, shape.height());
    }

    @Test
    void scriptedPortalsAreNotItemActivated() {
        PortalDefinition scripted = new PortalDefinition("ritual", Set.of("minecraft:obsidian"), "", false,
            2, 4, 3, 4, false, 0, null, null);
        assertFalse(scripted.itemActivated());
        assertThrows(IllegalArgumentException.class, () -> new PortalDefinition("Bad Id", Set.of("minecraft:obsidian"),
            "", false, 2, 4, 3, 4, false, 0, null, null));
        assertThrows(IllegalArgumentException.class, () -> new PortalDefinition("empty", Set.of(), "", false,
            2, 4, 3, 4, false, 0, null, null));
    }

    @Test
    void coordinateMappingRoundTripsAndClampsToTheBorder() {
        CoordinateMapping nether = new CoordinateMapping(0.125, 100.0, -50.0);
        double[] there = nether.toDimension(800.0, -400.0);
        assertArrayEquals(new double[]{800.0, -400.0}, nether.fromDimension(there[0], there[1]), 1.0E-9);
        assertArrayEquals(new double[]{120.0, 50.0},
            CoordinateMapping.clampToBorder(500.0, 50.0, 0.0, 0.0, 256.0, 8.0), 1.0E-9);
        assertThrows(IllegalArgumentException.class, () -> new CoordinateMapping(0.0, 0.0, 0.0));
    }

    @Test
    void safeLocationsNeedFloorAndHeadroomAndAvoidHazards() {
        GridFixture grid = new GridFixture(0, 128)
            .fill(-5, 0, -5, 5, 63, 5, "minecraft:stone")
            .set(0, 63, 0, "minecraft:lava");
        Optional<SafeLocationFinder.Spot> spot = new SafeLocationFinder(4).find(grid, 0, 0, 64);
        assertTrue(spot.isPresent());
        assertEquals(64, spot.get().y());
        assertFalse(spot.get().x() == 0 && spot.get().z() == 0, "must not stand on lava");
        assertTrue(Math.abs(spot.get().x()) <= 1 && Math.abs(spot.get().z()) <= 1, "nearest ring first");
        assertTrue(new SafeLocationFinder(2).find(new GridFixture(0, 128), 0, 0, 64).isEmpty(), "no floor anywhere");
        assertTrue(SafeLocationFinder.isSafe(grid, 2, 64, 2));
        assertFalse(SafeLocationFinder.isSafe(grid.set(2, 65, 2, "minecraft:stone"), 2, 64, 2), "no headroom");
        assertThrows(IllegalArgumentException.class, () -> new SafeLocationFinder(1000));
    }

    /** Found on a live server: a rift aimed into the main portal put players on the structure's roof. */
    @Test
    void aSafeRequestedSpotIsKeptUnderARoof() {
        GridFixture grid = new GridFixture(0, 128)
            .fill(-5, 0, -5, 5, 63, 5, "minecraft:stone")
            .fill(-5, 79, -5, 5, 79, 5, "minecraft:stone")
            .fill(0, 64, 0, 0, 69, 0, "minecraft:light");

        assertEquals(Optional.of(new SafeLocationFinder.Spot(0, 64, 0)), new SafeLocationFinder(4).find(grid, 0, 0, 64));
        assertEquals(80, new SafeLocationFinder(4).find(grid.set(0, 65, 0, "minecraft:stone"), 0, 0, 64).orElseThrow().y(),
            "an unsafe request still searches the column from above");
    }

    @Test
    void cooldownsExpire() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-16T12:00:00Z"));
        Clock clock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        TravelCooldowns cooldowns = new TravelCooldowns(clock);
        UUID player = UUID.randomUUID();
        assertEquals(Duration.ZERO, cooldowns.remaining(player, Duration.ofSeconds(5)));
        cooldowns.record(player);
        assertEquals(Duration.ofSeconds(5), cooldowns.remaining(player, Duration.ofSeconds(5)));
        now.set(now.get().plusSeconds(3));
        assertEquals(Duration.ofSeconds(2), cooldowns.remaining(player, Duration.ofSeconds(5)));
        now.set(now.get().plusSeconds(3));
        assertEquals(Duration.ZERO, cooldowns.remaining(player, Duration.ofSeconds(5)));
    }
}
