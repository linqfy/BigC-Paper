package dev.linqfy.bigCasares.modules.alexscaves.magnetic;

/**
 * Pure rules of the Boundroid and its winch, taken from the mod's {@code BoundroidWinchEntity} and
 * {@code BoundroidEntity}: latch progress, chain length, how hard the chain pulls the head up and when the head slams.
 */
final class BoundroidWinchRules {
    /** Mod {@code latchProgress} runs 0 to 5, one step a tick. */
    static final int MAX_LATCH_PROGRESS = 5;
    /** The latch state flips once its timer is above this many ticks. */
    static final int LATCH_FLIP_TICKS = 5;
    /** Highest ceiling (above the winch's top) it can latch to; a ceiling farther than this lets go again. */
    static final double LATCH_CEILING_DISTANCE = 2.9;
    /** Chain length hanging from a latched winch (it bobs 0.25 around it) and trailing a loose one. */
    static final double LATCHED_CHAIN = 1.25;
    static final double LOOSE_CHAIN = 3.5;
    /** Farther than this from its winch with no line of sight, the head is dragged through the blocks. */
    static final double PHASE_DISTANCE = 7.0;
    /** A head this close horizontally to its target slams. */
    static final double SLAM_RANGE = 1.2;
    /** Ticks the head stays down after a slam, and the base of the pause before the next one. */
    static final int STAY_ON_GROUND_TICKS = 10;
    static final int SLAM_PAUSE_BASE = 30;
    static final int SLAM_PAUSE_RANDOM = 20;
    /** A hit winch cannot latch for this many ticks plus a random part. */
    static final int DAZED_BASE = 60;
    static final int DAZED_RANDOM = 60;
    /** Most chain links drawn however long the chain is (the links stretch instead). */
    static final int MAX_LINKS = 24;

    private BoundroidWinchRules() {
    }

    /** One tick of the mod's {@code latchProgress}: up to 5 while latched, down to 0 while not. */
    static int stepLatchProgress(int progress, boolean latched) {
        if (latched && progress < MAX_LATCH_PROGRESS) {
            return progress + 1;
        }
        if (!latched && progress > 0) {
            return progress - 1;
        }
        return progress;
    }

    /** Mod {@code getLatchProgress}: the progress as a fraction of its full travel. */
    static float latchFraction(int progress) {
        return Math.max(0, Math.min(MAX_LATCH_PROGRESS, progress)) / (float) MAX_LATCH_PROGRESS;
    }

    /** Model state loop: the latch pose once it is mostly latched; otherwise walk or idle by movement. */
    static String latchState(int progress) {
        return progress * 2 > MAX_LATCH_PROGRESS ? "latch" : null;
    }

    /** Length the winch tries to keep its chain at: a bobbing 1.25 while latched, a loose 3.5 otherwise. */
    static double chainLength(boolean latched, int age) {
        return latched ? LATCHED_CHAIN + Math.sin(age * 0.1F) * 0.25 : LOOSE_CHAIN;
    }

    /** Mod {@code stopPullingUp}: the head is not reeled up while it slams or stays on the ground after. */
    static boolean stopPullingUp(boolean slamming, int stayOnGround) {
        return slamming || stayOnGround > 0;
    }

    /**
     * Strength with which the chain pulls the head toward the winch: the overshoot past the chain length, at most 1,
     * times 0.3 when the head has a target (it climbs fast to strike again) or 0.1 otherwise. Zero when the chain is
     * slack or the head must stay down.
     */
    static double pull(double distance, double length, boolean stopPulling, boolean hasTarget) {
        if (distance <= length || stopPulling) {
            return 0.0;
        }
        return Math.min(distance - length, 1.0) * (hasTarget ? 0.3 : 0.1);
    }

    /** Mod: out of sight and past 7 blocks, the head gets no collision so the chain can bring it back. */
    static boolean phasesThroughBlocks(boolean headSeesWinch, boolean stopPulling, double distance) {
        return !headSeesWinch && !stopPulling && distance > PHASE_DISTANCE;
    }

    /** Mod {@code calculateDistanceToCeiling}: from the top of the winch up to the underside of the ceiling block. */
    static double ceilingDistance(int ceilingBlockY, double top) {
        return ceilingBlockY - top;
    }

    /**
     * Next value of the latch timer ({@code changeLatchStateTime}). Latched, it counts while the ceiling is too far,
     * the winch is dead or dazed; loose, it counts while a ceiling is within reach and it is not dazed.
     */
    static int nextLatchTimer(boolean latched, int timer, double ceilingDistance, boolean alive, int noLatchCooldown) {
        boolean counting = latched
            ? ceilingDistance > LATCH_CEILING_DISTANCE || !alive || noLatchCooldown > 0
            : ceilingDistance < LATCH_CEILING_DISTANCE && noLatchCooldown <= 0;
        return counting ? timer + 1 : 0;
    }

    /** Whether the latch state flips now. */
    static boolean flipsLatch(int timer) {
        return timer > LATCH_FLIP_TICKS;
    }

    /** Mod hop up: on the ground, not dazed, a ceiling out of reach but blocks above, one chance in 30 a tick. */
    static boolean hopsUp(boolean onGround, int noLatchCooldown, boolean alive, int roll, double ceilingDistance,
                          boolean blockAbove) {
        return onGround && noLatchCooldown == 0 && alive && roll == 0 && ceilingDistance > LATCH_CEILING_DISTANCE
            && blockAbove;
    }

    /** Mod: the head slams once its target is within 1.2 blocks horizontally and the pause is over. */
    static boolean startsSlam(boolean hasTarget, double horizontalDistance, int stopSlammingFor) {
        return hasTarget && horizontalDistance < SLAM_RANGE && stopSlammingFor == 0;
    }

    /** Pause before the next slam: {@code 30 + nextInt(20)}; {@code roll} is the 0-19 random part. */
    static int slamPause(int roll) {
        return SLAM_PAUSE_BASE + Math.max(0, Math.min(SLAM_PAUSE_RANDOM - 1, roll));
    }

    /** Ticks a hit winch cannot latch: {@code 60 + nextInt(60)}; {@code roll} is the 0-59 random part. */
    static int dazedTicks(int roll) {
        return DAZED_BASE + Math.max(0, Math.min(DAZED_RANDOM - 1, roll));
    }

    /** Chain links to draw for a chain of this length (one per block, at least one, capped). */
    static int links(double length) {
        return Math.max(1, Math.min(MAX_LINKS, (int) Math.ceil(length)));
    }
}
