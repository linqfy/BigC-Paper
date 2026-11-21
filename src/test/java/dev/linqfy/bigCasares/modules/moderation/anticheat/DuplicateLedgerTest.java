package dev.linqfy.bigCasares.modules.moderation.anticheat;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DuplicateLedgerTest {
    private final DuplicateLedger ledger = new DuplicateLedger();
    private final DuplicateLedger.Holder alice = new DuplicateLedger.Holder(UUID.randomUUID(), "Alice", "inventario");
    private final DuplicateLedger.Holder bob = new DuplicateLedger.Holder(UUID.randomUUID(), "Bob", "inventario");

    @Test
    void sharedContainerObservationIsNotAttributedToItsViewer() {
        DuplicateLedger.Holder shared = new DuplicateLedger.Holder(bob.playerId(), "Bob", "cofre", true);
        ledger.beginPass();
        ledger.seen("id-1", "ELYTRA", alice);
        ledger.seen("id-1", "ELYTRA", bob);
        ledger.seen("id-1", "ELYTRA", shared);
        assertEquals(1, ledger.endPass(0).size());
    }

    @Test
    void sharedLocationWinsEvenWhenPrivatePairWasSeenFirst() {
        DuplicateLedger.Holder shared = new DuplicateLedger.Holder(bob.playerId(), "Bob", "cofre", true);
        ledger.beginPass();
        ledger.seen("id-1", "ELYTRA", alice);
        ledger.seen("id-1", "ELYTRA", bob);
        ledger.seen("id-1", "ELYTRA", shared);
        assertTrue(ledger.endPass(0).getFirst().hasSharedContainer());
    }

    @Test
    void anItemMovingBetweenPassesIsNotADupe() {
        ledger.beginPass();
        ledger.seen("id-1", "ELYTRA", alice);
        assertTrue(ledger.endPass(0).isEmpty());
        ledger.beginPass();
        ledger.seen("id-1", "ELYTRA", bob);
        assertTrue(ledger.endPass(2_000).isEmpty());
    }

    @Test
    void theSameIdTwiceInOnePassIsReportedOnce() {
        ledger.beginPass();
        ledger.seen("id-1", "ELYTRA", alice);
        ledger.seen("id-1", "ELYTRA", bob);
        List<DuplicateLedger.Duplicate> found = ledger.endPass(0);
        assertEquals(1, found.size());
        assertEquals("Alice", found.getFirst().first().playerName());
        assertEquals("Bob", found.getFirst().second().playerName());

        ledger.beginPass();
        ledger.seen("id-1", "ELYTRA", alice);
        ledger.seen("id-1", "ELYTRA", bob);
        assertTrue(ledger.endPass(2_000).isEmpty());
        ledger.beginPass();
        ledger.seen("id-1", "ELYTRA", alice);
        ledger.seen("id-1", "ELYTRA", bob);
        assertEquals(1, ledger.endPass(DuplicateLedger.REPORT_COOLDOWN_MILLIS + 5_000).size());
    }
}
