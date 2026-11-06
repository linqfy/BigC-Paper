package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

/**
 * Own Gaussian blur for baked art: three box passes per axis approximate a Gaussian of the given standard
 * deviation (like CSS {@code blur()}), in time independent of the radius. Works in premultiplied alpha so
 * transparent pixels never bleed their RGB into the result. Edges clamp, so a blurred region keeps its border colour.
 */
final class Blur {
    private Blur() { }

    /** Box widths whose three successive passes match a Gaussian of {@code sigma} (W. Jarosz, "Fast image convolutions"). */
    static int[] boxes(double sigma) {
        double ideal = Math.sqrt(12 * sigma * sigma / 3 + 1);
        int lower = (int) Math.floor(ideal); if (lower % 2 == 0) lower--;
        int upper = lower + 2;
        double m = (12 * sigma * sigma - 3 * lower * lower - 4 * 3 * lower - 3 * 3) / (-4.0 * lower - 4);
        long count = Math.round(m);
        int[] sizes = new int[3];
        for (int i = 0; i < 3; i++) sizes[i] = i < count ? lower : upper;
        return sizes;
    }

    /** Blurs {@code width x height} ARGB pixels of {@code argb} in place with standard deviation {@code sigma} pixels. */
    static void gaussian(int[] argb, int width, int height, double sigma) {
        if (!(sigma > 0) || width < 1 || height < 1) return;
        if (argb.length < width * height) throw new IllegalArgumentException("Pixel buffer too small");
        int n = width * height;
        int[] a = new int[n], r = new int[n], g = new int[n], b = new int[n];
        for (int i = 0; i < n; i++) {
            // Fixed point (x256) keeps the faint tails of a wide blur instead of rounding them away.
            int c = argb[i], alpha = c >>> 24;
            a[i] = alpha << 8;
            r[i] = ((c >>> 16) & 255) * alpha << 8; g[i] = ((c >>> 8) & 255) * alpha << 8; b[i] = (c & 255) * alpha << 8;
        }
        int[] scratch = new int[n];
        for (int size : boxes(sigma)) {
            int radius = (size - 1) / 2;
            if (radius == 0) continue;
            for (int[] channel : new int[][]{a, r, g, b}) {
                horizontal(channel, scratch, width, height, radius);
                vertical(scratch, channel, width, height, radius);
            }
        }
        for (int i = 0; i < n; i++) {
            int alpha = Math.min(255, (a[i] + 128) >> 8);
            if (alpha == 0) { argb[i] = 0; continue; }
            argb[i] = alpha << 24 | channel(r[i], a[i]) << 16 | channel(g[i], a[i]) << 8 | channel(b[i], a[i]);
        }
    }
    /** Straight colour from fixed-point values: (colour x alpha x 256) / (alpha x 256), rounded. */
    private static int channel(int premultiplied, int fixedAlpha) { return Math.min(255, (premultiplied + fixedAlpha / 2) / fixedAlpha); }

    /** Running-sum box filter along rows; samples past either end repeat the edge pixel. */
    private static void horizontal(int[] in, int[] out, int width, int height, int radius) {
        int span = 2 * radius + 1;
        for (int y = 0; y < height; y++) {
            int row = y * width;
            long sum = (long) (radius + 1) * in[row];
            for (int x = 1; x <= radius; x++) sum += in[row + Math.min(x, width - 1)];
            for (int x = 0; x < width; x++) {
                out[row + x] = (int) ((sum + span / 2) / span);
                sum += in[row + Math.min(x + radius + 1, width - 1)] - in[row + Math.max(x - radius, 0)];
            }
        }
    }
    private static void vertical(int[] in, int[] out, int width, int height, int radius) {
        int span = 2 * radius + 1;
        for (int x = 0; x < width; x++) {
            long sum = (long) (radius + 1) * in[x];
            for (int y = 1; y <= radius; y++) sum += in[Math.min(y, height - 1) * width + x];
            for (int y = 0; y < height; y++) {
                out[y * width + x] = (int) ((sum + span / 2) / span);
                sum += in[Math.min(y + radius + 1, height - 1) * width + x] - in[Math.max(y - radius, 0) * width + x];
            }
        }
    }
}
