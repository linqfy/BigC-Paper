package dev.linqfy.bigCasares.modules.legendary;

public final class LegendaryStormTiming {
    private LegendaryStormTiming() {}

    /** Five pulses: impact, +2s, +4s, +6s, +8s. The storm ends at +10s. */
    public static boolean pulse(int elapsedTicks) {
        return elapsedTicks >= 0 && elapsedTicks < LegendaryWeapon.STORM_TICKS
            && elapsedTicks % LegendaryWeapon.LIGHTNING_INTERVAL_TICKS == 0;
    }
}
