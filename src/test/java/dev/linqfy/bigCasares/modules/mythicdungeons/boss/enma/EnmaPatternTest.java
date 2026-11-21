package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enma;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnmaPatternTest {

    private static int total(EnmaPattern pattern, List<Double> aims) {
        int count = 0;
        for (int elapsed = 0; elapsed < pattern.activeTicks(); elapsed++) {
            count += pattern.shots(elapsed, aims, 0.0).size();
        }
        return count;
    }

    @Test
    void ringsAreEvenAndEveryOtherRingIsShiftedIntoTheGaps() {
        List<EnmaPattern.Shot> first = EnmaPattern.RING.shots(0, List.of(), 0.0);
        List<EnmaPattern.Shot> second = EnmaPattern.RING.shots(12, List.of(), 0.0);
        assertEquals(EnmaPattern.RING_BULLETS, first.size());
        double step = 2.0 * Math.PI / EnmaPattern.RING_BULLETS;
        for (int i = 1; i < first.size(); i++) {
            assertEquals(step, first.get(i).angle() - first.get(i - 1).angle(), 1.0e-9);
        }
        assertEquals(step / 2.0, second.get(0).angle() - first.get(0).angle(), 1.0e-9);
        assertTrue(EnmaPattern.RING.shots(5, List.of(), 0.0).isEmpty());
        assertEquals(3 * EnmaPattern.RING_BULLETS, total(EnmaPattern.RING, List.of()));
    }

    @Test
    void theSpiralTurnsBetweenEmissions() {
        List<EnmaPattern.Shot> a = EnmaPattern.SPIRAL.shots(0, List.of(), 0.0);
        List<EnmaPattern.Shot> b = EnmaPattern.SPIRAL.shots(4, List.of(), 0.0);
        assertEquals(EnmaPattern.SPIRAL_ARMS, a.size());
        assertTrue(b.get(0).angle() > a.get(0).angle());
        assertEquals(Math.PI / 2.0, a.get(1).angle() - a.get(0).angle(), 1.0e-9);
    }

    @Test
    void aimedFansAreCentredOnEachPlayer() {
        List<EnmaPattern.Shot> shots = EnmaPattern.AIMED.shots(0, List.of(1.0, -2.0), 0.0);
        assertEquals(10, shots.size());
        assertEquals(1.0, shots.get(2).angle(), 1.0e-9);
        assertEquals(-2.0, shots.get(7).angle(), 1.0e-9);
        assertEquals(3 * 10, total(EnmaPattern.AIMED, List.of(1.0, -2.0)));
        assertTrue(EnmaPattern.AIMED.shots(0, List.of(), 0.0).isEmpty());
    }

    @Test
    void theCrossSwitchesFromPlusToSaltireHalfway() {
        double plus = EnmaPattern.CROSS.shots(0, List.of(), 0.0).get(0).angle();
        int late = EnmaPattern.CROSS.activeTicks() / 2 + (4 - EnmaPattern.CROSS.activeTicks() / 2 % 4) % 4;
        double saltire = EnmaPattern.CROSS.shots(late, List.of(), 0.0).get(0).angle();
        assertEquals(Math.PI / 4.0, saltire - plus, 0.5);
        assertEquals(4, EnmaPattern.CROSS.shots(0, List.of(), 0.0).size());
    }

    @Test
    void theVolleyIsThreeBulletsAtTheTarget() {
        List<EnmaPattern.Shot> shots = EnmaPattern.VOLLEY.shots(0, List.of(0.5), 0.0);
        assertEquals(3, shots.size());
        assertEquals(0.5, shots.get(1).angle(), 1.0e-9);
    }

    @Test
    void everyBulletIsSlowEnoughToDodge() {
        for (EnmaPattern pattern : EnmaPattern.values()) {
            for (int elapsed = 0; elapsed < pattern.activeTicks(); elapsed++) {
                for (EnmaPattern.Shot shot : pattern.shots(elapsed, List.of(0.0), 0.0)) {
                    // un jugador camina a ~0,22 bloques por tick y corre a ~0,28
                    assertTrue(shot.speed() > 0.0 && shot.speed() <= 0.3, pattern + " " + shot);
                }
            }
            assertTrue(pattern.shots(-1, List.of(0.0), 0.0).isEmpty());
            assertTrue(pattern.shots(pattern.activeTicks(), List.of(0.0), 0.0).isEmpty());
        }
    }
}
