package dev.linqfy.bigCasares.modules.sift;

import java.util.List;
import java.util.Random;

/**
 * Where Echo Golems appear: the search of the mod's {@code SiftEchoGolemSpawner}, written against a small
 * {@link Level} view so it runs without a server. Every {@value #CHECK_INTERVAL_TICKS} ticks (with mob spawning
 * on) a {@link #pass pass} first looks for a Soul Canyon that has no golem yet and places one near its entrance
 * (within twelve blocks, at most six blocks above or below it, on a floor with three free blocks above), then
 * gives each player in turn, if no golem is within 165x96x165 blocks, up to 28 attempts to find a spot 82 to 160
 * blocks away (a golden-angle spiral from a random phase) in a fully loaded area with no other golem within the
 * same box. The first success ends the pass.
 */
final class EchoGolemSpawning {

    static final int CHECK_INTERVAL_TICKS = 80;
    /** Half-extents of the box in which two golems keep apart. */
    static final double SPACING_HORIZONTAL = 165.0;
    static final double SPACING_VERTICAL = 96.0;

    private static final int ENTRANCE_RADIUS = 12;
    private static final int ENTRANCE_REACH = 6;
    private static final int ATTEMPTS = 28;
    private static final double GOLDEN_ANGLE = 2.399963229728653;
    private static final int MINIMUM_RADIUS = 82;
    private static final int RADIUS_SPREAD = 79;
    private static final int FLOOR_SEARCH = 12;

    private EchoGolemSpawning() {
    }

    /** Why a golem is placed: next to its canyon, or out in the open near a player. */
    enum Reason {
        STRUCTURE,
        NATURAL
    }

    /** What the search needs to know about the world. */
    interface Level {
        /** The chunk is loaded. */
        boolean isChunkLoaded(int chunkX, int chunkZ);

        /** {@code getHeight(MOTION_BLOCKING_NO_LEAVES)}: the first free Y above the highest blocking, non-leaf block. */
        int height(int x, int z);

        boolean isFullBlock(SiftPos pos);

        /** No fluid and no collision shape. */
        boolean isEmptySpace(SiftPos pos);

        /** A golem is within the box of half-extents {@code horizontal} x {@code vertical} x {@code horizontal} around the position. */
        boolean golemNear(SiftPos around, double horizontal, double vertical);
    }

    /** Creates one golem; false when it could not be placed (no room, or a cancelled spawn). */
    @FunctionalInterface
    interface Spawner {
        boolean spawn(SiftPos feet, Reason reason);
    }

    /** One pass of {@code SiftEchoGolemSpawner.tick}; returns whether a golem was placed. */
    static boolean pass(Level level, List<SiftPos> pendingEntrances, java.util.function.Consumer<SiftPos> placedAt,
                        List<SiftPos> players, Random random, Spawner spawner) {
        if (spawnCanyonGolem(level, pendingEntrances, placedAt, spawner)) {
            return true;
        }
        for (SiftPos player : players) {
            if (!level.golemNear(player, SPACING_HORIZONTAL, SPACING_VERTICAL) && trySpawn(level, player, random, spawner)) {
                return true;
            }
        }
        return false;
    }

    private static boolean spawnCanyonGolem(Level level, List<SiftPos> pending, java.util.function.Consumer<SiftPos> placedAt,
                                            Spawner spawner) {
        for (SiftPos entrance : pending) {
            if (!level.isChunkLoaded(entrance.x() >> 4, entrance.z() >> 4)) {
                continue;
            }
            for (int radius = 0; radius <= ENTRANCE_RADIUS; radius++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                            continue;
                        }
                        int x = entrance.x() + dx;
                        int z = entrance.z() + dz;
                        if (!areaLoaded(level, x, z)) {
                            continue;
                        }
                        SiftPos feet = findSpawnFeet(level, x, z);
                        if (feet == null || Math.abs(feet.y() - entrance.y()) > ENTRANCE_REACH
                            || !spawner.spawn(feet, Reason.STRUCTURE)) {
                            continue;
                        }
                        placedAt.accept(entrance);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean trySpawn(Level level, SiftPos around, Random random, Spawner spawner) {
        double phase = random.nextDouble() * Math.PI * 2.0;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            double angle = phase + attempt * GOLDEN_ANGLE;
            int radius = MINIMUM_RADIUS + random.nextInt(RADIUS_SPREAD);
            int x = around.x() + (int) Math.round(Math.cos(angle) * radius);
            int z = around.z() + (int) Math.round(Math.sin(angle) * radius);
            if (!areaLoaded(level, x, z)) {
                continue;
            }
            SiftPos feet = findSpawnFeet(level, x, z);
            if (feet == null || level.golemNear(feet, SPACING_HORIZONTAL, SPACING_VERTICAL)
                || !spawner.spawn(feet, Reason.NATURAL)) {
                continue;
            }
            return true;
        }
        return false;
    }

    /** The block column and its neighbours are in loaded chunks. */
    static boolean areaLoaded(Level level, int x, int z) {
        for (int chunkX = x - 1 >> 4; chunkX <= x + 1 >> 4; chunkX++) {
            for (int chunkZ = z - 1 >> 4; chunkZ <= z + 1 >> 4; chunkZ++) {
                if (!level.isChunkLoaded(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The first full block, from the surface down twelve blocks, with three empty blocks over it. */
    static SiftPos findSpawnFeet(Level level, int x, int z) {
        int top = level.height(x, z);
        for (int drop = 0; drop <= FLOOR_SEARCH; drop++) {
            SiftPos floor = new SiftPos(x, top - 1 - drop, z);
            SiftPos feet = floor.above();
            if (level.isFullBlock(floor) && level.isEmptySpace(feet) && level.isEmptySpace(feet.above())
                && level.isEmptySpace(feet.above(2))) {
                return feet;
            }
        }
        return null;
    }
}
