package dev.linqfy.bigCasares.modules.sift;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SingerTimingsTest {

    @Test
    void theModsAnimationLengthsBecomeWholeTicksRoundedUp() {
        assertEquals(148, SingerTimings.MOD.appearTicks());
        assertEquals(161, SingerTimings.MOD.singTicks());
        assertEquals(100, SingerTimings.MOD.disappearTicks());
    }

    @Test
    void tinyAndZeroLengthsStillTakeATick() {
        assertEquals(1, SingerTimings.ticksFor(0.0));
        assertEquals(1, SingerTimings.ticksFor(0.04));
        assertEquals(2, SingerTimings.ticksFor(0.051));
    }
}
