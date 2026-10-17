package dev.linqfy.bigCasares.modules.sift;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiftPlacementTest {

    /** Flat ground at {@code groundY} (top solid block), with optional extra solid blocks. */
    private static final class Flat implements RiftPlacement.Terrain {
        private final int groundY;
        private final Set<String> solids = new HashSet<>();
        private final int border;

        Flat(int groundY, int border) {
            this.groundY = groundY;
            this.border = border;
        }

        Flat solid(int x, int y, int z) {
            solids.add(x + "," + y + "," + z);
            return this;
        }

        @Override public int minY() { return 0; }
        @Override public int maxY() { return 256; }
        @Override public int surface(int x, int z) { return groundY + 1; }
        @Override public boolean clear(int x, int y, int z) { return y > groundY && !solids.contains(x + "," + y + "," + z); }
        @Override public boolean usable(int x, int z) { return Math.abs(x) < border && Math.abs(z) < border; }
    }

    @Test
    void opensOnOpenGroundAboveTheSurface() {
        Optional<RiftPlacement.Spot> spot = RiftPlacement.column(new Flat(63, 1000), 10, 10, true);
        assertEquals(Optional.of(new RiftPlacement.Spot(10, 64, 10, true)), spot);
    }

    @Test
    void turnsOrRisesWhenTheVolumeIsBlocked() {
        // A pillar in the X span forces the Z axis.
        Flat terrain = new Flat(63, 1000).solid(13, 65, 10);
        assertEquals(Optional.of(new RiftPlacement.Spot(10, 64, 10, false)), RiftPlacement.column(terrain, 10, 10, true));
        // A wall across both spans at y 64..68 is impassable.
        Flat walled = new Flat(63, 1000);
        for (int y = 64; y <= 70; y++) {
            walled.solid(10, y, 10);
        }
        assertTrue(RiftPlacement.column(walled, 10, 10, true).isEmpty());
    }

    @Test
    void needsGroundWithinThreeBlocksAndStaysInsideTheBorder() {
        assertFalse(RiftPlacement.space(new Flat(63, 1000), 0, 68, 0, true), "floating four blocks up");
        assertTrue(RiftPlacement.space(new Flat(63, 1000), 0, 66, 0, true));
        assertFalse(RiftPlacement.space(new Flat(63, 8), 4, 64, 0, true), "span crosses the border");
        assertFalse(RiftPlacement.space(new Flat(250, 1000), 0, 251, 0, true), "too close to the build limit");
    }

    @Test
    void naturalRiftsOpenTwentyToSixtyFourBlocksAwayDeterministically() {
        Flat terrain = new Flat(63, 1000);
        RiftPlacement.Spot first = RiftPlacement.findNatural(terrain, 0, 0, new Random(5)).orElseThrow();
        RiftPlacement.Spot again = RiftPlacement.findNatural(terrain, 0, 0, new Random(5)).orElseThrow();
        assertEquals(first, again);
        double distance = Math.hypot(first.x(), first.z());
        assertTrue(distance >= 18 && distance <= 66, "distance " + distance);
    }

    @Test
    void exitRiftsSearchRingsAroundTheAnchor() {
        Flat terrain = new Flat(63, 1000);
        // A tall block around the anchor: rising two blocks onto it is not enough.
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                for (int y = 64; y <= 72; y++) {
                    terrain.solid(x, y, z);
                }
            }
        }
        RiftPlacement.Spot spot = RiftPlacement.findNear(terrain, 0, 0, 64).orElseThrow();
        assertEquals(16, Math.round(Math.hypot(spot.x(), spot.z())), 1.0);
        assertTrue(RiftPlacement.findNear(new Flat(63, 2), 0, 0, 64).isEmpty(), "nothing usable");
    }

    @Test
    void touchUsesThePortalBounds() {
        RiftPlacement.Spot spot = new RiftPlacement.Spot(0, 64, 0, true);
        assertTrue(spot.touches(4.0, 64.0, 0.2, 4.6, 65.8, 0.8), "inside the long span");
        assertFalse(spot.touches(5.1, 64.0, 0.2, 5.7, 65.8, 0.8), "past the end");
        assertFalse(spot.touches(0.2, 64.0, 1.2, 0.8, 65.8, 1.8), "beside the thin side");
    }
}
