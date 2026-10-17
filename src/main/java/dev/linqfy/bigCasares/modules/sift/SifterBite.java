package dev.linqfy.bigCasares.modules.sift;

/**
 * The timeline of the Sifter's bite ({@code SifterEntity#tick}): the mod starts a
 * 14-tick bite when its melee goal attacks, stands still while it lasts (ages 1 to 14),
 * plays the attack sound on age 8 and deals the damage on age 13. The starting tick counts
 * as the first one, so after {@link #advance()} has been called {@code n} times the age is
 * {@code n + 1}.
 *
 * <p>The melee goal that starts the bite waits {@value #COOLDOWN_TICKS} ticks before its next
 * attack; the port keeps the Sifter busy for that long (standing still for the first 14,
 * then closing in) so the attack rate stays the mod's.</p>
 */
final class SifterBite {

    static final int SOUND_TICK = 8;
    static final int DAMAGE_TICK = 13;
    /** The Sifter stands still up to and including this age. */
    static final int DURATION_TICKS = 14;
    /** Vanilla's {@code MeleeAttackGoal} cooldown after an attack. */
    static final int COOLDOWN_TICKS = 20;
    /** {@code SifterEntity.BITE_REACH} squared: the target must still be this close when the bite lands. */
    static final double REACH_SQUARED = 6.25;

    enum Stage {
        NONE,
        /** The attack sound plays. */
        SOUND,
        /** The damage lands (if the target is still in reach and in sight). */
        DAMAGE,
        /** The bite is over (the tick after age 14); the Sifter moves again but cannot attack yet. */
        RELEASED,
        /** The melee cooldown is over. */
        FINISHED
    }

    private int age = 1;

    /** Moves one tick on and reports what happens on it. */
    Stage advance() {
        age++;
        if (age >= COOLDOWN_TICKS) {
            return Stage.FINISHED;
        }
        return switch (age) {
            case SOUND_TICK -> Stage.SOUND;
            case DAMAGE_TICK -> Stage.DAMAGE;
            case DURATION_TICKS + 1 -> Stage.RELEASED;
            default -> Stage.NONE;
        };
    }

    /** Whether the Sifter still has to stand still. */
    boolean frozen() {
        return age <= DURATION_TICKS;
    }

    int age() {
        return age;
    }
}
