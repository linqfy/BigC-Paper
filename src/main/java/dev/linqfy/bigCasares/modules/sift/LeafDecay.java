package dev.linqfy.bigCasares.modules.sift;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Vanilla's leaf decay rule, which the mod's foliage inherits from {@code LeavesBlock}: a leaf has a distance from
 * the nearest log, one for a leaf touching a log, one more for each leaf between (the six face neighbours), and a
 * leaf that is not persistent and whose distance reaches {@value #DECAY_DISTANCE} decays on a random tick.
 *
 * <p>Vanilla keeps the distance in the block state and updates it with neighbour updates. The custom foliage has no
 * state to keep it in, so this class finds the same number when the tick happens: a breadth-first search through
 * leaves, at most {@value #MAX_REACH} steps, that reads the world through {@link Grid}. A path may pass through any
 * leaves (also vanilla ones), as vanilla's distance does. It knows nothing about Bukkit.</p>
 */
final class LeafDecay {

    /** {@code LeavesBlock.DECAY_DISTANCE}: a leaf at this distance decays. */
    static final int DECAY_DISTANCE = 7;
    /** The farthest log that keeps a leaf alive, in steps. */
    static final int MAX_REACH = DECAY_DISTANCE - 1;

    enum Kind {
        /** A log or wood block (the {@code logs} tag): distance zero. */
        LOG,
        /** A leaf: carries the distance on. */
        LEAVES,
        /** Anything else blocks the path. */
        OTHER
    }

    /** The world as the search sees it. */
    @FunctionalInterface
    interface Grid {
        Kind kindAt(int x, int y, int z);
    }

    private static final int[][] FACES = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};

    private LeafDecay() {
    }

    /**
     * The distance of the leaf at the position as vanilla would hold it, from 1 to {@value #DECAY_DISTANCE}
     * (seven also means "no log within six steps"). The block at the position is taken to be a leaf.
     */
    static int distance(Grid grid, int x, int y, int z) {
        Set<SiftPos> seen = new HashSet<>();
        SiftPos start = new SiftPos(x, y, z);
        seen.add(start);
        List<SiftPos> frontier = List.of(start);
        for (int steps = 1; steps <= MAX_REACH && !frontier.isEmpty(); steps++) {
            List<SiftPos> next = new ArrayList<>();
            for (SiftPos leaf : frontier) {
                for (int[] face : FACES) {
                    SiftPos neighbour = leaf.offset(face[0], face[1], face[2]);
                    if (!seen.add(neighbour)) {
                        continue;
                    }
                    Kind kind = grid.kindAt(neighbour.x(), neighbour.y(), neighbour.z());
                    if (kind == Kind.LOG) {
                        return steps;
                    }
                    if (kind == Kind.LEAVES) {
                        next.add(neighbour);
                    }
                }
            }
            frontier = next;
        }
        return DECAY_DISTANCE;
    }

    /** True when the leaf at the position has lost its logs (distance seven). */
    static boolean decays(Grid grid, int x, int y, int z) {
        return distance(grid, x, y, z) >= DECAY_DISTANCE;
    }
}
