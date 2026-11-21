package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enlil;

import java.util.ArrayList;
import java.util.List;

/**
 * The boss room as block coordinates (no server types): the interior columns {@code minX..maxX},
 * {@code minZ..maxZ} (inclusive) and {@code floorY}, the first air layer (where players stand).
 * It is measured at spawn by walking from the boss to the walls, so every mechanic stays inside the room.
 */
public record EnlilArena(int minX, int maxX, int minZ, int maxZ, int floorY) {

    /** Farthest a wall is looked for; a room without walls in reach is treated as this big. */
    public static final int MAX_REACH = 20;
    /** Used when no wall is found in a direction (spawned by an admin in the open). */
    public static final int OPEN_REACH = 16;
    /** Blocks above the walking level that still count as inside the room (the generated arena has 16). */
    public static final double HEADROOM = 17.0;

    public EnlilArena {
        if (maxX < minX || maxZ < minZ) {
            throw new IllegalArgumentException("inverted arena");
        }
    }

    /**
     * @param west free blocks toward -X before a wall ({@code -1} when none was found within {@link #MAX_REACH})
     */
    public static EnlilArena measured(int originX, int originZ, int floorY, int west, int east, int north, int south) {
        return new EnlilArena(originX - reach(west), originX + reach(east), originZ - reach(north),
            originZ + reach(south), floorY);
    }

    /** Whether a block lets a body through (air, water, torches...); stairs, slabs and walls do not. */
    @FunctionalInterface
    public interface Passable {
        boolean passable(int x, int y, int z);
    }

    /** Side offsets of the probe lines: a single line from the altar can hit the door stairs or a pillar. */
    private static final int[] PROBE_OFFSETS = {-6, -3, 0, 3, 6};

    /**
     * The walking level under a boss spawned at (x, y, z): the first air layer over a solid block, looked for a few
     * blocks to the sides because the boss usually stands on the altar. Falls back to {@code y}.
     */
    public static int floorUnder(int x, int y, int z, Passable world) {
        int[][] sides = {{3, 0}, {-3, 0}, {0, 3}, {0, -3}};
        for (int[] side : sides) {
            for (int level = y; level >= y - 6; level--) {
                if (!world.passable(x + side[0], level - 1, z + side[1]) && world.passable(x + side[0], level, z + side[1])) {
                    return level;
                }
            }
        }
        return y;
    }

    /**
     * Measures the room around (originX, originZ): in every direction, the longest free run of several parallel
     * lines (offsets {@link #PROBE_OFFSETS}) on the two flood layers, so one obstacle (the entrance stairs, a pillar,
     * the altar) does not shrink the room. A line that finds no wall within {@link #MAX_REACH} makes that side open.
     */
    public static EnlilArena probe(int originX, int originZ, int floorY, Passable world) {
        return measured(originX, originZ, floorY,
            longest(originX, originZ, floorY, -1, 0, world), longest(originX, originZ, floorY, 1, 0, world),
            longest(originX, originZ, floorY, 0, -1, world), longest(originX, originZ, floorY, 0, 1, world));
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
                int free = free(startX, y, startZ, stepX, stepZ, world);
                if (free < 0) {
                    return -1;
                }
                best = Math.max(best, free);
            }
        }
        return best;
    }

    private static int free(int fromX, int y, int fromZ, int stepX, int stepZ, Passable world) {
        for (int index = 1; index <= MAX_REACH; index++) {
            if (!world.passable(fromX + stepX * index, y, fromZ + stepZ * index)) {
                return index - 1;
            }
        }
        return -1;
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

    /** Clamps a world x to the interior, keeping {@code margin} blocks from the walls. */
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

    public boolean contains(double x, double z) {
        return x >= minX && x < maxX + 1 && z >= minZ && z < maxZ + 1;
    }

    /** Points on a circle around the centre (clamped inside), the first at {@code angle} radians. */
    public List<double[]> ring(int count, double radius, double angle) {
        List<double[]> points = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            double a = angle + index * 2.0 * Math.PI / count;
            points.add(new double[] {
                clampX(centerX() + Math.cos(a) * radius, 1.5),
                clampZ(centerZ() + Math.sin(a) * radius, 1.5)
            });
        }
        return points;
    }
}
