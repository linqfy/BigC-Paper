package dev.linqfy.bigCasares.modules.sift;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Optional;
import java.util.Random;

/**
 * Random places to walk to, in place of Minecraft's {@code DefaultRandomPos}, which Paper does not expose: a
 * spot within a horizontal and vertical range, optionally in the direction of or away from a point, with solid
 * ground under it and room to stand. Only loaded chunks are looked at.
 */
final class LandSpots {

    private static final int ATTEMPTS = 10;

    private LandSpots() {
    }

    /**
     * {@code getPosTowards}: a standable spot within {@code horizontal} and {@code vertical} blocks of {@code from}
     * whose direction is within {@code maximumAngle} radians of the direction to {@code toward}.
     */
    static Optional<Location> towards(World world, Location from, SiftPoint toward, int horizontal, int vertical,
                                      double maximumAngle, int headroom, Random random) {
        double base = Math.atan2(toward.z() - from.getZ(), toward.x() - from.getX()) - Math.PI / 2.0;
        return pick(world, from, base, maximumAngle, horizontal, vertical, headroom, random);
    }

    /** {@code getPosAway}: as {@link #towards} for the direction away from the point, within a quarter turn. */
    static Optional<Location> away(World world, Location from, SiftPoint avoid, int horizontal, int vertical, int headroom,
                                   Random random) {
        double base = Math.atan2(from.getZ() - avoid.z(), from.getX() - avoid.x()) - Math.PI / 2.0;
        return pick(world, from, base, Math.PI / 2.0, horizontal, vertical, headroom, random);
    }

    /** {@code getPos}: a standable spot in any direction. */
    static Optional<Location> any(World world, Location from, int horizontal, int vertical, int headroom, Random random) {
        return pick(world, from, 0.0, Math.PI, horizontal, vertical, headroom, random);
    }

    private static Optional<Location> pick(World world, Location from, double base, double maximumAngle, int horizontal,
                                           int vertical, int headroom, Random random) {
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            double angle = base + (2.0 * random.nextFloat() - 1.0) * maximumAngle;
            double distance = Math.sqrt(random.nextDouble()) * Math.sqrt(2.0) * horizontal;
            int x = (int) Math.floor(from.getX() - distance * Math.sin(angle));
            int z = (int) Math.floor(from.getZ() + distance * Math.cos(angle));
            int y = from.getBlockY() + random.nextInt(2 * vertical + 1) - vertical;
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            Optional<Location> spot = standable(world, x, y, z, vertical, headroom);
            if (spot.isPresent()) {
                return spot;
            }
        }
        return Optional.empty();
    }

    /** Looks for ground from the given height downward, then upward, and returns the feet position over it. */
    private static Optional<Location> standable(World world, int x, int y, int z, int range, int headroom) {
        int low = Math.max(world.getMinHeight() + 1, y - range);
        int high = Math.min(world.getMaxHeight() - headroom - 1, y + range);
        for (int candidate = Math.min(y, high); candidate >= low; candidate--) {
            if (isFloor(world.getBlockAt(x, candidate - 1, z)) && hasRoom(world, x, candidate, z, headroom)) {
                return Optional.of(new Location(world, x + 0.5, candidate, z + 0.5));
            }
        }
        return Optional.empty();
    }

    private static boolean isFloor(Block block) {
        return !block.getCollisionShape().getBoundingBoxes().isEmpty() && !block.isLiquid();
    }

    private static boolean hasRoom(World world, int x, int y, int z, int headroom) {
        for (int dy = 0; dy < headroom; dy++) {
            Block block = world.getBlockAt(x, y + dy, z);
            if (!block.getCollisionShape().getBoundingBoxes().isEmpty() || block.isLiquid()) {
                return false;
            }
        }
        return true;
    }
}
