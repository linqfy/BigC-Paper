package dev.linqfy.bigCasares.modules.staffevents;

/**
 * Heart transfer of the Lifesteal event, in whole hearts.
 *
 * <p>A kill moves one heart from the victim to the killer. A victim already at the
 * protection floor loses nothing and the killer gains nothing, so fresh players are
 * not worth farming. The killer cannot exceed the cap.</p>
 */
public record LifestealRules(int heartsPerKill, int floorHearts, int maxHearts, int permanentRewardThreshold) {

    public LifestealRules {
        if (heartsPerKill <= 0) {
            throw new IllegalArgumentException("hearts-per-kill must be greater than zero");
        }
        if (floorHearts <= 0) {
            throw new IllegalArgumentException("floor-hearts must be greater than zero");
        }
        if (maxHearts < floorHearts) {
            throw new IllegalArgumentException("max-hearts must not be lower than floor-hearts");
        }
        if (permanentRewardThreshold <= 0) {
            throw new IllegalArgumentException("permanent-reward-threshold must be greater than zero");
        }
    }

    /** Result of one kill: hearts the victim loses and the killer gains (equal unless the cap trims it). */
    public record KillOutcome(int victimLoss, int killerGain, boolean floorProtected) {
    }

    public KillOutcome resolveKill(int victimHearts, int killerHearts) {
        int stealable = Math.max(0, Math.min(heartsPerKill, victimHearts - floorHearts));
        if (stealable == 0) {
            return new KillOutcome(0, 0, true);
        }
        int gain = Math.max(0, Math.min(stealable, maxHearts - killerHearts));
        return new KillOutcome(stealable, gain, false);
    }

    /** Whether a player finishing the event with {@code hearts} earns the permanent heart. */
    public boolean earnsPermanentHeart(int hearts) {
        return hearts >= permanentRewardThreshold;
    }
}
