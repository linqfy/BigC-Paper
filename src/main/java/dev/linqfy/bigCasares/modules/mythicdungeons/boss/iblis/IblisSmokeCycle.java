package dev.linqfy.bigCasares.modules.mythicdungeons.boss.iblis;

/**
 * When the invisible Iblis of phase 1 throws fire, and what, without server types. Fireballs at the players and a
 * flame line alternate every {@link #PERIOD} ticks ({@link #FAST_PERIOD} below {@link #FAST_BELOW} of his health);
 * every third cast is a burst of embers around him instead if someone is hugging the smoke.
 */
public final class IblisSmokeCycle {

    public static final int FIRST_DELAY = 60;
    public static final int PERIOD = 80;
    public static final int FAST_PERIOD = 60;
    public static final double FAST_BELOW = 0.75;
    public static final int CAST_TICKS = 24;

    public enum Attack { FIREBALLS, FLAME_LINE, EMBER_RING }

    private int next;
    private int casts;
    private int lastCast;

    public IblisSmokeCycle(int startTick) {
        this.next = startTick + FIRST_DELAY;
    }

    /** The attack that starts this tick, or {@code null}. */
    public Attack advance(int tick, double healthFraction, boolean someoneClose) {
        if (tick < next) {
            return null;
        }
        next = tick + (healthFraction < FAST_BELOW ? FAST_PERIOD : PERIOD);
        lastCast = tick;
        int index = casts++;
        if (someoneClose && index % 3 == 2) {
            return Attack.EMBER_RING;
        }
        return index % 2 == 0 ? Attack.FIREBALLS : Attack.FLAME_LINE;
    }

    /** {@code true} for {@link #CAST_TICKS} after a cast, while Iblis stays put and throws. */
    public boolean casting(int tick) {
        return casts > 0 && tick - lastCast < CAST_TICKS;
    }

    /** How many fireballs for {@code players} targets: one each up to three. */
    public static int fireballs(int players) {
        return Math.max(0, Math.min(3, players));
    }
}
