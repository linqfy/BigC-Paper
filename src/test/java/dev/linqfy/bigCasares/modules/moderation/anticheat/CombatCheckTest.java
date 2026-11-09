package dev.linqfy.bigCasares.modules.moderation.anticheat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CombatCheckTest {
    private final CombatCheck check = new CombatCheck(22);
    private final UUID attacker = UUID.randomUUID();
    private final UUID target = UUID.randomUUID();

    @Test
    void measuresDistanceToTheTargetBox() {
        assertEquals(0, CombatCheck.distanceToBox(0.5, 1.5, 0.5, 0, 0, 0, 1, 2, 1), 1e-9);
        assertEquals(2.0, CombatCheck.distanceToBox(3, 1, 0.5, 0, 0, 0, 1, 2, 1), 1e-9);
    }

    @Test
    void allowedReachGrowsWithPingButIsCapped() {
        assertEquals(4.0, CombatCheck.allowedReach(3.0, 0), 1e-9);
        assertTrue(CombatCheck.allowedReach(3.0, 150) > CombatCheck.allowedReach(3.0, 50));
        assertEquals(5.75, CombatCheck.allowedReach(3.0, 5_000), 1e-9);
    }

    @Test
    void normalFightingAtFifteenClicksPerSecondNeverFlags() {
        long now = 0;
        for (int i = 0; i < 300; i++) {
            now += 66;
            assertTrue(check.attack(attacker, now, target, 3.2, 4.3).isEmpty());
        }
    }

    @Test
    void oneLaggyHitIsNotAReachFlag() {
        assertTrue(check.attack(attacker, 0, target, 5.0, 4.3).isEmpty());
        for (int i = 1; i <= 10; i++) {
            assertTrue(check.attack(attacker, i * 500L, target, 3.0, 4.3).isEmpty());
        }
        assertTrue(check.attack(attacker, 6_000, target, 5.0, 4.3).isEmpty());
    }

    @Test
    void repeatedLongReachIsFlagged() {
        List<Detection> detections = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            detections.addAll(check.attack(attacker, i * 500L, target, 6.0, 4.3));
        }
        assertEquals("melee-reach", detections.getFirst().rule());
    }

    @Test
    void autoclickerNeedsTwoBursts() {
        List<Detection> detections = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            detections.addAll(check.attack(attacker, i * 30L, target, Double.NaN, Double.NaN));
        }
        assertTrue(detections.isEmpty());
        for (int i = 0; i < 30; i++) {
            detections.addAll(check.attack(attacker, 3_000 + i * 30L, target, Double.NaN, Double.NaN));
        }
        assertEquals("melee-rate", detections.getFirst().rule());
    }

    @Test
    void switchingTargetsNormallyIsFine() {
        long now = 0;
        for (int i = 0; i < 100; i++) {
            now += 100;
            UUID next = i % 2 == 0 ? target : UUID.randomUUID();
            assertTrue(check.attack(attacker, now, next, Double.NaN, Double.NaN).isEmpty());
        }
    }

    @Test
    void hittingThreePlayersAtOnceTwiceIsFlagged() {
        List<Detection> detections = new ArrayList<>();
        for (long burst : new long[] {0, 2_000}) {
            for (int i = 0; i < 3; i++) {
                detections.addAll(check.attack(attacker, burst + i * 20L, UUID.randomUUID(), Double.NaN, Double.NaN));
            }
        }
        assertEquals("melee-multi-target", detections.getFirst().rule());
    }
}
