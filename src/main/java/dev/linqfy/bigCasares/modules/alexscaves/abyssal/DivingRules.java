package dev.linqfy.bigCasares.modules.alexscaves.abyssal;

/** Diving suit rules that need no server classes. */
final class DivingRules {
    static final int BREATHING_TICKS = 810;

    private DivingRules() {
    }

    /** Water Breathing is renewed unless a longer or stronger one is running. */
    static boolean shouldRenew(int amplifier, int duration) {
        return amplifier == 0 && duration < BREATHING_TICKS;
    }
}
