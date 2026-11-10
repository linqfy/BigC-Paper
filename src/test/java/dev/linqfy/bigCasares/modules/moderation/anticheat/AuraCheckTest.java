package dev.linqfy.bigCasares.modules.moderation.anticheat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AuraCheckTest {
    private final AuraCheck check = new AuraCheck();
    private final UUID attacker = UUID.randomUUID();

    @Test
    void computesAngles() {
        assertEquals(0, AuraCheck.angleDegrees(1, 0, 0, 3, 0, 0), 1e-9);
        assertEquals(90, AuraCheck.angleDegrees(1, 0, 0, 0, 0, 2), 1e-9);
        assertEquals(180, AuraCheck.angleDegrees(1, 0, 0, -1, 0, 0), 1e-9);
    }

    @Test
    void fightingInFrontOfYouNeverFlags() {
        for (int i = 0; i < 200; i++) {
            assertTrue(check.attack(attacker, 25 + i % 40, 2.5, false, false, false).isEmpty());
        }
    }

    @Test
    void occasionalBadSamplesAreForgotten() {
        for (int i = 0; i < 50; i++) {
            boolean odd = i % 5 == 0;
            assertTrue(check.attack(attacker, odd ? 150 : 20, 2.5, odd, false, false).isEmpty());
        }
    }

    @Test
    void hittingBehindRepeatedlyIsFlagged() {
        assertEquals("killaura-angle", firstOf(() -> check.attack(attacker, 160, 2.5, false, false, false)));
    }

    @Test
    void closeRangeAnglesAndWallsAreIgnored() {
        for (int i = 0; i < 20; i++) {
            assertTrue(check.attack(attacker, 170, 0.5, true, false, false).isEmpty());
        }
    }

    @Test
    void wallsContainersAndItemUseAreFlagged() {
        assertEquals("killaura-wall", firstOf(() -> check.attack(attacker, 10, 3, true, false, false)));
        assertEquals("killaura-inventory", firstOf(() -> check.attack(attacker, 10, 3, false, true, false)));
        assertEquals("killaura-use-item", firstOf(() -> check.attack(attacker, 10, 3, false, false, true)));
    }

    private static String firstOf(java.util.function.Supplier<List<Detection>> attack) {
        List<Detection> detections = new ArrayList<>();
        for (int i = 0; i < 10 && detections.isEmpty(); i++) {
            detections.addAll(attack.get());
        }
        return detections.getFirst().rule();
    }
}
