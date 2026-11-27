package dev.linqfy.bigCasares.modules.alexscaves.toxic;

import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.SimplexNoise3D;

import java.util.Set;

/**
 * Formulas of the mod's {@code NuclearExplosionEntity}, as pure logic. Size 1 is a Nucleeper (1.75 when
 * charged). The Nuclear Bomb is size 9 on this server: three times the mod's 3 ({@code nuclear_explosion_size_modifier}),
 * a crater radius of 135 blocks instead of 45.
 */
public final class NukeMath {
    public static final float NUCLEEPER_SIZE = 1.0F;
    public static final float CHARGED_NUCLEEPER_SIZE = 1.75F;
    public static final float BOMB_SIZE = 9.0F;
    /** Mod {@code nuke_max_block_explosion_resistance}. */
    public static final float MAX_BLOCK_RESISTANCE = 1000.0F;
    /** Mod tag {@code resists_radiation}: hurt 25 % by the blast. */
    static final Set<String> RESISTS_RADIATION = Set.of("alexscaves:nucleeper", "alexscaves:radgill",
        "alexscaves:brainiac", "alexscaves:gammaroach", "alexscaves:raycat", "alexscaves:tremorzilla");

    private NukeMath() {
    }

    /** Chunk-sized cubes affected in each direction. */
    public static int chunksAffected(float size) {
        return (int) Math.ceil(size);
    }

    /** Crater radius in blocks. */
    public static int radius(float size) {
        return chunksAffected(size) * 15;
    }

    /** Blast damage at a distance; zero beyond the kill box. */
    public static float damage(float size, float distance) {
        int radius = radius(size);
        float max = radius + radius * 0.5F + 1.0F;
        float revert = (max - distance) / max;
        float base = size <= 1.5F ? 100.0F : 100.0F + (size - 1.5F) * 400.0F;
        return Math.max(0.0F, revert * base);
    }

    /** Irradiated amplifier given to everything in the blast. */
    public static int irradiatedAmplifier(float size) {
        return size <= 1.5F ? 1 : 2;
    }

    /** Mod {@code removeChunk}: whether a block is inside the crater. */
    public static boolean inCrater(int x, int y, int z, int centerX, int centerY, int centerZ, int radius) {
        float width = (SimplexNoise3D.sample(x, y, z, radius) - 0.5F) * 0.45F + 0.55F;
        double yDist = smin(0.6F - (float) Math.abs(centerY - y) / radius, 0.6F, 0.2F);
        // Mod distToCenterSqr to (centerX, y - 1, centerZ).
        double dx = x + 0.5 - centerX;
        double dz = z + 0.5 - centerZ;
        double distance = dx * dx + 1.5 * 1.5 + dz * dz;
        double target = yDist * (radius + width * radius) * radius;
        return distance <= target;
    }

    /**
     * Whether the 16-block cube whose lowest corner is {@code (dx, dy, dz)} from the centre can hold a crater block.
     * {@link #inCrater} needs a vertical distance below 0.6 radius and a horizontal one below about 1.1 radius
     * (noise width 1 at most); cubes beyond both, with a margin, are skipped instead of sampled block by block.
     */
    public static boolean cubeMayHoldCrater(int dx, int dy, int dz, int radius) {
        double nearX = Math.max(0, Math.max(dx - 1, -(dx + 16)));
        double nearZ = Math.max(0, Math.max(dz - 1, -(dz + 16)));
        double nearY = Math.max(0, Math.max(dy - 1, -(dy + 16)));
        double reach = radius * 1.15 + 2;
        return nearY <= radius * 0.6 + 2 && nearX * nearX + nearZ * nearZ <= reach * reach;
    }

    /**
     * The chunks a crater of this radius can touch, nearest to the centre first: every chunk whose closest block is
     * within the reach {@link #cubeMayHoldCrater} uses. Each entry is {@code {chunkX, chunkZ}}.
     */
    public static java.util.List<int[]> craterChunks(int centerX, int centerZ, int radius) {
        double reach = radius * 1.15 + 2;
        int minX = Math.floorDiv((int) Math.floor(centerX - reach), 16);
        int maxX = Math.floorDiv((int) Math.ceil(centerX + reach), 16);
        int minZ = Math.floorDiv((int) Math.floor(centerZ - reach), 16);
        int maxZ = Math.floorDiv((int) Math.ceil(centerZ + reach), 16);
        java.util.List<int[]> chunks = new java.util.ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double dx = Math.max(0, Math.max(x * 16 - centerX, centerX - (x * 16 + 15)));
                double dz = Math.max(0, Math.max(z * 16 - centerZ, centerZ - (z * 16 + 15)));
                if (dx * dx + dz * dz <= reach * reach) {
                    chunks.add(new int[] {x, z});
                }
            }
        }
        chunks.sort(java.util.Comparator.comparingDouble(chunk -> {
            double dx = chunk[0] * 16 + 8 - centerX;
            double dz = chunk[1] * 16 + 8 - centerZ;
            return dx * dx + dz * dz;
        }));
        return chunks;
    }

    /** Cubes carved per tick: the whole crater in about {@code CARVE_TICKS} ticks, never fewer than 3 a tick. */
    public static int cubesPerTick(int cubes) {
        return Math.max(3, (int) Math.ceil(cubes / (double) CARVE_TICKS));
    }

    static final int CARVE_TICKS = 400;
    /** The blast hurts and irradiates for the mod's 6 seconds, however long the crater takes to carve. */
    public static final int BLAST_TICKS = 120;
    /** Fastest an entity is flung, in blocks per tick (a size-9 blast would otherwise throw it out of the world). */
    public static final double MAX_FLING = 4.0;

    /** Fire left on the crater floor burns this long, then goes out. */
    public static final int FIRE_TICKS = 100;

    /** Chance that a destroyed block drops as an item. */
    public static float dropChance(float size) {
        return 0.025F / Math.min(1.0F, size);
    }

    static float smin(float a, float b, float k) {
        float h = Math.max(k - Math.abs(a - b), 0.0F) / k;
        return Math.min(a, b) - h * h * k * 0.25F;
    }
}
