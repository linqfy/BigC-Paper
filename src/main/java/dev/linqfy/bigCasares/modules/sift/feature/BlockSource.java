package dev.linqfy.bigCasares.modules.sift.feature;

import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;

/**
 * Read-only blocks of a fixed area, thread-safe so that a plan can be computed away from
 * the server thread (production: chunk snapshots; tests: a hash map). Positions outside
 * {@link #contains} read as air.
 */
public interface BlockSource {

    int minY();

    /** Exclusive top of the build range. */
    int maxY();

    /** Whether the column is part of the area. */
    boolean contains(int x, int z);

    /** The state at a position of the area ({@code minecraft:air} outside it). */
    BlockStateSpec state(int x, int y, int z);

    /** Full solid collision (vine and plant supports), as {@link WorldRegion#isSolid}. */
    boolean solid(int x, int y, int z);

    /** Y of the highest block of the column that is not air, or {@code minY() - 1} for an empty column. */
    int topY(int x, int z);
}
