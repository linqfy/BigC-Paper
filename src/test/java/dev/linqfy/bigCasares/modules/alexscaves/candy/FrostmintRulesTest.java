package dev.linqfy.bigCasares.modules.alexscaves.candy;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrostmintRulesTest {
    @Test
    void bottomAndDoubleFallWhenTheBlockBelowIsFree() {
        assertTrue(FrostmintRules.falls(FrostmintRules.BOTTOM, true, false));
        assertTrue(FrostmintRules.falls(FrostmintRules.DOUBLE, true, false));
        assertFalse(FrostmintRules.falls(FrostmintRules.BOTTOM, false, false));
        assertFalse(FrostmintRules.falls(FrostmintRules.DOUBLE, false, false));
    }

    @Test
    void topSlabsHangUnlessAProjectileBringsTheColumnDown() {
        assertFalse(FrostmintRules.falls(FrostmintRules.TOP, true, false), "a hanging slab stays");
        assertFalse(FrostmintRules.falls(FrostmintRules.TOP, false, false));
        assertTrue(FrostmintRules.falls(FrostmintRules.TOP, true, true));
        assertTrue(FrostmintRules.falls(FrostmintRules.TOP, false, true), "a hit top slab at the bottom of a column comes down");
        assertTrue(FrostmintRules.falls(FrostmintRules.DOUBLE, true, true));
        assertFalse(FrostmintRules.falls(FrostmintRules.DOUBLE, false, true), "a column resting on the ground holds");
    }

    @Test
    void freeBlocksAreAirFireFluidsAndReplaceables() {
        assertTrue(FrostmintRules.isFree(Material.AIR, m -> false));
        assertTrue(FrostmintRules.isFree(Material.CAVE_AIR, m -> false));
        assertTrue(FrostmintRules.isFree(Material.WATER, m -> false));
        assertTrue(FrostmintRules.isFree(Material.LAVA, m -> false));
        assertTrue(FrostmintRules.isFree(Material.FIRE, m -> false));
        assertTrue(FrostmintRules.isFree(Material.SHORT_GRASS, m -> m == Material.SHORT_GRASS));
        assertFalse(FrostmintRules.isFree(Material.STONE, m -> false));
    }

    @Test
    void theColumnEndsAtTheLowestFrostmint() {
        // Frostmint at y=3..7, something else at 2.
        assertEquals(3, FrostmintRules.columnBottom(6, -64, y -> y >= 3 && y <= 7));
        assertEquals(5, FrostmintRules.columnBottom(5, -64, y -> y >= 5 && y <= 7));
        assertEquals(-63, FrostmintRules.columnBottom(0, -64, y -> y > -64), "the world floor ends it");
    }

    @Test
    void slabsMergeOnTheirOpenFace() {
        assertTrue(FrostmintRules.merges(FrostmintRules.BOTTOM, true, false));
        assertFalse(FrostmintRules.merges(FrostmintRules.BOTTOM, false, true));
        assertTrue(FrostmintRules.merges(FrostmintRules.TOP, false, true));
        assertFalse(FrostmintRules.merges(FrostmintRules.TOP, true, false));
        assertFalse(FrostmintRules.merges(FrostmintRules.DOUBLE, true, false));
        assertFalse(FrostmintRules.merges(FrostmintRules.BOTTOM, false, false), "side faces never merge");
    }

    @Test
    void aSlabGoesInTheUpperHalfWhenClickedFromBelowOrHigh() {
        assertTrue(FrostmintRules.upperHalf(false, true, 0.0));
        assertFalse(FrostmintRules.upperHalf(true, false, 0.9));
        assertTrue(FrostmintRules.upperHalf(false, false, 0.75));
        assertFalse(FrostmintRules.upperHalf(false, false, 0.25));
        assertFalse(FrostmintRules.upperHalf(false, false, 0.5));
    }

    @Test
    void rockCandyBlobsAreCyanInsideAndWhiteOutside() {
        // Sizes of 1 each: radius 3 * 0.333 + 0.5 = 1.499, squared about 2.25.
        double radiusSquared = FrostmintRules.blobRadiusSquared(1, 1, 1);
        assertEquals(2.247, radiusSquared, 0.01);
        assertTrue(FrostmintRules.cyanRockCandy(1.0, radiusSquared));
        assertFalse(FrostmintRules.cyanRockCandy(2.0, radiusSquared));
        assertTrue(FrostmintRules.blobRadiusSquared(2, 2, 2) > FrostmintRules.blobRadiusSquared(1, 1, 2));
    }

    @Test
    void hangingColumnsRunTwoToFiveStepsBelowTheirStart() {
        assertEquals(2, FrostmintRules.hangingLength(0));
        assertEquals(5, FrostmintRules.hangingLength(3));
    }
}
