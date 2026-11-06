package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlurTest {
    @Test void threeBoxesHaveTheVarianceOfTheRequestedGaussian() {
        for (double sigma = 1; sigma <= 64; sigma *= 1.5) {
            double variance = 0;
            for (int size : Blur.boxes(sigma)) { assertEquals(1, size % 2, "odd box widths keep the blur centred"); variance += (size * size - 1) / 12.0; }
            assertEquals(sigma * sigma, variance, Math.max(1, sigma * sigma * .05), "sigma " + sigma);
        }
    }

    @Test void aPointSpreadsSymmetricallyAndKeepsItsTotalCoverage() {
        int size = 61, center = 30;
        int[] pixels = new int[size * size];
        pixels[center * size + center] = 0xffffffff;
        Blur.gaussian(pixels, size, size, 4);
        long total = 0;
        for (int p : pixels) total += p >>> 24;
        // One opaque pixel of coverage (255) spread out: rounding per pass may move it by a few units, never lose it.
        assertEquals(255, total, 40);
        int peak = pixels[center * size + center] >>> 24;
        assertTrue(peak > 0 && peak < 64, "the point is spread out, peak " + peak);
        assertEquals(pixels[center * size + center - 4], pixels[center * size + center + 4], "left and right match");
        assertEquals(pixels[(center - 4) * size + center], pixels[(center + 4) * size + center], "up and down match");
        assertTrue((pixels[center * size + center + 4] >>> 24) > (pixels[center * size + center + 12] >>> 24), "falls off with distance");
        assertEquals(0xffffff, pixels[center * size + center + 4] & 0xffffff, "a white point stays white, only less opaque");
    }

    @Test void colourOfTransparentPixelsNeverBleedsIntoTheResult() {
        int width = 40, height = 4;
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) pixels[y * width + x] = x < 20 ? 0x00ff0000 : 0xff0000ff;
        Blur.gaussian(pixels, width, height, 6);
        for (int p : pixels) if ((p >>> 24) != 0) assertEquals(0, (p >>> 16) & 255, "no red from invisible pixels: " + Integer.toHexString(p));
        assertTrue((pixels[2 * width + 18] >>> 24) > 0, "blue spreads into the transparent half");
        assertEquals(0xff0000ff, pixels[2 * width + 39], "far from the edge the colour is unchanged");
    }

    @Test void flatAreasAndZeroSigmaStayExactlyAsTheyWere() {
        int[] flat = new int[16 * 16];
        java.util.Arrays.fill(flat, 0xc0336699);
        int[] copy = flat.clone();
        Blur.gaussian(flat, 16, 16, 5);
        assertArrayEquals(copy, flat, "clamped edges keep a uniform area uniform");
        int[] noise = new int[9];
        for (int i = 0; i < noise.length; i++) noise[i] = 0xff000000 | i * 0x111111;
        int[] before = noise.clone();
        Blur.gaussian(noise, 3, 3, 0);
        assertArrayEquals(before, noise);
    }
}
