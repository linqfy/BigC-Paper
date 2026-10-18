package dev.linqfy.bigCasares.modules.sift;

import java.util.Optional;
import java.util.Random;

/**
 * Where a rift may open: the mod's {@code RiftManager.space}, {@code column},
 * {@code findNatural} and {@code findNear}. A rift is 9 blocks wide along one axis
 * and 5 tall, needs that volume free of everything but plants, and ground within
 * three blocks below its base.
 */
final class RiftPlacement {

    /** Block access for the search; implementations only report loaded chunks as usable. */
    interface Terrain {
        int minY();

        /** Exclusive top of the build range. */
        int maxY();

        /** First free Y above the motion-blocking surface. */
        int surface(int x, int z);

        /** Air or a plant (vegetation, vines, hanging roots), without fluid. */
        boolean clear(int x, int y, int z);

        /** Inside the world border and loaded. */
        boolean usable(int x, int z);
    }

    /** Base block of the rift (bottom centre) and its long axis. */
    record Spot(int x, int y, int z, boolean alongX) {

        /** Squared distance from the base centre, where the mod's rift entity stands ({@code RiftEntity.position()}). */
        double distanceSquaredTo(double pointX, double pointY, double pointZ) {
            double dx = x + 0.5 - pointX;
            double dy = y - pointY;
            double dz = z + 0.5 - pointZ;
            return dx * dx + dy * dy + dz * dz;
        }

        /** {@code RiftEntity.getPortalBounds()} around the base centre. */
        boolean touches(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            double centerX = x + 0.5;
            double centerZ = z + 0.5;
            double halfX = alongX ? 4.5 : 0.5;
            double halfZ = alongX ? 0.5 : 4.5;
            return maxX > centerX - halfX && minX < centerX + halfX && maxY > y - 0.375 && minY < y + 3.875
                && maxZ > centerZ - halfZ && minZ < centerZ + halfZ;
        }
    }

    private RiftPlacement() {
    }

    static boolean space(Terrain terrain, int x, int y, int z, boolean alongX) {
        if (y < terrain.minY() || y + 5 >= terrain.maxY()) {
            return false;
        }
        boolean supported = false;
        for (int gap = 1; gap <= 3; gap++) {
            if (!terrain.clear(x, y - gap, z)) {
                supported = true;
                break;
            }
        }
        if (!supported) {
            return false;
        }
        for (int offset = -4; offset <= 4; offset++) {
            int px = alongX ? x + offset : x;
            int pz = alongX ? z : z + offset;
            if (!terrain.usable(px, pz)) {
                return false;
            }
            for (int dy = 0; dy < 5; dy++) {
                if (!terrain.clear(px, y + dy, pz)) {
                    return false;
                }
            }
        }
        return true;
    }

    static Optional<Spot> column(Terrain terrain, int x, int z, boolean alongX) {
        if (!terrain.usable(x, z)) {
            return Optional.empty();
        }
        int surface = terrain.surface(x, z);
        for (int rise = 0; rise <= 2; rise++) {
            int y = surface + rise;
            if (space(terrain, x, y, z, alongX)) {
                return Optional.of(new Spot(x, y, z, alongX));
            }
            if (space(terrain, x, y, z, !alongX)) {
                return Optional.of(new Spot(x, y, z, !alongX));
            }
        }
        return Optional.empty();
    }

    /** A natural rift 20-64 blocks from a player in the Sift (48 attempts). */
    static Optional<Spot> findNatural(Terrain terrain, int centerX, int centerZ, Random random) {
        for (int attempt = 0; attempt < 48; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int distance = 20 + random.nextInt(45);
            Optional<Spot> found = column(terrain, centerX + (int) (Math.cos(angle) * distance),
                centerZ + (int) (Math.sin(angle) * distance), random.nextBoolean());
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    /** The exit rift nearest an Overworld anchor: the anchor itself, then rings every 16 blocks. */
    static Optional<Spot> findNear(Terrain terrain, int anchorX, int anchorZ, int maxRadius) {
        Optional<Spot> first = column(terrain, anchorX, anchorZ, true);
        if (first.isPresent()) {
            return first;
        }
        for (int radius = 16; radius <= maxRadius; radius += 16) {
            for (int index = 0; index < 16; index++) {
                double angle = (index + radius * 0.017) * Math.PI / 8.0;
                int x = anchorX + (int) Math.round(Math.cos(angle) * radius);
                int z = anchorZ + (int) Math.round(Math.sin(angle) * radius);
                Optional<Spot> found = column(terrain, x, z, (index & 1) == 0);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }
}
