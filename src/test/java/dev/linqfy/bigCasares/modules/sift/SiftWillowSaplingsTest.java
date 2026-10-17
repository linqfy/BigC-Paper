package dev.linqfy.bigCasares.modules.sift;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiftWillowSaplingsTest {

    @Test
    void positionsPackLikeVanillaBlockPositions() {
        // BlockPos.asLong(1, 2, 3) in vanilla.
        assertEquals((1L << 38) | (3L << 12) | 2L, SiftWillowSaplings.pack(1, 2, 3));
        assertNotEquals(SiftWillowSaplings.pack(-1, -64, -1), SiftWillowSaplings.pack(1, -64, 1));
        assertNotEquals(SiftWillowSaplings.pack(0, -64, 0), SiftWillowSaplings.pack(0, 319, 0));
    }

    @Test
    void trackingAddsOnceAndRemoves() {
        long a = SiftWillowSaplings.pack(10, 70, -5);
        long b = SiftWillowSaplings.pack(11, 70, -5);
        long[] planted = SiftWillowSaplings.with(SiftWillowSaplings.with(new long[0], a), b);
        assertArrayEquals(planted, SiftWillowSaplings.with(planted, a), "a position is kept once");
        assertTrue(SiftWillowSaplings.contains(planted, a));
        long[] grown = SiftWillowSaplings.without(planted, a);
        assertFalse(SiftWillowSaplings.contains(grown, a));
        assertTrue(SiftWillowSaplings.contains(grown, b));
    }
}
