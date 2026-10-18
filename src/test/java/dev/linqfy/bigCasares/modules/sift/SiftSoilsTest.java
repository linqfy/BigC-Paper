package dev.linqfy.bigCasares.modules.sift;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiftSoilsTest {

    @Test
    void growthDiesUnderFullBlocksAndFluidsAndTurnsSnowyUnderSnow() {
        assertEquals(Optional.of(SiftSoils.SIFTSLATE), SiftSoils.covered(SiftSoils.GROWTH, true, false, false));
        assertEquals(Optional.of(SiftSoils.SIFTSLATE), SiftSoils.covered(SiftSoils.GROWTH, false, true, false));
        assertEquals(Optional.of(SiftSoils.GROWTH_SNOWY), SiftSoils.covered(SiftSoils.GROWTH, false, false, true));
        assertEquals(Optional.of(SiftSoils.GROWTH), SiftSoils.covered(SiftSoils.GROWTH_SNOWY, false, false, false),
            "snow removed");
        assertEquals(Optional.empty(), SiftSoils.covered(SiftSoils.GROWTH, false, false, false));
        assertEquals(Optional.empty(), SiftSoils.covered(SiftSoils.GROWTH_SNOWY, false, false, true));
    }

    @Test
    void dryGrowthDiesUnderFullBlocksAndFluids() {
        assertEquals(Optional.of(SiftSoils.DRY_SCULK), SiftSoils.covered(SiftSoils.DRY_GROWTH, true, false, false));
        assertEquals(Optional.of(SiftSoils.DRY_SCULK), SiftSoils.covered(SiftSoils.DRY_GROWTH, false, true, false));
        assertEquals(Optional.empty(), SiftSoils.covered(SiftSoils.DRY_GROWTH, false, false, true));
        assertEquals(Optional.empty(), SiftSoils.covered(SiftSoils.SIFTSLATE, true, false, false));
    }

    @Test
    void growthDiesInTheDarkAndNotBeforeLightFour() {
        assertTrue(SiftSoils.tooDark(0));
        assertTrue(SiftSoils.tooDark(3));
        assertFalse(SiftSoils.tooDark(4), "the mod: getMaxLocalRawBrightness(above) < 4");
        assertFalse(SiftSoils.tooDark(15));
        assertEquals(Optional.of(SiftSoils.SIFTSLATE), SiftSoils.covered(SiftSoils.GROWTH, false, false, false, true));
        assertEquals(Optional.of(SiftSoils.SIFTSLATE), SiftSoils.covered(SiftSoils.GROWTH_SNOWY, false, false, false, true),
            "snowy growth that lost its snow in the dark dies too");
        assertEquals(Optional.of(SiftSoils.DRY_SCULK), SiftSoils.covered(SiftSoils.DRY_GROWTH, false, false, false, true));
        assertEquals(Optional.empty(), SiftSoils.covered(SiftSoils.GROWTH, false, false, false, false));
    }

    @Test
    void snowKeepsTheSnowyFaceWhateverTheLight() {
        assertEquals(Optional.of(SiftSoils.GROWTH_SNOWY), SiftSoils.covered(SiftSoils.GROWTH, false, false, true, true));
        assertEquals(Optional.empty(), SiftSoils.covered(SiftSoils.GROWTH_SNOWY, false, false, true, true));
    }

    @Test
    void siftslateAndOtherSoilsNeverChangeInTheDark() {
        assertEquals(Optional.empty(), SiftSoils.covered(SiftSoils.SIFTSLATE, false, false, false, true));
        assertEquals(Optional.empty(), SiftSoils.covered(SiftSoils.DRY_SCULK, true, true, false, true));
        assertEquals(Optional.empty(), SiftSoils.covered("healthy_sculk", true, true, false, true));
    }

    @Test
    void soilIdsAreRealSiftBlocks() {
        for (String path : SiftSoils.SIFT_FLOORS) {
            assertTrue(SiftBlocks.custom(path).isPresent() || SiftBlocks.definitions().stream()
                .anyMatch(block -> block.id().path().equals(path)), path);
        }
    }
}
