package dev.linqfy.bigCasares.modules.alexscaves.core.worldgen;

/** SplitMix64-based hashing shared by the deterministic world generation helpers. */
public final class SeededHash {
    private SeededHash() {
    }

    public static long hash(long seed, long x, long z) {
        long value = seed;
        value = mix(value ^ (x * 0x9E3779B97F4A7C15L));
        value = mix(value ^ (z * 0xC2B2AE3D27D4EB4FL));
        return value;
    }

    public static long hash(long seed, long x, long y, long z) {
        return mix(hash(seed, x, z) ^ (y * 0x165667B19E3779F9L));
    }

    public static long mix(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Uniform double in [0, 1). */
    public static double unit(long hash) {
        return (hash >>> 11) * 0x1.0p-53;
    }
}
