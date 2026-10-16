package dev.linqfy.bigCasares.modules.sift.feature;

import java.util.Random;

/**
 * A Java port of one of the mod's custom feature types ({@code the_sift:*}).
 * Implementations must be stateless and thread-safe: populators run
 * concurrently on world-generation threads for different chunks.
 */
@FunctionalInterface
public interface SiftFeature {

    /**
     * Places the feature at {@code (x, y, z)} produced by the placement modifiers.
     *
     * @return whether anything was placed
     */
    boolean place(WorldRegion region, Random random, int x, int y, int z);
}
