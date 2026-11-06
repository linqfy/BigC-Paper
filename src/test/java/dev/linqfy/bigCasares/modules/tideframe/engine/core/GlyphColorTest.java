package dev.linqfy.bigCasares.modules.tideframe.engine.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GlyphColorTest {
    @Test void anyColourCanBeMarkedAndMovesByAtMostSevenPerChannel() {
        // Every channel value, in all three channels at once (the channels do not interact).
        for (int c = 0; c < 256; c++) {
            int marked = GlyphColor.mark(c << 16 | c << 8 | c);
            assertTrue(GlyphColor.marked(marked), "value " + c);
            for (int shift : new int[]{16, 8, 0}) assertTrue(Math.abs(((marked >> shift) & 255) - c) <= 7, "value " + c + " shift " + shift);
            assertEquals(marked, GlyphColor.mark(marked), "marking twice changes nothing");
        }
        assertEquals(0xfefffe, GlyphColor.mark(0xffffff), "white stays all but invisible");
        assertEquals(0x060706, GlyphColor.mark(0x000000));
    }

    @Test void noColourTheGameUsesForItsOwnTextIsMarked() {
        // The 16 chat colours (the grey #AAAAAA is why two bits were not enough), plus the usual UI greys and the shadow of white text.
        int[] vanilla = {0x000000, 0x0000aa, 0x00aa00, 0x00aaaa, 0xaa0000, 0xaa00aa, 0xffaa00, 0xaaaaaa, 0x555555, 0x5555ff, 0x55ff55, 0x55ffff,
                0xff5555, 0xff55ff, 0xffff55, 0xffffff, 0x404040, 0x3f3f3f, 0x373737, 0x8b8b8b, 0xc6c6c6, 0xe0e0e0, 0xfcfc00, 0x808080};
        for (int color : vanilla) assertFalse(GlyphColor.marked(color), Integer.toHexString(color));
    }

    @Test void markedRecognisesExactlyThePattern() {
        assertTrue(GlyphColor.marked(0xfeeffe)); // 0xFE ends in 110, 0xEF in 111, 0xFE in 110
        assertFalse(GlyphColor.marked(0xfffffe));
        assertFalse(GlyphColor.marked(0xfefffd));
        assertFalse(GlyphColor.marked(0xfdfffe));
        assertEquals(6, GlyphColor.RED); assertEquals(7, GlyphColor.GREEN); assertEquals(6, GlyphColor.BLUE);
    }
}
