package dev.linqfy.bigCasares.modules.mythicdungeons.boss.iblis;

import java.util.ArrayList;
import java.util.List;

/**
 * The boss room in block coordinates (no server types): interior columns {@code minX..maxX}, {@code minZ..maxZ}
 * (inclusive) and {@code floorY}, the first air layer where players stand. It is measured once at spawn by walking
 * from the altar to the walls, so the fight only sees players standing in the room: the runtime audience reaches 48
 * blocks and goes through walls (corridors, other floors).
 */
public record IblisRoom(int minX, int maxX, int minZ, int maxZ, int floorY) {

    /** Farthest a wall is looked for; a room without walls in reach is treated as {@link #OPEN_REACH}. */
    public static final int MAX_REACH = 20;
    public static final int OPEN_REACH = 16;
    /** Layers above the walking level that still count as in the room (jumps, the altar steps). */
    public static final double ABOVE = 7.0;
    /** Layers below the walking level that still count. */
    public static final double BELOW = 1.0;

    /** Side offsets of the probe lines: one line from the altar can hit the door stairs or a pillar. */
    private static final int[] PROBE_OFFSETS = {-6, -3, 0, 3, 6};

    public IblisRoom {
        if (maxX < minX || maxZ < minZ) {
            throw new IllegalArgumentException("inverted room");
        }
    }

    /** Whether a block lets a body through (air, torches...). */
    @FunctionalInterface
    public interface Passable {
        boolean passable(int x, int y, int z);
    }

    /**
     * The walking level under a boss spawned at (x, y, z) on the altar: the first air layer over a solid block, looked
     * for a few blocks to the sides. Falls back to {@code y}.
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

    /** Measures the room around (originX, originZ): the longest free run of several parallel lines per direction. */
    public static IblisRoom probe(int originX, int originZ, int floorY, Passable world) {
        return new IblisRoom(
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

    /** {@code (x, y, z)} are a player's feet in world coordinates. */
    public boolean contains(double x, double y, double z) {
        return containsColumn(x, z) && y >= floorY - BELOW && y <= floorY + ABOVE;
    }

    public boolean containsColumn(double x, double z) {
        return x >= minX && x < maxX + 1 && z >= minZ && z < maxZ + 1;
    }

    public double centerX() {
        return (minX + maxX + 1) / 2.0;
    }

    public double centerZ() {
        return (minZ + maxZ + 1) / 2.0;
    }

    /** Keeps a point inside the room, {@code margin} blocks from the walls. */
    public IblisPoint clamp(IblisPoint point, double margin) {
        return new IblisPoint(clamp(point.x(), minX + margin, maxX + 1 - margin),
            clamp(point.z(), minZ + margin, maxZ + 1 - margin));
    }

    private static double clamp(double value, double low, double high) {
        if (low > high) {
            return (low + high) / 2.0;
        }
        return Math.max(low, Math.min(high, value));
    }

    /**
     * Vessel spots to try, best first: four at the diagonals of the room centre, then four at the sides, then a
     * closer diagonal ring. The fight keeps the first free ones that are at least five blocks apart.
     */
    public List<IblisPoint> vesselCandidates() {
        List<IblisPoint> candidates = new ArrayList<>(ring(4, 7.0, Math.PI / 4.0));
        candidates.addAll(ring(4, 7.0, 0.0));
        candidates.addAll(ring(4, 4.5, Math.PI / 4.0));
        return candidates;
    }

    /**
     * {@code count} points on a circle of {@code radius} around the room centre (clamped two
     * blocks from the walls), the first at {@code angle} radians.
     */
    public List<IblisPoint> ring(int count, double radius, double angle) {
        List<IblisPoint> points = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            double a = angle + index * 2.0 * Math.PI / count;
            points.add(clamp(new IblisPoint(centerX() + Math.cos(a) * radius, centerZ() + Math.sin(a) * radius), 2.0));
        }
        return points;
    }
}
