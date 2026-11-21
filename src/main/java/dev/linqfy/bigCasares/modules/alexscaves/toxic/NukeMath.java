package dev.linqfy.bigCasares.modules.alexscaves.toxic;

import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.SimplexNoise3D;

import java.util.Set;

/**
 * Formulas of the mod's {@code NuclearExplosionEntity}, as pure logic. Size 1 is a Nucleeper (1.75 when
 * charged), size 3 the Nuclear Bomb ({@code nuclear_explosion_size_modifier}).
 */
public final class NukeMath {
    public static final float NUCLEEPER_SIZE = 1.0F;
    public static final float CHARGED_NUCLEEPER_SIZE = 1.75F;
    public static final float BOMB_SIZE = 3.0F;
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

    /** Chance that a destroyed block drops as an item. */
    public static float dropChance(float size) {
        return 0.025F / Math.min(1.0F, size);
    }

    static float smin(float a, float b, float k) {
        float h = Math.max(k - Math.abs(a - b), 0.0F) / k;
        return Math.min(a, b) - h * h * k * 0.25F;
    }
}
