package dev.linqfy.bigCasares.modules.sift;

import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * What an Echo Golem and a Singer need to know about each other for a soul exchange, and the search for
 * the place where a Singer appears for a golem ({@code SoulExchangeManager.findNaturalSpawn}). The two
 * creatures are Bukkit-free state machines ({@link EchoGolemBrain}, {@link SingerEvent}); the runtime
 * classes hand each of them the other through these views.
 */
final class SoulExchange {

    /** Half-extents of the box around a golem in which it looks for a Singer already out ({@code requestSinger}). */
    static final double SINGER_SEARCH_HORIZONTAL = 52.0;
    static final double SINGER_SEARCH_VERTICAL = 32.0;

    private static final int SPAWN_ATTEMPTS = 16;
    private static final double SPAWN_ANGLE_STEP = 0.39269908169872414;
    private static final int SPAWN_MINIMUM_RADIUS = 7;
    private static final int SPAWN_RADIUS_SPREAD = 6;
    private static final double CANYON_CLEARANCE_SQUARED = 576.0;
    private static final int HEADROOM = 4;

    private SoulExchange() {
    }

    /** The golem, as the Singer sees it ({@code EchoGolemEntity}'s public surface). */
    interface Golem {
        UUID id();

        boolean isAlive();

        boolean hasSoulBlock();

        /** Not bonded to a player: bonded golems bring their soul to their player instead. */
        boolean canInteractWithSinger();

        SiftPoint position();

        /** {@code beginBow}: stops and bows to the Singer while it still carries the soul. */
        void beginBow();

        /** {@code finishBow}: ends the bow and goes back to searching. */
        void finishBow();

        /** {@code faceEntity}: turns toward a point. */
        void faceToward(SiftPoint point);

        /** {@code transferSoulToSinger}: hands over the soul; false when it has none to give. */
        boolean transferSoulToSinger();
    }

    /** The Singer, as the golem sees it. */
    interface Singer {
        UUID id();

        boolean isAlive();

        boolean isSoulEvent();

        SiftPos blockPosition();

        SiftPoint position();

        /** {@code offerSoulGolem}: the golem has a soul for this Singer. */
        void offerSoulGolem(Golem golem);
    }

    /** What a golem finds out about walking to a Singer that is already out. */
    enum Reach {
        /** Its path searches are spent for now; it asks again later. */
        NO_BUDGET,
        UNREACHABLE,
        REACHABLE
    }

    /** What the spawn search reads from the world. */
    interface Level {
        /** {@code getHeight(MOTION_BLOCKING_NO_LEAVES)}: the first free Y above the highest blocking, non-leaf block. */
        int height(int x, int z);

        boolean isLoaded(SiftPos pos);

        /** The block has a full cube collision shape. */
        boolean isFullBlock(SiftPos pos);

        boolean isAir(SiftPos pos);
    }

    /**
     * {@code findNaturalSpawn}: from a random phase, sixteen spots on a circle 7 to 12 blocks from the golem,
     * 22.5 degrees apart. A spot is the first free block over the surface; it needs a loaded chunk, a full
     * block under it, five blocks of air from there up and, when a canyon with Soul Blocks is within 48
     * blocks of the golem, to be at least 24 blocks from that canyon's cluster.
     */
    static Optional<SiftPos> findNaturalSpawn(Level level, SiftPos around, SiftPos activeCanyon, Random random) {
        double phase = random.nextDouble() * Math.PI * 2.0;
        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            double angle = phase + attempt * SPAWN_ANGLE_STEP;
            int radius = SPAWN_MINIMUM_RADIUS + random.nextInt(SPAWN_RADIUS_SPREAD);
            int x = around.x() + (int) Math.round(Math.cos(angle) * radius);
            int z = around.z() + (int) Math.round(Math.sin(angle) * radius);
            int y = level.height(x, z);
            SiftPos feet = new SiftPos(x, y, z);
            if (activeCanyon != null && feet.horizontalDistanceSqr(activeCanyon) < CANYON_CLEARANCE_SQUARED
                || !level.isLoaded(feet) || !level.isFullBlock(feet.below())) {
                continue;
            }
            boolean clear = true;
            for (int dy = 0; dy <= HEADROOM; dy++) {
                if (!level.isAir(feet.above(dy))) {
                    clear = false;
                    break;
                }
            }
            if (clear) {
                return Optional.of(feet);
            }
        }
        return Optional.empty();
    }
}
