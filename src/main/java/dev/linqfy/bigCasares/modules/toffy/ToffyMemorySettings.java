package dev.linqfy.bigCasares.modules.toffy;

/**
 * The {@code memory:} section of {@code toffy_complementaries.yml}.
 *
 * @param enabled            when {@code false} the real level is always the current level
 * @param forgetAfterMillis  how long a piece the player no longer has is remembered; 0 means it is only forgotten on
 *                           death (if {@code forgetOnDeath})
 * @param forgetOnDeath      dying resets the memory to what the player has after dying
 * @param checkIntervalTicks how often online players are checked for equipment changes
 */
public record ToffyMemorySettings(boolean enabled, long forgetAfterMillis, boolean forgetOnDeath,
                                      int checkIntervalTicks) {
    public static final long DEFAULT_FORGET_AFTER_MINUTES = 60L;
    public static final int DEFAULT_CHECK_INTERVAL_TICKS = 20;
    public static final ToffyMemorySettings DEFAULT = new ToffyMemorySettings(
        true, DEFAULT_FORGET_AFTER_MINUTES * 60_000L, true, DEFAULT_CHECK_INTERVAL_TICKS);

    public ToffyMemorySettings {
        if (forgetAfterMillis < 0L) {
            throw new IllegalArgumentException("forgetAfterMillis must not be negative");
        }
        if (checkIntervalTicks < 1) {
            throw new IllegalArgumentException("checkIntervalTicks must be at least 1");
        }
    }
}
