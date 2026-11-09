package dev.linqfy.bigCasares.modules.moderation.anticheat;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GroundSpoofCheckTest {
    private final GroundSpoofCheck check = new GroundSpoofCheck();
    private final UUID player = UUID.randomUUID();

    @Test
    void groundClaimsWithSupportOrInTheAirAreFine() {
        for (int i = 0; i < 100; i++) {
            assertTrue(check.sample(player, true, true, false).isEmpty());
            assertTrue(check.sample(player, false, false, false).isEmpty());
        }
    }

    @Test
    void anOccasionalMismatchIsForgotten() {
        for (int i = 0; i < 100; i++) {
            assertTrue(check.sample(player, i % 4 == 0, i % 4 != 0, false).isEmpty());
        }
    }

    @Test
    void repeatedGroundClaimsInTheAirAreFlagged() {
        Optional<Detection> detection = Optional.empty();
        for (int i = 0; i < 10 && detection.isEmpty(); i++) {
            detection = check.sample(player, true, false, false);
        }
        assertEquals("ground-spoof", detection.orElseThrow().rule());
    }

    @Test
    void exemptionResets() {
        for (int i = 0; i < 5; i++) {
            check.sample(player, true, false, false);
        }
        check.sample(player, true, false, true);
        for (int i = 0; i < 5; i++) {
            assertTrue(check.sample(player, true, false, false).isEmpty());
        }
    }
}
