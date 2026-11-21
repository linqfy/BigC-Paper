package dev.linqfy.bigCasares.modules.alexscaves.core.worldgen;

/**
 * Classic 3D simplex noise (Gustavson, public domain) with the permutation table the mod uses, so
 * {@link #sample(float, float, float, float)} gives the same values as the mod's
 * {@code ACMath.sampleNoise3D}. Pure logic.
 */
public final class SimplexNoise3D {
    private static final int[][] GRAD3 = {
        {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0}, {1, 0, 1}, {-1, 0, 1},
        {1, 0, -1}, {-1, 0, -1}, {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1}
    };
    private static final short[] P = {
        151, 160, 137, 91, 90, 15, 131, 13, 201, 95, 96, 53, 194, 233, 7, 225,
        140, 36, 103, 30, 69, 142, 8, 99, 37, 240, 21, 10, 23, 190, 6, 148,
        247, 120, 234, 75, 0, 26, 197, 62, 94, 252, 219, 203, 117, 35, 11, 32,
        57, 177, 33, 88, 237, 149, 56, 87, 174, 20, 125, 136, 171, 168, 68, 175,
        74, 165, 71, 134, 139, 48, 27, 166, 77, 146, 158, 231, 83, 111, 229, 122,
        60, 211, 133, 230, 220, 105, 92, 41, 55, 46, 245, 40, 244, 102, 143, 54,
        65, 25, 63, 161, 1, 216, 80, 73, 209, 76, 132, 187, 208, 89, 18, 169,
        200, 196, 135, 130, 116, 188, 159, 86, 164, 100, 109, 198, 173, 186, 3, 64,
        52, 217, 226, 250, 124, 123, 5, 202, 38, 147, 118, 126, 255, 82, 85, 212,
        207, 206, 59, 227, 47, 16, 58, 17, 182, 189, 28, 42, 223, 183, 170, 213,
        119, 248, 152, 2, 44, 154, 163, 70, 221, 153, 101, 155, 167, 43, 172, 9,
        129, 22, 39, 253, 19, 98, 108, 110, 79, 113, 224, 232, 178, 185, 112, 104,
        218, 246, 97, 228, 251, 34, 242, 193, 238, 210, 144, 12, 191, 179, 162, 241,
        81, 51, 145, 235, 249, 14, 239, 107, 49, 192, 214, 31, 181, 199, 106, 157,
        184, 84, 204, 176, 115, 121, 50, 45, 127, 4, 150, 254, 138, 236, 205, 93,
        222, 114, 67, 29, 24, 72, 243, 141, 128, 195, 78, 66, 215, 61, 156, 180
    };
    private static final short[] PERM = new short[512];
    private static final short[] PERM_MOD_12 = new short[512];

    static {
        for (int i = 0; i < 512; i++) {
            PERM[i] = P[i & 0xFF];
            PERM_MOD_12[i] = (short) (PERM[i] % 12);
        }
    }

    private SimplexNoise3D() {
    }

    /** The mod's {@code ACMath.sampleNoise3D}: noise at {@code (x + rate) / rate} and so on, in -1..1. */
    public static float sample(float x, float y, float z, float rate) {
        return (float) noise((x + rate) / rate, (y + rate) / rate, (z + rate) / rate);
    }

    /** The mod's {@code ACMath.sampleNoise2D}: 2D noise at {@code (x + rate) / rate}, in -1..1. */
    public static float sample2D(int x, int z, float rate) {
        return (float) noise((x + rate) / rate, (z + rate) / rate);
    }

    private static final double F2 = 0.5 * (Math.sqrt(3.0) - 1.0);
    private static final double G2 = (3.0 - Math.sqrt(3.0)) / 6.0;

    /** The mod's {@code ACSimplexNoise.noise(x, y)}. */
    public static double noise(double xin, double yin) {
        double s = (xin + yin) * F2;
        int i = floor(xin + s);
        int j = floor(yin + s);
        double t = (i + j) * G2;
        double x0 = xin - (i - t);
        double y0 = yin - (j - t);
        int i1 = x0 > y0 ? 1 : 0;
        int j1 = x0 > y0 ? 0 : 1;
        double x1 = x0 - i1 + G2;
        double y1 = y0 - j1 + G2;
        double x2 = x0 - 1.0 + 2.0 * G2;
        double y2 = y0 - 1.0 + 2.0 * G2;
        int ii = i & 0xFF;
        int jj = j & 0xFF;
        int g0 = PERM_MOD_12[ii + PERM[jj]];
        int g1 = PERM_MOD_12[ii + i1 + PERM[jj + j1]];
        int g2 = PERM_MOD_12[ii + 1 + PERM[jj + 1]];
        return 70.0 * (corner2D(g0, x0, y0) + corner2D(g1, x1, y1) + corner2D(g2, x2, y2));
    }

    private static double corner2D(int gradient, double x, double y) {
        double t = 0.5 - x * x - y * y;
        if (t < 0.0) {
            return 0.0;
        }
        t *= t;
        return t * t * (GRAD3[gradient][0] * x + GRAD3[gradient][1] * y);
    }

    private static int floor(double x) {
        int xi = (int) x;
        return x < xi ? xi - 1 : xi;
    }

    private static double dot(int[] g, double x, double y, double z) {
        return g[0] * x + g[1] * y + g[2] * z;
    }

    private static double corner(int gradient, double x, double y, double z) {
        double t = 0.6 - x * x - y * y - z * z;
        if (t < 0.0) {
            return 0.0;
        }
        t *= t;
        return t * t * dot(GRAD3[gradient], x, y, z);
    }

    public static double noise(double xin, double yin, double zin) {
        double s = (xin + yin + zin) * (1.0 / 3.0);
        int i = floor(xin + s);
        int j = floor(yin + s);
        int k = floor(zin + s);
        double t = (i + j + k) * (1.0 / 6.0);
        double x0 = xin - (i - t);
        double y0 = yin - (j - t);
        double z0 = zin - (k - t);
        int i1;
        int j1;
        int k1;
        int i2;
        int j2;
        int k2;
        if (x0 >= y0) {
            if (y0 >= z0) {
                i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 1; k2 = 0;
            } else if (x0 >= z0) {
                i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 0; k2 = 1;
            } else {
                i1 = 0; j1 = 0; k1 = 1; i2 = 1; j2 = 0; k2 = 1;
            }
        } else if (y0 < z0) {
            i1 = 0; j1 = 0; k1 = 1; i2 = 0; j2 = 1; k2 = 1;
        } else if (x0 < z0) {
            i1 = 0; j1 = 1; k1 = 0; i2 = 0; j2 = 1; k2 = 1;
        } else {
            i1 = 0; j1 = 1; k1 = 0; i2 = 1; j2 = 1; k2 = 0;
        }
        double x1 = x0 - i1 + 1.0 / 6.0;
        double y1 = y0 - j1 + 1.0 / 6.0;
        double z1 = z0 - k1 + 1.0 / 6.0;
        double x2 = x0 - i2 + 1.0 / 3.0;
        double y2 = y0 - j2 + 1.0 / 3.0;
        double z2 = z0 - k2 + 1.0 / 3.0;
        double x3 = x0 - 1.0 + 0.5;
        double y3 = y0 - 1.0 + 0.5;
        double z3 = z0 - 1.0 + 0.5;
        int ii = i & 0xFF;
        int jj = j & 0xFF;
        int kk = k & 0xFF;
        int g0 = PERM_MOD_12[ii + PERM[jj + PERM[kk]]];
        int g1 = PERM_MOD_12[ii + i1 + PERM[jj + j1 + PERM[kk + k1]]];
        int g2 = PERM_MOD_12[ii + i2 + PERM[jj + j2 + PERM[kk + k2]]];
        int g3 = PERM_MOD_12[ii + 1 + PERM[jj + 1 + PERM[kk + 1]]];
        return 32.0 * (corner(g0, x0, y0, z0) + corner(g1, x1, y1, z1) + corner(g2, x2, y2, z2) + corner(g3, x3, y3, z3));
    }
}
