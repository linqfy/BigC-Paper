package dev.linqfy.bigCasares.dimension.travel;

import java.util.Optional;

/**
 * Finds a safe standing position near a target column: a solid, non-hazardous
 * floor with two passable blocks above and no hazard next to the feet. Columns
 * are scanned in square rings of increasing radius so the closest spot wins and
 * the result is deterministic for the same terrain.
 */
public final class SafeLocationFinder {

    /** A feet position (block coordinates). */
    public record Spot(int x, int y, int z) {
    }

    private final int maxRadius;

    public SafeLocationFinder(int maxRadius) {
        if (maxRadius < 0 || maxRadius > 512) {
            throw new IllegalArgumentException("Search radius must be within [0, 512]");
        }
        this.maxRadius = maxRadius;
    }

    /**
     * @param preferredY Y to search around first (e.g. the mapped source height);
     *     the column is scanned downward from {@code preferredY + 32}, then upward.
     */
    public Optional<Spot> find(BlockGrid grid, int x, int z, int preferredY) {
        for (int radius = 0; radius <= maxRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    Optional<Spot> spot = column(grid, x + dx, z + dz, preferredY);
                    if (spot.isPresent()) {
                        return spot;
                    }
                }
            }
        }
        return Optional.empty();
    }

    Optional<Spot> column(BlockGrid grid, int x, int z, int preferredY) {
        int top = Math.min(grid.maxY() - 2, preferredY + 32);
        int bottom = grid.minY() + 1;
        for (int y = top; y >= bottom; y--) {
            if (isSafe(grid, x, y, z)) {
                return Optional.of(new Spot(x, y, z));
            }
        }
        for (int y = top + 1; y <= grid.maxY() - 2; y++) {
            if (isSafe(grid, x, y, z)) {
                return Optional.of(new Spot(x, y, z));
            }
        }
        return Optional.empty();
    }

    /** Feet at {@code y}: floor below, feet and head passable, nothing hazardous around. */
    public static boolean isSafe(BlockGrid grid, int x, int y, int z) {
        if (!grid.isSolidFloor(x, y - 1, z) || grid.isHazard(x, y - 1, z)) {
            return false;
        }
        if (!grid.isPassable(x, y, z) || !grid.isPassable(x, y + 1, z)) {
            return false;
        }
        if (grid.isHazard(x, y, z) || grid.isHazard(x, y + 1, z)) {
            return false;
        }
        int[][] neighbours = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] offset : neighbours) {
            if (grid.isHazard(x + offset[0], y, z + offset[1])) {
                return false;
            }
        }
        return true;
    }
}
