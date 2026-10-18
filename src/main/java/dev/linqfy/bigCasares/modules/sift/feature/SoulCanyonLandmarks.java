package dev.linqfy.bigCasares.modules.sift.feature;

import java.util.List;

/**
 * Receives the landmarks of every Soul Canyon the generator carves: the mod's
 * {@code SiftLandmarkTracker.record} calls in {@code SoulCanyonFeature}. Echo Golems navigate by them.
 * Called on world-generation threads, once per canyon, after its blocks are written, so implementations
 * must be thread-safe. Recording has no influence on generation.
 */
@FunctionalInterface
public interface SoulCanyonLandmarks {

    /** A block position. */
    record Position(int x, int y, int z) {
    }

    SoulCanyonLandmarks NONE = (entrance, midpoint, cluster, soulBlocks) -> {
    };

    /**
     * @param entrance   where the ramp meets the surface (the golem's feet position)
     * @param midpoint   the route point about 38% along the canyon
     * @param cluster    the average of the Soul Block positions
     * @param soulBlocks every Soul Block the canyon placed
     */
    void soulCanyon(Position entrance, Position midpoint, Position cluster, List<Position> soulBlocks);
}
