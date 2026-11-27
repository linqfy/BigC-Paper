package dev.linqfy.bigCasares.modules.alexscaves.primordial;

/**
 * Mod {@code SauropodBaseEntity.legBackAmount}: it follows the distance walked since the last step (and drops to zero
 * when the legs stop), so the hind legs are back during the second half of every stride. Here that is a switch for the
 * leg_back loop.
 */
final class SauropodLegs {
    /** Blocks of one stride (the step sound's). */
    static final double STRIDE = 4.0;

    private double since;

    /** @param moved blocks walked since the last call; @return whether the legs are back */
    boolean update(double moved, boolean moving) {
        if (!moving) {
            since = 0.0;
            return false;
        }
        since += moved;
        if (since >= STRIDE) {
            since -= STRIDE;
        }
        return since >= STRIDE / 2.0;
    }
}
