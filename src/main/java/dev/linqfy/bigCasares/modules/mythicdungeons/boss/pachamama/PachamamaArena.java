package dev.linqfy.bigCasares.modules.mythicdungeons.boss.pachamama;

import java.util.ArrayList;
import java.util.List;

/**
 * The boss room as block coordinates (no server types): interior columns {@code minX..maxX}, {@code minZ..maxZ}
 * (inclusive) and {@code floorY}, the first air layer (where players stand; the floor blocks are at
 * {@code floorY - 1}). It is measured at spawn by walking from the boss to the walls, so nothing leaves the room.
 * The floor is cut into {@link PachamamaTuning#TILE_SIZE}-block tiles from the north-west corner: the chessboard.
 */
public record PachamamaArena(int minX, int maxX, int minZ, int maxZ, int floorY) {

    /** Farthest a wall is looked for. */
    public static final int MAX_REACH = 20;
    /** Used when no wall is found in a direction (spawned by an admin in the open). */
    public static final int OPEN_REACH = 15;
    /** Blocks above the walking level that still count as inside the room (the generated arena has 16). */
    public static final double HEADROOM = 17.0;

    private static final int[] PROBE_OFFSETS = {-6, -3, 0, 3, 6};

    public PachamamaArena {
        if (maxX < minX || maxZ < minZ) {
            throw new IllegalArgumentException("inverted arena");
        }
    }

    /** Whether a block lets a body through (air, water, torches...). */
    @FunctionalInterface
    public interface Passable {
        boolean passable(int x, int y, int z);
    }

    /**
     * The walking level under a boss spawned at (x, y, z): the first air layer over a solid block, looked for a few
     * blocks to the sides because the boss stands on the altar. Falls back to {@code y}.
     */
    public static int floorUnder(int x, int y, int z, Passable world) {
        int[][] sides = {{3, 0}, {-3, 0}, {0, 3}, {0, -3}};
        for (int[] side : sides) {
            for (int level = y; level >= y - 6; level--) {
                if (!world.passable(x + side[0], level - 1, z + side[1])
                    && world.passable(x + side[0], level, z + side[1])) {
                    return level;
                }
            }
        }
        return y;
    }

    /**
     * Measures the room around (originX, originZ): per direction the longest free run of several parallel probe
     * lines, so one pillar or the entrance stairs do not shrink the room; no wall within {@link #MAX_REACH} makes
     * that side {@link #OPEN_REACH} long.
     */
    public static PachamamaArena probe(int originX, int originZ, int floorY, Passable world) {
        return new PachamamaArena(
            originX - reach(longest(originX, originZ, floorY, -1, 0, world)),
            originX + reach(longest(originX, originZ, floorY, 1, 0, world)),
            originZ - reach(longest(originX, originZ, floorY, 0, -1, world)),
            originZ + reach(longest(originX, originZ, floorY, 0, 1, world)),
            floorY);
    }

    private static int longest(int originX, int originZ, int floorY, int stepX, int stepZ, Passable world) {
        int best = 0;
        for (int offset : PROBE_OFFSETS) {
            int startX = originX + (stepX == 0 ? offset : 0);
            int startZ = originZ + (stepZ == 0 ? offset : 0);
            for (int y = floorY; y <= floorY + 1; y++) {
                if (!world.passable(startX, y, startZ)) {
                    continue;
                }
                int free = -1;
                for (int index = 1; index <= MAX_REACH; index++) {
                    if (!world.passable(startX + stepX * index, y, startZ + stepZ * index)) {
                        free = index - 1;
                        break;
                    }
                }
                if (free < 0) {
                    return -1;
                }
                best = Math.max(best, free);
            }
        }
        return best;
    }

    private static int reach(int free) {
        return free < 0 ? OPEN_REACH : Math.min(free, MAX_REACH);
    }

    public double centerX() {
        return (minX + maxX + 1) / 2.0;
    }

    public double centerZ() {
        return (minZ + maxZ + 1) / 2.0;
    }

    public boolean contains(double x, double z) {
        return x >= minX && x < maxX + 1 && z >= minZ && z < maxZ + 1;
    }

    /** Inside the room and at a height that belongs to it (feet coordinates). */
    public boolean containsFeet(double x, double y, double z) {
        return contains(x, z) && y >= floorY - 2.0 && y <= floorY + HEADROOM;
    }

    public double clampX(double x, double margin) {
        return clamp(x, minX + margin, maxX + 1 - margin);
    }

    public double clampZ(double z, double margin) {
        return clamp(z, minZ + margin, maxZ + 1 - margin);
    }

    private static double clamp(double value, double low, double high) {
        if (low > high) {
            return (low + high) / 2.0;
        }
        return Math.max(low, Math.min(high, value));
    }

    // ----------------------------------------------------------------------- chessboard

    public PachamamaTile tileAt(int blockX, int blockZ) {
        int size = PachamamaTuning.TILE_SIZE;
        return new PachamamaTile(Math.floorDiv(blockX - minX, size), Math.floorDiv(blockZ - minZ, size));
    }

    /** Every tile that has at least one column inside the room, west to east, north to south. */
    public List<PachamamaTile> tiles() {
        int size = PachamamaTuning.TILE_SIZE;
        int columns = (maxX - minX) / size + 1;
        int rows = (maxZ - minZ) / size + 1;
        List<PachamamaTile> tiles = new ArrayList<>(columns * rows);
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                tiles.add(new PachamamaTile(column, row));
            }
        }
        return tiles;
    }

    /** The block columns {x, z} of a tile that are inside the room. */
    public List<int[]> columns(PachamamaTile tile) {
        int size = PachamamaTuning.TILE_SIZE;
        int x0 = minX + tile.column() * size;
        int z0 = minZ + tile.row() * size;
        List<int[]> columns = new ArrayList<>(size * size);
        for (int x = x0; x < x0 + size; x++) {
            for (int z = z0; z < z0 + size; z++) {
                if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
                    columns.add(new int[] {x, z});
                }
            }
        }
        return columns;
    }

    /** Centre of a tile in world x/z. */
    public double[] tileCenter(PachamamaTile tile) {
        int size = PachamamaTuning.TILE_SIZE;
        return new double[] {
            Math.min(minX + tile.column() * size + size / 2.0, maxX + 0.5),
            Math.min(minZ + tile.row() * size + size / 2.0, maxZ + 0.5)
        };
    }
}
