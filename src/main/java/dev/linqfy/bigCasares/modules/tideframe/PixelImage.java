package dev.linqfy.bigCasares.modules.tideframe;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;

/**
 * Pixel art for the Tideframe screens: one pixel is one pixel of Tideframe's 256 x 144 canvas, the size of the game's
 * 16 px item icons, so frames, text and icons share one grid. Colours are ARGB ints.
 */
public final class PixelImage {
    private final int width;
    private final int height;
    private final int[] argb;

    public PixelImage(int width, int height) {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("empty image " + width + "x" + height);
        }
        this.width = width;
        this.height = height;
        this.argb = new int[width * height];
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int get(int x, int y) {
        return argb[y * width + x];
    }

    /** Draws {@code colour} over the pixel (source over). */
    public PixelImage set(int x, int y, int colour) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return this;
        }
        int i = y * width + x, under = argb[i];
        double a = (colour >>> 24) / 255.0, da = (under >>> 24) / 255.0, oa = a + da * (1 - a);
        if (oa == 0) {
            return this;
        }
        int out = (int) Math.round(oa * 255) << 24;
        for (int shift = 16; shift >= 0; shift -= 8) {
            double c = (colour >> shift & 255) * a + (under >> shift & 255) * da * (1 - a);
            out |= (int) Math.round(c / oa) << shift;
        }
        argb[i] = out;
        return this;
    }

    /** Replaces the pixel, alpha included. */
    public PixelImage put(int x, int y, int colour) {
        if (x >= 0 && y >= 0 && x < width && y < height) {
            argb[y * width + x] = colour;
        }
        return this;
    }

    public PixelImage rect(int x, int y, int w, int h, int colour) {
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                set(x + i, y + j, colour);
            }
        }
        return this;
    }

    public PixelImage fill(int x, int y, int w, int h, int colour) {
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                put(x + i, y + j, colour);
            }
        }
        return this;
    }

    /** A one-pixel outline inside the box. */
    public PixelImage outline(int x, int y, int w, int h, int colour) {
        rect(x, y, w, 1, colour);
        rect(x, y + h - 1, w, 1, colour);
        rect(x, y + 1, 1, h - 2, colour);
        return rect(x + w - 1, y + 1, 1, h - 2, colour);
    }

    /** Light on the top and left edge, dark on the bottom and right: a raised (or, swapped, sunken) box. */
    public PixelImage bevel(int x, int y, int w, int h, int light, int dark) {
        rect(x, y, w - 1, 1, light);
        rect(x, y + 1, 1, h - 2, light);
        rect(x + 1, y + h - 1, w - 1, 1, dark);
        return rect(x + w - 1, y + 1, 1, h - 2, dark);
    }

    public PixelImage blit(PixelImage source, int x, int y) {
        for (int j = 0; j < source.height; j++) {
            for (int i = 0; i < source.width; i++) {
                int colour = source.get(i, j);
                if (colour >>> 24 != 0) {
                    set(x + i, y + j, colour);
                }
            }
        }
        return this;
    }

    /** Lays the source over this image, replacing every pixel it paints instead of blending with what was there. */
    public PixelImage cover(PixelImage source, int x, int y) {
        for (int j = 0; j < source.height; j++) {
            for (int i = 0; i < source.width; i++) {
                int colour = source.get(i, j);
                if (colour >>> 24 != 0) {
                    put(x + i, y + j, colour);
                }
            }
        }
        return this;
    }

    public PixelImage crop(int x, int y, int w, int h) {
        PixelImage out = new PixelImage(w, h);
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                out.put(i, j, x + i < width && y + j < height ? get(x + i, y + j) : 0);
            }
        }
        return out;
    }

    public PixelImage copy() {
        return crop(0, 0, width, height);
    }

    /** Dark mottled stone, one texel per pixel: soft blotches plus grain, snapped to a few greys. */
    public PixelImage stone(int x, int y, int w, int h, int seed, double shade) {
        int[] tones = {44, 50, 55, 60, 66};
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int px = x + i, py = y + j, gx = Math.floorDiv(px, 6), gy = Math.floorDiv(py, 6);
                double tx = Math.floorMod(px, 6) / 6.0, ty = Math.floorMod(py, 6) / 6.0;
                double sx = smooth(tx), sy = smooth(ty);
                double coarse = (hash(gx, gy, seed) * (1 - sx) + hash(gx + 1, gy, seed) * sx) * (1 - sy)
                    + (hash(gx, gy + 1, seed) * (1 - sx) + hash(gx + 1, gy + 1, seed) * sx) * sy;
                int tone = Math.max(0, Math.min(4, (int) Math.floor((coarse * 0.72 + hash(px, py, seed + 9) * 0.28) * 5)));
                double v = tones[tone] * shade;
                put(px, py, rgb((int) Math.round(v), (int) Math.round(v), (int) Math.round(v * 0.97)));
            }
        }
        return this;
    }

    /** A wooden frame, 4 px: dark line, light plank, dark plank, dark line; short darker strokes for grain. */
    public PixelImage wood(int x, int y, int w, int h, int seed) {
        int edge = rgb(0x150d06), light = rgb(0x96622e), dark = rgb(0x6c4521), grainLight = rgb(0x7f5226), grainDark = rgb(0x573619);
        outline(x, y, w, h, edge).outline(x + 1, y + 1, w - 2, h - 2, light).outline(x + 2, y + 2, w - 4, h - 4, dark)
            .outline(x + 3, y + 3, w - 6, h - 6, edge);
        for (int i = 2; i < w - 2; i++) {
            if (hash(i >> 2, 1, seed) < 0.3) {
                put(x + i, y + 1, grainLight);
                put(x + i, y + h - 2, grainLight);
            }
            if (hash(i >> 2, 2, seed) < 0.3) {
                put(x + i, y + 2, grainDark);
                put(x + i, y + h - 3, grainDark);
            }
        }
        for (int j = 2; j < h - 2; j++) {
            if (hash(3, j >> 2, seed) < 0.3) {
                put(x + 1, y + j, grainLight);
                put(x + w - 2, y + j, grainLight);
            }
            if (hash(4, j >> 2, seed) < 0.3) {
                put(x + 2, y + j, grainDark);
                put(x + w - 3, y + j, grainDark);
            }
        }
        return this;
    }

    /** An item slot like the inventory's, in the dark theme: sunken, 18 x 18 around a 16 px item. */
    public PixelImage slot(int x, int y) {
        fill(x, y, 18, 18, rgb(0x1d1d1b));
        return bevel(x, y, 18, 18, rgb(0x0c0c0b), rgb(0x56544f));
    }

    /** A button like the game's: black outline, raised face. */
    public PixelImage button(int x, int y, int w, int h, int face, int light, int dark) {
        fill(x, y, w, h, rgb(face)).outline(x, y, w, h, rgb(0x0c0c0b));
        rect(x + 1, y + 1, w - 2, 1, rgb(light));
        rect(x + 1, y + 2, 1, h - 4, rgb(light));
        rect(x + 1, y + h - 2, w - 2, 1, rgb(dark));
        return rect(x + w - 2, y + 1, 1, h - 3, rgb(dark));
    }

    public PixelImage button(int x, int y, int w, int h) {
        return button(x, y, w, h, 0x5b5b58, 0x8d8d89, 0x2f2f2d);
    }

    /** The image as a PNG with every pixel {@code scale} x {@code scale}, in base64. */
    public String pngBase64(int scale) {
        BufferedImage image = new BufferedImage(width * scale, height * scale, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height * scale; y++) {
            for (int x = 0; x < width * scale; x++) {
                image.setRGB(x, y, get(x / scale, y / scale));
            }
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException impossible) {
            throw new UncheckedIOException(impossible);
        }
    }

    /** An opaque colour from 0xRRGGBB. */
    public static int rgb(int rgb) {
        return 0xFF000000 | rgb;
    }

    public static int rgb(int r, int g, int b) {
        return 0xFF000000 | Math.max(0, Math.min(255, r)) << 16 | Math.max(0, Math.min(255, g)) << 8 | Math.max(0, Math.min(255, b));
    }

    /** {@code rgb} (0xRRGGBB) with alpha {@code alpha} (0 to 255). */
    public static int rgba(int rgb, int alpha) {
        return alpha << 24 | rgb & 0xFFFFFF;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    /** A deterministic value in [0, 1) for a grid cell, the same the mockup's art used. */
    static double hash(int x, int y, int seed) {
        int h = (int) ((long) x * 374761393L + (long) y * 668265263L + (long) seed * 2147483647L);
        h = (h ^ (h >>> 13)) * 1274126177;
        return Integer.toUnsignedLong(h ^ (h >>> 16)) / 4294967296.0;
    }
}
