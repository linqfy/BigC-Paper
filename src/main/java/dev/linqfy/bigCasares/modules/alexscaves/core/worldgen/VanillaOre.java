package dev.linqfy.bigCasares.modules.alexscaves.core.worldgen;

import java.util.BitSet;
import java.util.SplittableRandom;

/**
 * Shape of a vanilla {@code minecraft:ore} vein (the feature the mod uses for its ores and block patches): a
 * string of spheres along a random line, {@code size} spheres long, fattest in the middle. Pure logic: the
 * blocks it covers go to {@code visit}, each once.
 */
public final class VanillaOre {
    private VanillaOre() {
    }

    @FunctionalInterface
    public interface Visitor {
        void visit(int x, int y, int z);
    }

    /** Visits blocks of a vein; true when it placed a block. */
    @FunctionalInterface
    public interface Placer {
        boolean place(int x, int y, int z);
    }

    public static void place(SplittableRandom random, int x, int y, int z, int size, Visitor visit) {
        place(random, x, y, z, size, (bx, by, bz) -> {
            visit.visit(bx, by, bz);
            return false;
        }, false);
    }

    /**
     * As {@link #place(SplittableRandom, int, int, int, int, Visitor)}, but after a placed block the rest of that
     * z row of the sphere is skipped (mod {@code PeeringCoprolithFeature}, which breaks out there).
     */
    public static void placeBreaking(SplittableRandom random, int x, int y, int z, int size, Placer placer) {
        place(random, x, y, z, size, placer, true);
    }

    private static void place(SplittableRandom random, int x, int y, int z, int size, Placer visit, boolean breakRow) {
        float angle = random.nextFloat() * (float) Math.PI;
        float spread = size / 8.0F;
        int pad = (int) Math.ceil((size / 16.0F * 2.0F + 1.0F) / 2.0F);
        double x1 = x + Math.sin(angle) * spread;
        double x2 = x - Math.sin(angle) * spread;
        double z1 = z + Math.cos(angle) * spread;
        double z2 = z - Math.cos(angle) * spread;
        double y1 = y + random.nextInt(3) - 2;
        double y2 = y + random.nextInt(3) - 2;
        int minX = x - (int) Math.ceil(spread) - pad;
        int minY = y - 2 - pad;
        int minZ = z - (int) Math.ceil(spread) - pad;
        int width = 2 * ((int) Math.ceil(spread) + pad);
        int height = 2 * (2 + pad);

        double[] spheres = new double[size * 4];
        for (int i = 0; i < size; i++) {
            float f = (float) i / size;
            double cx = x1 + (x2 - x1) * f;
            double cy = y1 + (y2 - y1) * f;
            double cz = z1 + (z2 - z1) * f;
            double scale = random.nextDouble() * size / 16.0;
            double radius = ((Math.sin(Math.PI * f) + 1.0) * scale + 1.0) / 2.0;
            spheres[i * 4] = cx;
            spheres[i * 4 + 1] = cy;
            spheres[i * 4 + 2] = cz;
            spheres[i * 4 + 3] = radius;
        }
        // Drop spheres that sit entirely inside another one.
        for (int i = 0; i < size - 1; i++) {
            if (spheres[i * 4 + 3] <= 0.0) {
                continue;
            }
            for (int j = i + 1; j < size; j++) {
                if (spheres[j * 4 + 3] <= 0.0) {
                    continue;
                }
                double dx = spheres[i * 4] - spheres[j * 4];
                double dy = spheres[i * 4 + 1] - spheres[j * 4 + 1];
                double dz = spheres[i * 4 + 2] - spheres[j * 4 + 2];
                double dr = spheres[i * 4 + 3] - spheres[j * 4 + 3];
                if (dr * dr > dx * dx + dy * dy + dz * dz) {
                    if (dr > 0.0) {
                        spheres[j * 4 + 3] = -1.0;
                    } else {
                        spheres[i * 4 + 3] = -1.0;
                    }
                }
            }
        }
        BitSet done = new BitSet(Math.max(1, width * height * width));
        for (int i = 0; i < size; i++) {
            double radius = spheres[i * 4 + 3];
            if (radius < 0.0) {
                continue;
            }
            double cx = spheres[i * 4];
            double cy = spheres[i * 4 + 1];
            double cz = spheres[i * 4 + 2];
            int fromX = Math.max((int) Math.floor(cx - radius), minX);
            int fromY = Math.max((int) Math.floor(cy - radius), minY);
            int fromZ = Math.max((int) Math.floor(cz - radius), minZ);
            int toX = Math.max((int) Math.floor(cx + radius), fromX);
            int toY = Math.max((int) Math.floor(cy + radius), fromY);
            int toZ = Math.max((int) Math.floor(cz + radius), fromZ);
            for (int bx = fromX; bx <= toX; bx++) {
                double ex = (bx + 0.5 - cx) / radius;
                if (ex * ex >= 1.0) {
                    continue;
                }
                for (int by = fromY; by <= toY; by++) {
                    double ey = (by + 0.5 - cy) / radius;
                    if (ex * ex + ey * ey >= 1.0) {
                        continue;
                    }
                    for (int bz = fromZ; bz <= toZ; bz++) {
                        double ez = (bz + 0.5 - cz) / radius;
                        if (ex * ex + ey * ey + ez * ez >= 1.0) {
                            continue;
                        }
                        int index = bx - minX + (by - minY) * width + (bz - minZ) * width * height;
                        if (index >= 0 && !done.get(index)) {
                            done.set(index);
                            if (visit.place(bx, by, bz) && breakRow) {
                                break;
                            }
                        }
                    }
                }
            }
        }
    }
}
