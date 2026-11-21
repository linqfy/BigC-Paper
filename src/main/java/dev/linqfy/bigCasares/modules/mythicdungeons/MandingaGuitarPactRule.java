package dev.linqfy.bigCasares.modules.mythicdungeons;

/**
 * Mandinga's deal, without server types: a heart (two health points of maximum health, for good) for the guitar.
 * One deal per player ever, and never below {@link #MIN_MAX_HEALTH}.
 */
public final class MandingaGuitarPactRule {

    /** What the deal costs, as maximum health. */
    public static final double HEART = 2.0;
    /** The deal is refused when it would leave the player with less maximum health than this (three hearts). */
    public static final double MIN_MAX_HEALTH = 6.0;

    public enum Outcome {
        /** Take the heart and give the guitar. */
        ACCEPT,
        /** This player already paid once: no second guitar. */
        ALREADY_PAID,
        /** Not enough maximum health left to pay. */
        TOO_WEAK
    }

    private MandingaGuitarPactRule() {
    }

    /**
     * @param paid      the player already carries the mark of the deal (persistent tag or the health modifier)
     * @param maxHealth the player's current maximum health
     */
    public static Outcome decide(boolean paid, double maxHealth) {
        if (paid) {
            return Outcome.ALREADY_PAID;
        }
        if (!Double.isFinite(maxHealth) || maxHealth - HEART < MIN_MAX_HEALTH - 1.0e-9) {
            return Outcome.TOO_WEAK;
        }
        return Outcome.ACCEPT;
    }

    /**
     * Whether the lost heart has to be put back on a player (join, respawn): they paid, the modifier is gone (some
     * server versions drop modifiers on death) and taking it again does not break the floor.
     */
    public static boolean restore(boolean paid, boolean hasModifier, double maxHealth) {
        return paid && !hasModifier && Double.isFinite(maxHealth) && maxHealth - HEART >= MIN_MAX_HEALTH - 1.0e-9;
    }
}
