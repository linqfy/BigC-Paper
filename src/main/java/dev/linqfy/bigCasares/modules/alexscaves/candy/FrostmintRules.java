package dev.linqfy.bigCasares.modules.alexscaves.candy;

import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.FeatureShapes;
import org.bukkit.Material;

import java.util.function.IntPredicate;
import java.util.function.Predicate;

/**
 * Rules of the mod's frostmint ({@code FrostmintBlock}, {@code CeilingFrostmintFeature}) apart from the world: which
 * slab falls, which blocks fall together, how the ceiling formations are shaped. The variants are the module block's:
 * {@code default} is the bottom slab, {@code top} the top slab and {@code double} the full block.
 */
final class FrostmintRules {
    static final String BOTTOM = "default";
    static final String TOP = "top";
    static final String DOUBLE = "double";
    /** Mod {@code getDelayAfterPlace}: ticks between a change and the fall check. */
    static final long FALL_DELAY_TICKS = 2L;
    /** Mod {@code FrostmintExplosion}: power of the blast when a frostmint meets purple soda. */
    static final float EXPLOSION_POWER = 4.0F;
    /** Freeze ticks added to every living entity near the blast, and how near. */
    static final int FREEZE_TICKS = 200;
    static final double FREEZE_RADIUS = 8.0;

    private FrostmintRules() {
    }

    /**
     * Whether a frostmint falls when its check runs. A bottom or double slab falls when the block below is free; a top
     * slab hangs, unless a projectile hit the column ({@code hit}), which makes the bottom block of the column come
     * down whatever it is (the mod's tick lets a top slab fall as well).
     */
    static boolean falls(String variant, boolean belowFree, boolean hit) {
        boolean top = TOP.equals(variant);
        return hit ? belowFree || top : belowFree && !top;
    }

    /** Vanilla {@code FallingBlock.isFree}: air, fire, fluids and replaceable blocks. */
    static boolean isFree(Material type, Predicate<Material> replaceable) {
        return FeatureShapes.isAir(type.name()) || type == Material.WATER || type == Material.LAVA || type == Material.FIRE
            || type == Material.SOUL_FIRE || replaceable.test(type);
    }

    /** Height of the lowest frostmint of the column that holds the one at {@code y}; the world floor ends it. */
    static int columnBottom(int y, int minY, IntPredicate frostmintAt) {
        while (y > minY && frostmintAt.test(y - 1)) {
            y--;
        }
        return y;
    }

    /** Whether a slab of this variant placed against the clicked face goes in the upper half of its block. */
    static boolean upperHalf(boolean faceUp, boolean faceDown, double clickedHeight) {
        if (faceDown) {
            return true;
        }
        return !faceUp && clickedHeight > 0.5;
    }

    /** Whether the frostmint at the clicked block and face takes another slab and becomes a double. */
    static boolean merges(String variant, boolean faceUp, boolean faceDown) {
        return BOTTOM.equals(variant) && faceUp || TOP.equals(variant) && faceDown;
    }

    /** Mod {@code CeilingFrostmintFeature}: squared radius of a rock candy blob from its half sizes (1 or 2 each). */
    static double blobRadiusSquared(int a, int b, int c) {
        float radius = (a + b + c) * 0.333F + 0.5F;
        return radius * radius;
    }

    /** Inside half of the squared radius the blob is cyan, outside it is white. */
    static boolean cyanRockCandy(double distanceSquared, double radiusSquared) {
        return distanceSquared <= radiusSquared / 2.0;
    }

    /** A hanging column of frostmint has {@code 2 + roll} (roll 0 to 3) steps below its start, the start included. */
    static int hangingLength(int roll) {
        return 2 + roll;
    }
}
