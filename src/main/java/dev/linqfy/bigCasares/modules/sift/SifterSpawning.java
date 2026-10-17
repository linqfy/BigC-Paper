package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.blocks.MushroomState;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Where and how many Sifters spawn: the search of the mod's {@code SiftSifterSpawner}
 * (and {@code SifterEntity.canSpawnOn}), written against a small {@link Level} view so it
 * runs without a server. {@code SiftSifters} adapts a Bukkit world to {@link Level} and
 * turns {@link Spawner} callbacks into entities.
 *
 * <p>Rules, as in the mod: every {@value #CHECK_INTERVAL_TICKS} ticks, unless the difficulty is
 * Peaceful, each player in the Sift (alive, not a spectator) in turn gets up to
 * {@value #ATTEMPTS} attempts to find a spot 32-76 blocks away (a golden-angle spiral from a
 * random phase) in a loaded chunk. A spot is the feet position over a floor of siftslate,
 * siftslate growth, healthy sculk, dry healthy sculk or dry healthy sculk growth with two
 * free (collision-less) blocks above it; 80% of the time the search starts at the surface
 * and looks up to ten blocks higher, otherwise it starts at a random height from the world
 * bottom (+2) to six blocks under the surface and looks up to eighteen blocks down (caves).
 * A spot is used with the chance of its biome's {@link Profile}; the Sifters within 96x48x96
 * blocks are counted against the profile's cap, and a group of one to five (at most the
 * remaining cap) is placed within four blocks of the spot, each member on a floor of the
 * same profile. The first player whose attempts spawn anything ends the pass.</p>
 */
final class SifterSpawning {

    static final int CHECK_INTERVAL_TICKS = 80;
    static final int ATTEMPTS = 18;
    /** Half-extents of the box around a spot in which existing Sifters count against the cap. */
    static final int POPULATION_RADIUS = 96;
    static final int POPULATION_HEIGHT = 48;
    static final int MINIMUM_GROUP = 1;
    static final int MAXIMUM_GROUP = 5;

    private static final double GOLDEN_ANGLE = 2.399963229728653;
    private static final int MINIMUM_RADIUS = 32;
    private static final int RADIUS_SPREAD = 45;
    private static final float SURFACE_SHARE = 0.8F;
    private static final int SURFACE_REACH = 10;
    private static final int CAVE_REACH = 18;
    private static final int CAVE_CEILING_GAP = 6;
    private static final int MEMBER_ATTEMPTS = 12;
    private static final int MEMBER_SPREAD = 9;

    /**
     * Blocks of the mod's {@code SifterEntity.canSpawnOn}, as the keys {@link SiftBlocks#custom} knows.
     * The snowy growth is a separate block here but the same block in the mod.
     */
    private static final List<String> FLOOR_KEYS = List.of("siftslate", "siftslate_growth", "siftslate_growth[snowy=true]",
        "healthy_sculk", "dry_healthy_sculk", "dry_healthy_sculk_growth");

    /** The backing states a Sifter may spawn on. */
    static final Set<MushroomState> FLOORS = FLOOR_KEYS.stream()
        .map(key -> SiftBlocks.custom(key)
            .orElseThrow(() -> new IllegalStateException("Unknown Sift block " + key)).state())
        .collect(Collectors.toUnmodifiableSet());

    private static final Set<String> OVERGROWN_BIOMES = Set.of("the_sift:overgrown_clearing", "the_sift:overgrown_forest",
        "the_sift:overgrown_forest_slopes", "the_sift:overgrown_slopes", "the_sift:overgrown_peaks");

    private SifterSpawning() {
    }

    /** The mod's {@code SpawnProfile}: the chance a spot is used and how many Sifters fit in its area. */
    enum Profile {
        OVERGROWN_FOREST(0.36, 8),
        OVERGROWN(0.28, 6),
        WASTES(0.22, 5),
        OTHER_SIFT(0.18, 5);

        private final double chance;
        private final int localCap;

        Profile(double chance, int localCap) {
            this.chance = chance;
            this.localCap = localCap;
        }

        double chance() {
            return chance;
        }

        int localCap() {
            return localCap;
        }

        /** {@code profileAt}: the overgrown forest, the other overgrown biomes, the wastes, then every other Sift biome. */
        static Profile ofBiome(String biomeId) {
            if (OVERGROWN_BIOMES.contains(biomeId)) {
                return biomeId.equals("the_sift:overgrown_forest") ? OVERGROWN_FOREST : OVERGROWN;
            }
            return biomeId.equals("the_sift:sift_wastes") ? WASTES : OTHER_SIFT;
        }
    }

    /** A block position at the Sifter's feet. */
    record Feet(int x, int y, int z) {
        Feet above() {
            return new Feet(x, y + 1, z);
        }
    }

    /** A player's block column, the centre of one spawn search. */
    record Anchor(int x, int z) {
    }

    /** What the search needs to know about the world; every query is for a block position. */
    interface Level {
        int minY();

        /** The block column's chunk is loaded. The search never asks about columns it did not check first. */
        boolean isLoaded(int x, int z);

        /** {@code getHeight(MOTION_BLOCKING_NO_LEAVES)}: the first free Y above the highest blocking, non-leaf block. */
        int height(int x, int z);

        /** The block is one of {@link #FLOORS} and a full cube ({@code canSpawnOn} and {@code isCollisionShapeFullBlock}). */
        boolean isSpawnFloor(int x, int y, int z);

        boolean hasEmptyCollision(int x, int y, int z);

        Profile profileAt(int x, int y, int z);

        /** Sifters within {@value SifterSpawning#POPULATION_RADIUS}x{@value SifterSpawning#POPULATION_HEIGHT}x{@value SifterSpawning#POPULATION_RADIUS} blocks of the spot. */
        int sifterCount(Feet around);
    }

    /** Creates one Sifter; false when it could not be placed (the mod's collision check or a cancelled spawn). */
    @FunctionalInterface
    interface Spawner {
        boolean spawn(Feet feet, float yaw);
    }

    /**
     * One spawner pass ({@code SiftSifterSpawner.tick}): nothing on Peaceful, otherwise the anchors in order
     * until one spawns something.
     *
     * @return whether any Sifter was spawned
     */
    static boolean pass(boolean peaceful, List<Anchor> anchors, Level level, Random random, Spawner spawner) {
        if (peaceful) {
            return false;
        }
        for (Anchor anchor : anchors) {
            if (trySpawnNear(level, anchor.x(), anchor.z(), random, spawner)) {
                return true;
            }
        }
        return false;
    }

    /** {@code trySpawnNear}: picks a spot around the anchor and spawns a group there. */
    static boolean trySpawnNear(Level level, int aroundX, int aroundZ, Random random, Spawner spawner) {
        double phase = random.nextDouble() * Math.PI * 2.0;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            double angle = phase + attempt * GOLDEN_ANGLE;
            int radius = MINIMUM_RADIUS + random.nextInt(RADIUS_SPREAD);
            int x = aroundX + (int) Math.round(Math.cos(angle) * radius);
            int z = aroundZ + (int) Math.round(Math.sin(angle) * radius);
            if (!level.isLoaded(x, z)) {
                continue;
            }
            boolean surface = random.nextFloat() < SURFACE_SHARE;
            Feet center = findSpawnFeet(level, x, z, surface, random);
            if (center == null) {
                continue;
            }
            Profile profile = level.profileAt(center.x(), center.y(), center.z());
            if (random.nextDouble() >= profile.chance()) {
                continue;
            }
            int available = profile.localCap() - level.sifterCount(center);
            if (available < MINIMUM_GROUP) {
                return false;
            }
            int maximumGroup = Math.min(MAXIMUM_GROUP, available);
            int groupSize = MINIMUM_GROUP + random.nextInt(maximumGroup - MINIMUM_GROUP + 1);
            return spawnGroup(level, center, profile, groupSize, random, spawner);
        }
        return false;
    }

    /** {@code spawnGroup}: each member needs a floor of the same profile within four blocks of the spot (12 tries). */
    private static boolean spawnGroup(Level level, Feet center, Profile profile, int groupSize, Random random, Spawner spawner) {
        int spawned = 0;
        for (int member = 0; member < groupSize; member++) {
            Feet feet = null;
            for (int attempt = 0; attempt < MEMBER_ATTEMPTS && feet == null; attempt++) {
                int x = center.x() + random.nextInt(MEMBER_SPREAD) - MEMBER_SPREAD / 2;
                int z = center.z() + random.nextInt(MEMBER_SPREAD) - MEMBER_SPREAD / 2;
                boolean surface = random.nextFloat() < SURFACE_SHARE;
                if (!level.isLoaded(x, z)) {
                    continue;
                }
                Feet candidate = findSpawnFeet(level, x, z, surface, random);
                if (candidate == null || level.profileAt(candidate.x(), candidate.y(), candidate.z()) != profile
                    || !level.hasEmptyCollision(candidate.x(), candidate.y(), candidate.z())
                    || !level.hasEmptyCollision(candidate.x(), candidate.y() + 1, candidate.z())) {
                    continue;
                }
                feet = candidate;
            }
            if (feet == null) {
                continue;
            }
            if (spawner.spawn(feet, random.nextFloat() * 360.0F)) {
                spawned++;
            }
        }
        return spawned > 0;
    }

    /** {@code findSpawnFeet}: a surface search upwards from the top block, or a cave search downwards from a random height. */
    static Feet findSpawnFeet(Level level, int x, int z, boolean surface, Random random) {
        int top = level.height(x, z);
        if (surface) {
            return findFloor(level, x, z, top - 1, SURFACE_REACH, 1);
        }
        int highestCaveFloor = top - CAVE_CEILING_GAP;
        int minY = level.minY() + 2;
        if (highestCaveFloor <= minY) {
            return null;
        }
        int startY = minY + random.nextInt(highestCaveFloor - minY + 1);
        return findFloor(level, x, z, startY, CAVE_REACH, -1);
    }

    /** {@code findFloor}: the first floor from {@code startY} (stepping {@code direction}) with two free blocks above it. */
    static Feet findFloor(Level level, int x, int z, int startY, int attempts, int direction) {
        for (int offset = 0; offset <= attempts; offset++) {
            int floorY = startY + offset * direction;
            if (!level.isSpawnFloor(x, floorY, z)) {
                continue;
            }
            int feetY = floorY + 1;
            if (level.hasEmptyCollision(x, feetY, z) && level.hasEmptyCollision(x, feetY + 1, z)) {
                return new Feet(x, feetY, z);
            }
        }
        return null;
    }
}
