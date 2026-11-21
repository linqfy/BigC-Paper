package dev.linqfy.bigCasares.modules.alexscaves.core.worldgen;

import org.bukkit.Material;

import java.util.SplittableRandom;
import java.util.function.Predicate;

/**
 * The vanilla placement modifiers the mod's placed features use ({@code count}, {@code height_range},
 * {@code environment_scan}, ...), so features can be placed with the mod's own numbers. Pure logic.
 */
public final class ModPlacement {
    /** Vanilla {@code VerticalAnchor.top()} as used by the mod ({@code absolute: 256}). */
    public static final int TOP = 256;

    private ModPlacement() {
    }

    /** {@code uniform} int provider, both ends inclusive. */
    public static int uniform(SplittableRandom random, int min, int max) {
        return max <= min ? min : min + random.nextInt(max - min + 1);
    }

    /** {@code trapezoid} height without plateau: the sum of two uniform values, peaking in the middle. */
    public static int trapezoid(SplittableRandom random, int min, int max) {
        int range = max - min;
        if (range <= 0) {
            return min;
        }
        int lower = range / 2;
        int upper = range - lower;
        return min + random.nextInt(upper + 1) + random.nextInt(lower + 1);
    }

    /** Vanilla {@code WorldgenRandom.setLargeFeatureSeed}: the structure random of a start chunk. */
    public static java.util.Random largeFeatureRandom(long worldSeed, int chunkX, int chunkZ) {
        java.util.Random random = new java.util.Random(worldSeed);
        long a = random.nextLong();
        long b = random.nextLong();
        random.setSeed((long) chunkX * a ^ (long) chunkZ * b ^ worldSeed);
        return random;
    }

    /** {@code rarity_filter}: passes with chance 1/{@code chance}. */
    public static boolean rarity(SplittableRandom random, int chance) {
        return chance <= 1 || random.nextInt(chance) == 0;
    }

    /**
     * {@code environment_scan} downwards: the first y (from {@code y}, at most {@code maxSteps} steps down) whose
     * block matches {@code target}, or {@link Integer#MIN_VALUE}.
     */
    public static int scanDown(CaveChunk chunk, int x, int y, int z, int maxSteps, Predicate<Material> target) {
        CaveChunk.BlockSink blocks = chunk.blocks();
        for (int step = 0; step <= maxSteps; step++, y--) {
            if (y < chunk.minY()) {
                break;
            }
            if (blocks.isInRegion(x, y, z) && target.test(blocks.getType(x, y, z))) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    /** The mod's usual scan target: anything that is not air (nor ambersol light). */
    public static boolean notAir(Material material) {
        return !material.isAir() && material != Material.LIGHT;
    }

    /**
     * Vanilla {@code noise_based_count}: {@code ceil((noise(x / factor, z / factor) + offset) * ratio)} tries, more
     * where a world-wide 2D noise is high, none where it is low.
     */
    public static int noiseCount(CaveChunk chunk, double factor, double offset, double ratio) {
        double noise = SimplexNoise3D.noise(chunk.minBlockX() / factor, 0.0, chunk.minBlockZ() / factor);
        return Math.max(0, (int) Math.ceil((noise + offset) * ratio));
    }

    /** {@code surface_relative_threshold_filter} on {@code OCEAN_FLOOR_WG} with only a maximum. */
    public static boolean belowSurface(CaveChunk chunk, int x, int y, int z, int maxRelative) {
        return y <= chunk.surfaceY(x, z) + maxRelative;
    }
}
