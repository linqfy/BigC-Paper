package dev.linqfy.bigCasares.modules.moderation.anticheat;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class XrayTrackerTest {
    private final XrayTracker tracker = new XrayTracker();
    private final UUID player = UUID.randomUUID();

    @Test
    void branchMiningFindsVeinsAfterLotsOfStone() {
        long now = 0;
        for (int vein = 0; vein < 8; vein++) {
            for (int stone = 0; stone < 150; stone++) {
                now += 300;
                assertTrue(tracker.mined(player, now, XrayTracker.Kind.STONE, 0, 0, 0).isEmpty());
            }
            for (int ore = 0; ore < 6; ore++) {
                now += 300;
                assertTrue(tracker.mined(player, now, XrayTracker.Kind.RARE_ORE, vein * 50, -50, ore).isEmpty());
            }
        }
    }

    @Test
    void goingStraightToVeinsIsReported() {
        long now = 0;
        Optional<Detection> detection = Optional.empty();
        for (int vein = 0; vein < 6 && detection.isEmpty(); vein++) {
            for (int stone = 0; stone < 10; stone++) {
                tracker.mined(player, now += 300, XrayTracker.Kind.STONE, 0, 0, 0);
            }
            for (int ore = 0; ore < 4 && detection.isEmpty(); ore++) {
                detection = tracker.mined(player, now += 300, XrayTracker.Kind.RARE_ORE, vein * 50, -50, ore);
            }
        }
        assertEquals("xray-ratio", detection.orElseThrow().rule());
    }
}
