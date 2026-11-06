package dev.linqfy.bigCasares.modules.tideframe.engine.core.demo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RouletteSpinTest {
    @Test void everyChosenColorFinishesExactlyAtItsOwnResultAndAngle() {
        for (var chosen : RouletteSpin.Color.values()) {
            var spin = new RouletteSpin(chosen);
            assertFalse(spin.finished());
            double previous = spin.angle();
            for (int step = 1; step <= RouletteSpin.DURATION_TICKS; step++) {
                String frame = spin.tick();
                assertTrue(spin.angle() >= previous);
                assertTrue(spin.angle() <= 1080 + chosen.ordinal() * 90);
                if (step < RouletteSpin.DURATION_TICKS) assertTrue(frame.startsWith("spin_"));
                previous = spin.angle();
            }
            assertTrue(spin.finished());
            assertEquals("win_" + chosen.name().toLowerCase(java.util.Locale.ROOT), spin.frameId());
            assertEquals(1080 + chosen.ordinal() * 90, spin.angle(), .000001);
            assertEquals(chosen, spin.chosenColor());
            assertEquals(spin.frameId(), spin.tick(), "Finished spin is stable");
        }
    }

    @Test void spinIsDeterministicAndDeceleratesInsteadOfFakingRandomness() {
        var one = new RouletteSpin(RouletteSpin.Color.GOLD);
        var two = new RouletteSpin(RouletteSpin.Color.GOLD);
        double previousAngle = 0, previousDelta = Double.POSITIVE_INFINITY;
        for (int i = 0; i < RouletteSpin.DURATION_TICKS; i++) {
            assertEquals(one.tick(), two.tick());
            assertEquals(one.angle(), two.angle());
            double delta = one.angle() - previousAngle;
            assertTrue(delta < previousDelta);
            previousAngle = one.angle(); previousDelta = delta;
        }
        assertThrows(IllegalArgumentException.class, () -> new RouletteSpin(null));
    }
}
