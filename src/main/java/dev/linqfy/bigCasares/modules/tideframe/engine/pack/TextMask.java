package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;

/**
 * One line of the project 5x7 font on the logical pixel grid, with optional scale, outline and drop shadow,
 * in capitals or as written (the style's text case).
 * Shared by the baked canvas and the live-text font grids so both draw identical pixels.
 * Coordinates: the text origin (first glyph's top-left) sits at ({@link #pad()}, {@link #pad()}).
 */
public final class TextMask {
    private static final byte NONE = 0, SHADOW = 1, OUTLINE = 2, TEXT = 3;
    private final int width, height, pad, glyphHeight, advance, shadow;
    private final byte[] layer;

    private TextMask(int width, int height, int pad, int glyphHeight, int advance, int shadow, byte[] layer) {
        this.width = width; this.height = height; this.pad = pad; this.glyphHeight = glyphHeight; this.advance = advance; this.shadow = shadow; this.layer = layer;
    }

    public static TextMask of(String text, Screen.Style style) {
        int scale = style.textScale(), outline = style.outlineThickness(), shadow = style.textShadow() ? scale : 0;
        int inkWidth = Math.max(0, (text.length() * 6 - 1) * scale);
        int width = Math.max(1, outline + inkWidth + outline + shadow), height = outline + 7 * scale + outline + shadow;
        var layer = new byte[width * height];
        boolean upper = style.upperCase();
        for (int index = 0; index < text.length(); index++) {
            String[] rows = Glyphs.pattern(text.charAt(index), upper).split("/");
            for (int y = 0; y < 7; y++) for (int x = 0; x < 5; x++) {
                if (rows[y].charAt(x) != '1') continue;
                for (int dy = 0; dy < scale; dy++) for (int dx = 0; dx < scale; dx++)
                    layer[(outline + y * scale + dy) * width + outline + (index * 6 + x) * scale + dx] = TEXT;
            }
        }
        if (outline > 0) {
            var grown = layer.clone();
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                if (layer[y * width + x] != TEXT) continue;
                for (int dy = -outline; dy <= outline; dy++) for (int dx = -outline; dx <= outline; dx++) {
                    if (outline > 1 && dx * dx + dy * dy > outline * outline + 1) continue; // rounder corners when thick
                    int nx = x + dx, ny = y + dy;
                    if (nx >= 0 && ny >= 0 && nx < width && ny < height && grown[ny * width + nx] == NONE) grown[ny * width + nx] = OUTLINE;
                }
            }
            layer = grown;
        }
        if (shadow > 0) {
            var shaded = layer.clone();
            for (int y = 0; y + shadow < height; y++) for (int x = 0; x + shadow < width; x++)
                if (layer[y * width + x] != NONE && shaded[(y + shadow) * width + x + shadow] == NONE) shaded[(y + shadow) * width + x + shadow] = SHADOW;
            layer = shaded;
        }
        return new TextMask(width, height, outline, 7 * scale, 6 * scale, shadow, layer);
    }

    public int width() { return width; }
    public int height() { return height; }
    public int pad() { return pad; }

    /**
     * As {@link #color(int, int, int, Screen.Style)} with words in their own colour: {@code colors} holds one ARGB per
     * character (0: the text colour). A coloured character is solid (no text gradient); without an outline its shadow is
     * its own colour at a quarter brightness.
     */
    public int color(int x, int y, int textArgb, Screen.Style style, int[] colors) {
        if (colors == null || colors.length == 0) return color(x, y, textArgb, style);
        byte cell = layer[y * width + x];
        int own = colors[Math.max(0, Math.min(colors.length - 1, (x - (cell == SHADOW ? shadow : 0) - pad) / advance))];
        if (own == 0 || cell == OUTLINE || cell == NONE || cell == SHADOW && style.textOutline() != null) return color(x, y, textArgb, style);
        return cell == TEXT ? own : 0xff000000 | ((own >>> 16 & 255) / 4) << 16 | ((own >>> 8 & 255) / 4) << 8 | (own & 255) / 4;
    }
    /** ARGB at a mask cell; 0 when empty. Shadows are the decorated colour at a quarter brightness, like vanilla. */
    public int color(int x, int y, int textArgb, Screen.Style style) {
        return switch (layer[y * width + x]) {
            case TEXT -> style.textGradient() == null ? textArgb
                    : HudRaster.mix(textArgb, 0xff000000 | Integer.parseInt(style.textGradient().substring(1), 16),
                        Math.max(0, Math.min(1, (y - pad) / (double) Math.max(1, glyphHeight - 1))));
            case OUTLINE -> 0xff000000 | Integer.parseInt(style.textOutline().substring(1), 16);
            case SHADOW -> {
                int base = style.textOutline() == null ? textArgb : Integer.parseInt(style.textOutline().substring(1), 16);
                yield 0xff000000 | ((base >>> 16 & 255) / 4) << 16 | ((base >>> 8 & 255) / 4) << 8 | (base & 255) / 4;
            }
            default -> 0;
        };
    }
}
