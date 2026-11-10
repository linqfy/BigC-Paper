package dev.linqfy.bigCasares.modules.moderation.anticheat;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HoverCheckTest {
    private final HoverCheck check = new HoverCheck();
    private final UUID player = UUID.randomUUID();

    @Test
    void aFallingPlayerIsNeverFlagged() {
        double y = 100;
        double velocity = 0.42;
        for (int tick = 0; tick < 200; tick++) {
            y += velocity;
            velocity = (velocity - 0.08) * 0.98;
            if (tick % 5 == 0) {
                assertTrue(check.sample(player, y, true, false).isEmpty());
            }
        }
    }

    @Test
    void hoveringInTheAirIsFlagged() {
        Optional<Detection> detection = Optional.empty();
        for (int i = 0; i < 10 && detection.isEmpty(); i++) {
            detection = check.sample(player, 120.0, true, false);
        }
        assertEquals("hover-flight", detection.orElseThrow().rule());
    }

    @Test
    void supportOrExemptionResetsTheCount() {
        for (int i = 0; i < 5; i++) {
            check.sample(player, 120.0, true, false);
        }
        assertTrue(check.sample(player, 120.0, false, false).isEmpty());
        for (int i = 0; i < 5; i++) {
            assertTrue(check.sample(player, 120.0, true, false).isEmpty());
        }
        assertTrue(check.sample(player, 120.0, true, true).isEmpty());
        for (int i = 0; i < 5; i++) {
            assertTrue(check.sample(player, 120.0, true, false).isEmpty());
        }
    }
}
