package dev.linqfy.bigCasares.modules.quadrants.quests;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PvpQuestServiceTest {
    private final UUID alice = UUID.randomUUID(), bob = UUID.randomUUID(), carol = UUID.randomUUID();
    private final Map<UUID, PvpQuestState> data = new HashMap<>();
    private final PvpQuestService.Store store = new PvpQuestService.Store() {
        public PvpQuestState load(UUID player) { return data.getOrDefault(player, PvpQuestState.empty()); }
        public void save(UUID player, PvpQuestState state) { data.put(player, state); }
    };
    private final PvpQuestService service = new PvpQuestService(store, 2, 1, 3, 300, 500);

    @Test
    void rivalProgressRequiresOptInAndDistinctOpponents() throws IOException {
        service.kill(alice, bob, "2026-11-16");
        assertEquals(0, service.status(alice, "2026-11-16").rivals().size());
        service.start(alice, "rivals", "2026-11-16");
        service.kill(alice, bob, "2026-11-16");
        service.kill(alice, bob, "2026-11-16");
        assertEquals(1, service.status(alice, "2026-11-16").rivals().size());
        service.kill(alice, carol, "2026-11-16");
        assertTrue(service.ready(alice, "rivals", "2026-11-16"));
    }

    @Test
    void dominationStreakStartsAfterAcceptanceAndResetsOnRevenge() throws IOException {
        service.kill(alice, bob, "2026-11-16");
        service.start(alice, "domination", "2026-11-16");
        service.kill(alice, bob, "2026-11-16");
        service.kill(bob, alice, "2026-11-16");
        service.kill(alice, bob, "2026-11-16");
        service.kill(alice, bob, "2026-11-16");
        assertFalse(service.ready(alice, "domination", "2026-11-16"));
        service.kill(alice, bob, "2026-11-16");
        assertTrue(service.ready(alice, "domination", "2026-11-16"));
    }

    @Test
    void failedOrAmbiguousDepositIsNotReplayedAndSurvivesWeekChange() throws IOException {
        service.start(alice, "rivals", "2026-11-16");
        service.kill(alice, bob, "2026-11-16");
        service.kill(alice, carol, "2026-11-16");
        AtomicInteger deposits = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> service.claim(alice, "rivals", "2026-11-16", (id, amount) -> {
            assertEquals(PvpQuestState.Claim.PENDING, data.get(alice).rivalClaim());
            deposits.incrementAndGet();
            throw new IllegalStateException("provider uncertain");
        }));
        assertFalse(service.claim(alice, "rivals", "2026-11-23", (id, amount) -> deposits.incrementAndGet()));
        assertEquals(1, deposits.get());
        assertEquals(PvpQuestState.Claim.PENDING, service.status(alice, "2026-11-23").rivalClaim());
    }

    @Test
    void successfulRewardCanOnlyBeClaimedOnceAndResetsNextWeek() throws IOException {
        service.start(alice, "rivals", "2026-11-16");
        service.kill(alice, bob, "2026-11-16");
        service.kill(alice, carol, "2026-11-16");
        AtomicInteger deposits = new AtomicInteger();
        assertTrue(service.claim(alice, "rivals", "2026-11-16", (id, amount) -> deposits.incrementAndGet()));
        assertFalse(service.claim(alice, "rivals", "2026-11-16", (id, amount) -> deposits.incrementAndGet()));
        assertTrue(service.start(alice, "rivals", "2026-11-23"));
        assertEquals(0, service.status(alice, "2026-11-23").rivals().size());
        assertEquals(1, deposits.get());
    }

    @Test
    void persistenceFailurePreventsAnyDeposit() throws IOException {
        service.start(alice, "rivals", "2026-11-16");
        service.kill(alice, bob, "2026-11-16");
        service.kill(alice, carol, "2026-11-16");
        PvpQuestService.Store broken = new PvpQuestService.Store() {
            public PvpQuestState load(UUID player) { return data.getOrDefault(player, PvpQuestState.empty()); }
            public void save(UUID player, PvpQuestState state) throws IOException { throw new IOException("disk"); }
        };
        AtomicInteger deposits = new AtomicInteger();
        PvpQuestService failing = new PvpQuestService(broken, 2, 1, 3, 300, 500);
        assertThrows(IOException.class, () -> failing.claim(alice, "rivals", "2026-11-16", (id, amount) -> deposits.incrementAndGet()));
        assertEquals(0, deposits.get());
    }
}
