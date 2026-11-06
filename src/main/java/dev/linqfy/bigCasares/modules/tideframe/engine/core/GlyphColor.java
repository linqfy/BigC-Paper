package dev.linqfy.bigCasares.modules.tideframe.engine.core;

/**
 * The colour marker of auto-fit: with {@code display.auto-fit} the colour of every Tideframe menu glyph carries a
 * pattern in its three lowest bits per channel (red 6, green 7, blue 6), which the pack's text shader recognises
 * to scale those glyphs to the window. White becomes #FEFFFE and any colour moves by at most 7/255 in a channel:
 * invisible, and no vanilla colour (#AAAAAA, #FFFFFF, #555555...) has the pattern. Pure arithmetic: any thread.
 */
public final class GlyphColor {
    private GlyphColor() { }
    /** The pattern in the three lowest bits of red, green and blue; the shader tests the same values. */
    public static final int RED = 6, GREEN = 7, BLUE = 6;

    /** {@code rgb} (0xRRGGBB) with the marker set. */
    public static int mark(int rgb) {
        return ((((rgb >> 16) & 0xF8) | RED) << 16) | ((((rgb >> 8) & 0xF8) | GREEN) << 8) | ((rgb & 0xF8) | BLUE);
    }
    /** True when {@code rgb} carries the marker. */
    public static boolean marked(int rgb) {
        return ((rgb >> 16) & 7) == RED && ((rgb >> 8) & 7) == GREEN && (rgb & 7) == BLUE;
    }
}
