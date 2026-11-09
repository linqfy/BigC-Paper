package dev.linqfy.bigCasares.modules.moderation;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ModerationSignalTrackerTest {
    private static final Instant NOW = Instant.parse("2026-07-15T12:00:00Z");

    @Test
    void localTunnelAndPrivateAddressesAreShared() {
        for (String ip : List.of("127.0.0.1", "10.0.0.4", "192.168.1.20", "172.16.3.1", "100.64.12.9", "::1",
            "fd00::1", "unknown", "not-an-ip")) {
            assertTrue(ModerationSignalTracker.sharedAddress(ip), ip);
        }
        assertFalse(ModerationSignalTracker.sharedAddress("8.8.8.8"));
        assertFalse(ModerationSignalTracker.sharedAddress("100.128.0.1"));
    }

    @Test
    void playersBehindOneSharedAddressProduceNoIpSignals() {
        ModerationSignalTracker tracker = new ModerationSignalTracker(Set.of());
        List<AbuseSignal> signals = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            signals.addAll(tracker.join(NOW.plusSeconds(i), UUID.randomUUID(), "P" + i, "127.0.0.1", true));
        }
        assertTrue(signals.isEmpty());
    }

    @Test
    void shortRepeatedChatRepliesAreNotSpam() {
        ModerationSignalTracker tracker = new ModerationSignalTracker(Set.of());
        UUID player = UUID.randomUUID();
        List<AbuseSignal> signals = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            signals.addAll(tracker.chat(NOW.plusSeconds(i * 2L), player, "P", "gg"));
        }
        assertTrue(signals.isEmpty());
    }

    @Test
    void repeatingAMenuCommandAFewTimesIsNotFlagged() {
        ModerationSignalTracker tracker = new ModerationSignalTracker(Set.of());
        UUID player = UUID.randomUUID();
        List<AbuseSignal> signals = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            signals.addAll(tracker.command(NOW.plusSeconds(i), player, "P", "/casino", false));
        }
        assertTrue(signals.isEmpty());
    }
}
