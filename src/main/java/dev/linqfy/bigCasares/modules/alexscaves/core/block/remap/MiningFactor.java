package dev.linqfy.bigCasares.modules.alexscaves.core.block.remap;

import java.util.OptionalDouble;

/**
 * Mining prediction fix: the factor the client's
 * {@code minecraft:block_break_speed} must be scaled by so that, digging the borrowed block it sees, it progresses as
 * fast as the server does on the real block.
 * <p>
 * Vanilla progress per tick is {@code toolSpeed / hardness / (canHarvest ? 30 : 100)}, where {@code toolSpeed}
 * already includes efficiency, and the player's effects (haste, fatigue, water, not on ground, the attribute itself)
 * multiply it for any block. Those effects are the same on both sides, so they cancel in the quotient and only the
 * block-dependent parts are compared.
 */
final class MiningFactor {
    /** Below this relative difference the prediction is good enough: nothing is sent. */
    static final double TOLERANCE = 0.001;
    /** Upper bound of {@code minecraft:block_break_speed}. */
    static final double ATTRIBUTE_MAX = 1024.0;

    private MiningFactor() {
    }

    /**
     * @param toolSpeed destroy speed of the held item on the block (enchantments included, 1.0 for a bare hand)
     * @param hardness  block hardness (negative: unbreakable)
     * @param harvest   whether the tool is good enough for drops (30 instead of 100 as divisor)
     */
    record Profile(double toolSpeed, double hardness, boolean harvest) {
        /** Progress per tick without the player's own multipliers; 0 when the block cannot be dug at a rate. */
        double progress() {
            if (!(toolSpeed > 0) || !(hardness > 0)) {
                return 0.0;
            }
            return toolSpeed / hardness / (harvest ? 30.0 : 100.0);
        }
    }

    /**
     * @return the multiplier for the client's break speed, empty when it is not needed (same speed) or not possible
     *         (instant or unbreakable blocks on either side)
     */
    static OptionalDouble factor(Profile server, Profile client) {
        double serverProgress = server.progress();
        double clientProgress = client.progress();
        if (serverProgress <= 0 || clientProgress <= 0) {
            return OptionalDouble.empty();
        }
        double factor = serverProgress / clientProgress;
        if (Math.abs(factor - 1.0) < TOLERANCE) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(factor);
    }

    /** The factor, limited so that {@code base * factor} stays inside the attribute's range. */
    static double clamp(double factor, double currentValue) {
        double limit = currentValue > 0 ? ATTRIBUTE_MAX / currentValue : factor;
        return Math.max(0.0, Math.min(factor, limit));
    }
}
