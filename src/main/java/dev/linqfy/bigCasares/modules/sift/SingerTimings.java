package dev.linqfy.bigCasares.modules.sift;

/**
 * How long the Singer's appear, sing and disappear animations last, in ticks: the port of
 * {@code SingerAnimationTimings}, which reads {@code singer.animation.json} when the mod starts. The
 * lengths are fixed by the imported animation file (7.375, 8.0417 and 5 seconds), so they are constants
 * here; {@code SingerModelTest} compares them with the converted model.
 */
record SingerTimings(int appearTicks, int singTicks, int disappearTicks) {

    static final SingerTimings MOD = new SingerTimings(ticksFor(7.375), ticksFor(8.0417), ticksFor(5.0));

    /** The mod's rounding: at least one tick, whole ticks rounded up. */
    static int ticksFor(double seconds) {
        return Math.max(1, (int) Math.ceil(Math.max(0.05, seconds) * 20.0));
    }
}
