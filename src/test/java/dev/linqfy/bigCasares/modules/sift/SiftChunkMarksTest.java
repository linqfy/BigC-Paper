package dev.linqfy.bigCasares.modules.sift;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiftChunkMarksTest {

    @Test
    void positionsPackLikeVanillaBlockPositions() {
        // BlockPos.asLong(1, 2, 3) in vanilla.
        assertEquals((1L << 38) | (3L << 12) | 2L, SiftChunkMarks.pack(1, 2, 3));
        assertNotEquals(SiftChunkMarks.pack(-1, -64, -1), SiftChunkMarks.pack(1, -64, 1));
        assertNotEquals(SiftChunkMarks.pack(0, -64, 0), SiftChunkMarks.pack(0, 319, 0));
    }

    @Test
    void trackingAddsOnceAndRemoves() {
        long a = SiftChunkMarks.pack(10, 70, -5);
        long b = SiftChunkMarks.pack(11, 70, -5);
        long[] planted = SiftChunkMarks.with(SiftChunkMarks.with(new long[0], a), b);
        assertArrayEquals(planted, SiftChunkMarks.with(planted, a), "a position is kept once");
        assertTrue(SiftChunkMarks.contains(planted, a));
        long[] grown = SiftChunkMarks.without(planted, a);
        assertFalse(SiftChunkMarks.contains(grown, a));
        assertTrue(SiftChunkMarks.contains(grown, b));
    }
}
