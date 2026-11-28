package dev.linqfy.bigCasares.modules.tideframe;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.text.Normalizer;

/**
 * BigCasares' own pixel font (pack texture {@code bigcasares:font/fuente}: 16 x 6 cells of 8 x 12 pixels), drawn into a
 * {@link PixelImage} like the game draws its text: capitals in rows 2 to 8 of a cell, each glyph advancing its width
 * plus one pixel, and a shadow a quarter of the colour one pixel down and right.
 */
public final class PixelFont {
    static final String CHARACTERS = "ABCDEFGHIJKLMNOP" + "QRSTUVWXYZÑÁÉÍÓÚ" + "abcdefghijklmnop" + "qrstuvwxyzñáéíóú"
        + "0123456789$.,:;!" + "?¡¿-+/()%'\"&·…Üü";
    private static final int SPACE = 4;
    private static final int CAP_TOP = 2;
    private static final PixelFont DEFAULT = load();

    private final boolean[][] glyphs;
    private final int[] widths;

    private PixelFont(BufferedImage sheet) {
        glyphs = new boolean[CHARACTERS.length()][];
        widths = new int[CHARACTERS.length()];
        for (int index = 0; index < CHARACTERS.length(); index++) {
            boolean[] cell = new boolean[8 * 12];
            int cellX = (index % 16) * 8, cellY = (index / 16) * 12, width = 0;
            for (int y = 0; y < 12; y++) {
                for (int x = 0; x < 8; x++) {
                    if ((sheet.getRGB(cellX + x, cellY + y) >>> 24) != 0) {
                        cell[y * 8 + x] = true;
                        width = Math.max(width, x + 1);
                    }
                }
            }
            glyphs[index] = cell;
            widths[index] = width;
        }
    }

    public static PixelFont get() {
        return DEFAULT;
    }

    private static PixelFont load() {
        try (InputStream in = PixelFont.class.getResourceAsStream("/assets/bigcasares/textures/font/fuente.png")) {
            if (in == null) {
                throw new IllegalStateException("BigCasares font sheet is missing from the plugin");
            }
            return new PixelFont(ImageIO.read(in));
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    /** The text as the font can draw it: colour codes removed, accents it lacks dropped, other characters as ?. */
    public static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length());
        int[] codePoints = text.codePoints().toArray();
        for (int i = 0; i < codePoints.length; i++) {
            int codePoint = codePoints[i];
            if (codePoint == '§' || codePoint == '&' && i + 1 < codePoints.length
                && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(codePoints[i + 1]) >= 0) {
                i++;
                continue;
            }
            if (codePoint == ' ' || codePoint < 0x10000 && CHARACTERS.indexOf(codePoint) >= 0) {
                out.appendCodePoint(codePoint);
                continue;
            }
            String base = Normalizer.normalize(Character.toString(codePoint), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
            out.append(base.length() == 1 && CHARACTERS.indexOf(base.charAt(0)) >= 0 ? base : Character.isWhitespace(codePoint) ? " " : "?");
        }
        return out.toString();
    }

    /** Pixel width of sanitized {@code text}, from its first lit column to its last. */
    public int width(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            width += character == ' ' ? SPACE : widths[CHARACTERS.indexOf(character)] + 1;
        }
        return Math.max(0, width - 1);
    }

    /** {@code text} cut to {@code maxWidth} pixels, ending in … when it had to be cut. */
    public String fit(String text, int maxWidth) {
        String clean = sanitize(text);
        if (width(clean) <= maxWidth) {
            return clean;
        }
        String cut = clean;
        while (!cut.isEmpty() && width(cut.stripTrailing() + "…") > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut.isEmpty() ? "" : cut.stripTrailing() + "…";
    }

    /** Draws {@code text} with the top of its capitals at {@code y}; returns its width. */
    public int draw(PixelImage image, String text, int x, int y, int colour, boolean shadow) {
        String clean = sanitize(text);
        int dark = 0xFF000000 | (colour >> 16 & 255) >> 2 << 16 | (colour >> 8 & 255) >> 2 << 8 | (colour & 255) >> 2;
        int[][] passes = shadow ? new int[][] {{1, dark}, {0, colour}} : new int[][] {{0, colour}};
        for (int[] pass : passes) {
            int pen = x;
            for (int i = 0; i < clean.length(); i++) {
                char character = clean.charAt(i);
                if (character == ' ') {
                    pen += SPACE;
                    continue;
                }
                int index = CHARACTERS.indexOf(character);
                boolean[] cell = glyphs[index];
                for (int row = 0; row < 12; row++) {
                    for (int column = 0; column < 8; column++) {
                        if (cell[row * 8 + column]) {
                            image.set(pen + column + pass[0], y + row - CAP_TOP + pass[0], pass[1]);
                        }
                    }
                }
                pen += widths[index] + 1;
            }
        }
        return width(clean);
    }

    public int draw(PixelImage image, String text, int x, int y, int colour) {
        return draw(image, text, x, y, colour, true);
    }

    /** Text whose last pixel ends just before {@code right}. */
    public void right(PixelImage image, String text, int right, int y, int colour) {
        draw(image, text, right - width(sanitize(text)), y, colour, true);
    }

    public void centre(PixelImage image, String text, double centre, int y, int colour) {
        draw(image, text, (int) Math.floor(centre - width(sanitize(text)) / 2.0), y, colour, true);
    }
}
