package dev.linqfy.bigCasares.modules.alexscaves.core.worldgen;

/**
 * Vanilla {@code SimplexNoise} seeded by a {@code XoroshiroRandomSource}, as the mod's {@code ACBiomeRarity} builds
 * its {@code PerlinSimplexNoise} (one octave at frequency 1, so its value is this noise's 2D value). Ported so the
 * plugin bends biome borders exactly like the mod. Pure logic.
 */
public final class VanillaSimplexNoise {
    private static final int[][] GRADIENT = {
        {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0}, {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
        {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1}, {1, 1, 0}, {0, -1, 1}, {-1, 1, 0}, {0, -1, -1}
    };
    private static final double SQRT_3 = Math.sqrt(3.0);
    private static final double F2 = 0.5 * (SQRT_3 - 1.0);
    private static final double G2 = (3.0 - SQRT_3) / 6.0;

    private final int[] p = new int[512];

    /** A noise seeded like {@code new PerlinSimplexNoise(new XoroshiroRandomSource(seed), List.of(0))}. */
    public VanillaSimplexNoise(long seed) {
        Xoroshiro random = new Xoroshiro(seed);
        // xo, yo, zo: drawn by vanilla, unused for 2D values without offsets.
        random.nextDouble();
        random.nextDouble();
        random.nextDouble();
        for (int i = 0; i < 256; i++) {
            p[i] = i;
        }
        for (int i = 0; i < 256; i++) {
            int j = random.nextInt(256 - i);
            int k = p[i];
            p[i] = p[j + i];
            p[j + i] = k;
        }
    }

    private int p(int i) {
        return p[i & 255];
    }

    /** Vanilla {@code SimplexNoise.getValue(x, y)}. */
    public double getValue(double x, double y) {
        double s = (x + y) * F2;
        int i = (int) Math.floor(x + s);
        int j = (int) Math.floor(y + s);
        double t = (i + j) * G2;
        double x0 = x - (i - t);
        double y0 = y - (j - t);
        int i1;
        int j1;
        if (x0 > y0) {
            i1 = 1;
            j1 = 0;
        } else {
            i1 = 0;
            j1 = 1;
        }
        double x1 = x0 - i1 + G2;
        double y1 = y0 - j1 + G2;
        double x2 = x0 - 1.0 + 2.0 * G2;
        double y2 = y0 - 1.0 + 2.0 * G2;
        int ii = i & 255;
        int jj = j & 255;
        int g0 = p(ii + p(jj)) % 12;
        int g1 = p(ii + i1 + p(jj + j1)) % 12;
        int g2 = p(ii + 1 + p(jj + 1)) % 12;
        return 70.0 * (corner(g0, x0, y0) + corner(g1, x1, y1) + corner(g2, x2, y2));
    }

    private static double corner(int gradient, double x, double y) {
        double t = 0.5 - x * x - y * y;
        if (t < 0.0) {
            return 0.0;
        }
        t *= t;
        return t * t * (GRADIENT[gradient][0] * x + GRADIENT[gradient][1] * y);
    }

    /** Vanilla {@code XoroshiroRandomSource} (Xoroshiro128++ with the seed upgrade of {@code RandomSupport}). */
    static final class Xoroshiro {
        private long lo;
        private long hi;

        Xoroshiro(long seed) {
            long l = seed ^ 0x6A09E667F3BCC909L;
            long h = l + 0x9E3779B97F4A7C15L;
            lo = mixStafford13(l);
            hi = mixStafford13(h);
            if ((lo | hi) == 0L) {
                lo = 0x9E3779B97F4A7C15L;
                hi = 0x6A09E667F3BCC909L;
            }
        }

        static long mixStafford13(long z) {
            z = (z ^ z >>> 30) * 0xBF58476D1CE4E5B9L;
            z = (z ^ z >>> 27) * 0x94D049BB133111EBL;
            return z ^ z >>> 31;
        }

        long nextLong() {
            long l = lo;
            long h = hi;
            long result = Long.rotateLeft(l + h, 17) + l;
            h ^= l;
            lo = Long.rotateLeft(l, 49) ^ h ^ h << 21;
            hi = Long.rotateLeft(h, 28);
            return result;
        }

        int nextInt() {
            return (int) nextLong();
        }

        int nextInt(int bound) {
            long i = Integer.toUnsignedLong(nextInt());
            long j = i * bound;
            long k = j & 0xFFFFFFFFL;
            if (k < bound) {
                for (int l = Integer.remainderUnsigned(~bound + 1, bound); k < l; k = j & 0xFFFFFFFFL) {
                    i = Integer.toUnsignedLong(nextInt());
                    j = i * bound;
                }
            }
            return (int) (j >> 32);
        }

        double nextDouble() {
            return (nextLong() >>> 11) * (double) 1.110223E-16F;
        }
    }
}
