package dev.linqfy.bigCasares.modules.mythicdungeons.boss.pachamama;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PachamamaShockwaveTest {

    @Test
    void theRingHitsAGroundedPlayerOnceWhenItPasses() {
        PachamamaShockwave wave = new PachamamaShockwave(0.0, 0.0);
        UUID player = UUID.randomUUID();
        int hits = 0;
        int hitTick = -1;
        for (int tick = 1; wave.advance(); tick++) {
            if (wave.hits(player, 6.0, 0.0, true)) {
                hits++;
                hitTick = tick;
            }
        }
        assertEquals(1, hits);
        double radiusAtHit = hitTick * PachamamaTuning.WAVE_SPEED;
        assertTrue(Math.abs(radiusAtHit - 6.0) <= PachamamaTuning.WAVE_HALF_WIDTH + PachamamaTuning.WAVE_SPEED);
    }

    @Test
    void jumpingOverTheFrontAvoidsIt() {
        PachamamaShockwave wave = new PachamamaShockwave(10.0, 10.0);
        UUID player = UUID.randomUUID();
        boolean hit = false;
        while (wave.advance()) {
            boolean nearFront = Math.abs(wave.radius() - 5.0) < 2.0;
            hit |= wave.hits(player, 15.0, 10.0, !nearFront);
        }
        assertFalse(hit);
    }

    @Test
    void theWaveEndsAtItsMaximumRadiusAndNeverReachesFarther() {
        PachamamaShockwave wave = new PachamamaShockwave(0.0, 0.0);
        UUID player = UUID.randomUUID();
        boolean far = false;
        int ticks = 0;
        while (wave.advance()) {
            far |= wave.hits(player, PachamamaTuning.WAVE_MAX_RADIUS + 3.0, 0.0, true);
            ticks++;
        }
        assertFalse(far);
        assertEquals((int) Math.ceil(PachamamaTuning.WAVE_MAX_RADIUS / PachamamaTuning.WAVE_SPEED), ticks);
    }

    @Test
    void theStrumWarnsBeforeEveryWave() {
        PachamamaChordCycle cycle = new PachamamaChordCycle();
        int lastStrum = -1;
        int waves = 0;
        for (int tick = 1; tick <= 1000; tick++) {
            Optional<PachamamaChordCycle.Cue> cue = cycle.advance();
            if (cue.isEmpty()) {
                continue;
            }
            if (cue.get() == PachamamaChordCycle.Cue.STRUM) {
                lastStrum = tick;
            } else {
                assertEquals(PachamamaTuning.CHORD_WARN_TICKS, tick - lastStrum);
                assertTrue(PachamamaTuning.CHORD_WARN_TICKS >= 16);
                if (waves == 0) {
                    assertEquals(PachamamaTuning.FIRST_CHORD_TICKS, tick);
                }
                waves++;
            }
        }
        assertEquals(1 + (1000 - PachamamaTuning.FIRST_CHORD_TICKS) / PachamamaTuning.CHORD_PERIOD_TICKS, waves);
    }
}
