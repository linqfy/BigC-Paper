package dev.linqfy.bigCasares.modules.sift;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SiftIchorSnowfallTest {

    private static final String PEAKS = SiftIchorSnowfall.BIOME;

    @Test
    void snowSettlesOnlyOnIchorSnowyPeaksInLowBlockLight() {
        assertEquals(OptionalInt.of(1), SiftIchorSnowfall.layersAfterSnowfall(PEAKS, 0, 0, true, 1));
        assertEquals(OptionalInt.empty(), SiftIchorSnowfall.layersAfterSnowfall("the_sift:siftslate_peaks", 0, 0, true, 1));
        assertEquals(OptionalInt.of(1), SiftIchorSnowfall.layersAfterSnowfall(PEAKS, 9, 0, true, 1));
        assertEquals(OptionalInt.empty(), SiftIchorSnowfall.layersAfterSnowfall(PEAKS, 10, 0, true, 1));
        assertEquals(OptionalInt.empty(), SiftIchorSnowfall.layersAfterSnowfall(PEAKS, 0, -1, true, 1), "only air or snow");
        assertEquals(OptionalInt.empty(), SiftIchorSnowfall.layersAfterSnowfall(PEAKS, 0, 0, false, 1), "snow must lie");
    }

    @Test
    void layersFollowTheGameRuleUpToTwo() {
        assertEquals(OptionalInt.empty(), SiftIchorSnowfall.layersAfterSnowfall(PEAKS, 0, 1, true, 1), "vanilla default");
        assertEquals(OptionalInt.of(2), SiftIchorSnowfall.layersAfterSnowfall(PEAKS, 0, 1, true, 5));
        assertEquals(OptionalInt.empty(), SiftIchorSnowfall.layersAfterSnowfall(PEAKS, 0, 2, true, 5), "the mod's cap");
        assertEquals(OptionalInt.of(2), SiftIchorSnowfall.layersAfterSnowfall(PEAKS, 0, 4, true, 8), "thicker snow is cut to two");
        assertEquals(OptionalInt.of(1), SiftIchorSnowfall.layersAfterSnowfall(PEAKS, 0, 3, true, 0), "no accumulation resets");
    }
}
