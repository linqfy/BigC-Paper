package dev.linqfy.bigCasares.modules.legendary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegendaryCraftingEventTest {
    @TempDir Path directory;
    private static final long START = 1_700_000_000_000L;
    private static final UUID WORLD = UUID.randomUUID();
    private LegendaryCraftingLedger ledger() throws Exception {
        return new LegendaryCraftingLedger(new LegendaryStateStore(directory.resolve("state.properties")), new Random(123));
    }
    private static LegendaryCraftingSession.Pending tx(UUID player, LegendaryCraftingSession.TransactionKind kind) {
        return new LegendaryCraftingSession.Pending(player, UUID.randomUUID(), "serialized-after-inventory", kind);
    }
    private static LegendaryCraftingSession start(LegendaryCraftingLedger ledger, LegendaryWeapon weapon, int x) throws Exception {
        var tx = tx(UUID.randomUUID(), LegendaryCraftingSession.TransactionKind.START);
        var session = ledger.start(weapon, WORLD, "Overworld (world)", x, 84, -623, START, tx);
        ledger.confirm(weapon, tx.nonce());
        return session;
    }

    @Test void nestedInclusiveSquaresContainNegativePositiveAndBoundaryCoordinatesWithoutFixedCenter() {
        var random = new Random(987);
        var centers = new java.util.HashSet<Integer>();
        for (int i = 0; i < 10_000; i++) {
            int x = i % 2 == 0 ? -30_000_000 + i : 30_000_000 - i;
            int z = -i;
            var zones = LegendarySearchAreas.create(x, z, random);
            assertTrue(zones.outer().contains(x, z)); assertTrue(zones.inner().contains(x, z));
            assertTrue(zones.outer().contains(zones.inner()));
            assertEquals(1000, zones.outer().maxX() - zones.outer().minX() + 1);
            assertEquals(500, zones.inner().maxZ() - zones.inner().minZ() + 1);
            centers.add(x - zones.outer().minX());
        }
        assertTrue(centers.size() > 900);
    }
    @Test void revealsOnlyAtFiveTenFifteenAndTwentyMinutesUsingRealDeadlines() throws Exception {
        var ledger = ledger(); start(ledger, LegendaryWeapon.THUNDER_SPEAR, 1427);
        assertEquals(List.of(0), ledger.advance(START).stream().map(LegendaryCraftingLedger.Notice::index).toList());
        assertTrue(ledger.advance(START + 299_999).isEmpty());
        assertEquals(1, ledger.advance(START + 300_000).getFirst().index());
        assertTrue(ledger.advance(START + 599_999).isEmpty());
        assertEquals(2, ledger.advance(START + 600_000).getFirst().index());
        assertTrue(ledger.advance(START + 899_999).isEmpty());
        assertEquals(3, ledger.advance(START + 900_000).getFirst().index());
        assertTrue(ledger.advance(START + 1_199_999).isEmpty());
        assertEquals(LegendaryCraftingSession.Stage.CRAFTING, ledger.find(LegendaryWeapon.THUNDER_SPEAR).orElseThrow().stage());
        assertEquals(4, ledger.advance(START + 1_200_000).getFirst().index());
        assertEquals(LegendaryCraftingSession.Stage.READY_TO_CLAIM, ledger.find(LegendaryWeapon.THUNDER_SPEAR).orElseThrow().stage());
    }
    @Test void restartPreservesLocationZonesAndClockAndDoesNotRepeatBroadcasts() throws Exception {
        var ledger = ledger(); var original = start(ledger, LegendaryWeapon.BLOODLUST, -1427);
        ledger.advance(START + 600_000);
        var restart = ledger(); var restored = restart.find(LegendaryWeapon.BLOODLUST).orElseThrow();
        assertEquals(original.areas(), restored.areas()); assertEquals(original.deadline(), restored.deadline());
        assertEquals(-1427, restored.x()); assertEquals(-623, restored.z());
        assertTrue(restart.advance(START + 600_000).isEmpty());
        assertEquals(List.of(3, 4), restart.advance(START + 1_900_000).stream().map(LegendaryCraftingLedger.Notice::index).toList());
        assertTrue(ledger().advance(START + 1_900_001).isEmpty());
    }
    @Test void noClaimBeforeDeadlineAndAnyDifferentPlayerCanClaimAfterwards() throws Exception {
        var ledger = ledger(); var creator = UUID.randomUUID();
        var start = tx(creator, LegendaryCraftingSession.TransactionKind.START);
        ledger.start(LegendaryWeapon.THUNDER_SPEAR, WORLD, "Overworld (world)", 12, 80, 30, START, start);
        ledger.confirm(LegendaryWeapon.THUNDER_SPEAR, start.nonce());
        var claimant = tx(UUID.randomUUID(), LegendaryCraftingSession.TransactionKind.CLAIM);
        assertThrows(IllegalArgumentException.class, () -> ledger.claim(LegendaryWeapon.THUNDER_SPEAR, claimant));
        ledger.advance(START + 1_200_000);
        var result = ledger.claim(LegendaryWeapon.THUNDER_SPEAR, claimant);
        assertEquals(LegendaryCraftingSession.Stage.CLAIMED, result.stage());
        assertNotEquals(creator, result.transactions().getFirst().player());
        ledger.confirm(LegendaryWeapon.THUNDER_SPEAR, claimant.nonce());
        assertTrue(ledger.find(LegendaryWeapon.THUNDER_SPEAR).orElseThrow().transactions().isEmpty());
    }
    @Test void oneConcurrentClaimWinsAndItsRecoveryDoesNotCreateAnotherReservation() throws Exception {
        var ledger = ledger(); start(ledger, LegendaryWeapon.SHADOW_BLADE, 100);
        ledger.advance(START + 1_200_000);
        try (var threads = Executors.newFixedThreadPool(8)) {
            Callable<Boolean> attempt = () -> {
                try { ledger.claim(LegendaryWeapon.SHADOW_BLADE, tx(UUID.randomUUID(), LegendaryCraftingSession.TransactionKind.CLAIM)); return true; }
                catch (IllegalArgumentException rejected) { return false; }
            };
            var results = threads.invokeAll(java.util.Collections.nCopies(16, attempt));
            int successes = 0;
            for (var result : results) if (result.get()) successes++;
            assertEquals(1, successes);
        }
        var restarted = ledger();
        var pending = restarted.find(LegendaryWeapon.SHADOW_BLADE).orElseThrow().transactions().getFirst();
        assertTrue(restarted.advance(START + 1_300_000).isEmpty());
        restarted.confirm(LegendaryWeapon.SHADOW_BLADE, pending.nonce());
        assertEquals(List.of(5), restarted.advance(START + 1_300_000).stream().map(LegendaryCraftingLedger.Notice::index).toList());
        assertTrue(ledger().advance(START + 1_300_001).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ledger().start(LegendaryWeapon.SHADOW_BLADE, WORLD, "Overworld (world)", 0, 0, 0, START, tx(UUID.randomUUID(), LegendaryCraftingSession.TransactionKind.START)));
    }
    @Test void creatorDisconnectOrUnresolvedConsumptionDoesNotPauseEventOrReservePrizeOwnership() throws Exception {
        var ledger = ledger(); var creator = tx(UUID.randomUUID(), LegendaryCraftingSession.TransactionKind.START);
        ledger.start(LegendaryWeapon.WINDWEAVER, WORLD, "Overworld (world)", 1, 2, 3, START, creator);
        var restarted = ledger(); restarted.advance(START + 1_200_000);
        var winner = tx(UUID.randomUUID(), LegendaryCraftingSession.TransactionKind.CLAIM);
        assertEquals(2, restarted.claim(LegendaryWeapon.WINDWEAVER, winner).transactions().size());
        restarted.confirm(LegendaryWeapon.WINDWEAVER, winner.nonce());
        assertEquals(List.of(5), restarted.advance(START + 1_200_001).stream().map(LegendaryCraftingLedger.Notice::index).toList());
        assertTrue(restarted.pending(creator.player()));
        assertFalse(restarted.protectedCell(WORLD, 1, 2, 3));
        restarted.confirm(LegendaryWeapon.WINDWEAVER, creator.nonce());
        assertFalse(restarted.pending(creator.player()));
    }
    @Test void differentWeaponsHaveIndependentEventsButOneTableCannotHostTwo() throws Exception {
        var ledger = ledger(); start(ledger, LegendaryWeapon.SHADOW_BLADE, 11);
        assertThrows(IllegalArgumentException.class, () -> start(ledger, LegendaryWeapon.BLOODLUST, 11));
        start(ledger, LegendaryWeapon.BLOODLUST, 12);
        assertEquals(2, ledger.all().size());
        assertThrows(IllegalArgumentException.class, () -> start(ledger, LegendaryWeapon.BLOODLUST, 13));
    }
    @Test void protectsExactlyTableAndDisplayCellInItsWorldUntilClaimed() throws Exception {
        var ledger = ledger(); start(ledger, LegendaryWeapon.EARTHBREAKER, -12);
        assertTrue(ledger.protectedCell(WORLD, -12, 84, -623));
        assertTrue(ledger.protectedCell(WORLD, -12, 85, -623));
        assertFalse(ledger.protectedCell(WORLD, -12, 86, -623));
        assertFalse(ledger.protectedCell(WORLD, -11, 84, -623));
        assertFalse(ledger.protectedCell(UUID.randomUUID(), -12, 84, -623));
        ledger.advance(START + 1_200_000);
        var winner = tx(UUID.randomUUID(), LegendaryCraftingSession.TransactionKind.CLAIM);
        ledger.claim(LegendaryWeapon.EARTHBREAKER, winner);
        assertTrue(ledger.protectedCell(WORLD, -12, 84, -623));
        ledger.confirm(LegendaryWeapon.EARTHBREAKER, winner.nonce());
        assertFalse(ledger.protectedCell(WORLD, -12, 84, -623));
    }
    @Test void publicNoticesNeverContainPlayerIdentityOrExactLocationBeforeFifteenMinutes() throws Exception {
        var ledger = ledger(); var player = UUID.randomUUID();
        var transaction = tx(player, LegendaryCraftingSession.TransactionKind.START);
        ledger.start(LegendaryWeapon.THUNDER_SPEAR, WORLD, "Overworld (world)", 1427, 84, -623, START, transaction);
        ledger.confirm(LegendaryWeapon.THUNDER_SPEAR, transaction.nonce());
        for (var notice : ledger.advance(START + 1_200_000)) {
            String text = String.join("\n", LegendaryCraftingMessages.notice(notice, "Overworld"));
            assertFalse(text.contains(player.toString()));
            if (notice.index() < 3) { assertFalse(text.contains("Y:")); assertFalse(text.contains("X: 1427 |")); }
        }
        var winner = tx(UUID.randomUUID(), LegendaryCraftingSession.TransactionKind.CLAIM);
        ledger.claim(LegendaryWeapon.THUNDER_SPEAR, winner); ledger.confirm(LegendaryWeapon.THUNDER_SPEAR, winner.nonce());
        var notice = ledger.advance(START + 1_200_001).getFirst();
        assertEquals(List.of("§6⚔ ¡THUNDER SPEAR HA SIDO RECLAMADA!"), LegendaryCraftingMessages.notice(notice, "Overworld"));
        assertFalse(ledger.find(LegendaryWeapon.THUNDER_SPEAR).orElseThrow().encode().contains(winner.player().toString()));
    }
    @Test void failedPersistenceDoesNotCreateAnInMemoryReservation() throws Exception {
        var file = directory.resolve("blocked"); java.nio.file.Files.createDirectory(file);
        var store = new LegendaryStateStore(directory.resolve("blocked/state.properties"));
        java.nio.file.Files.delete(file); java.nio.file.Files.writeString(file, "not-a-directory");
        var ledger = new LegendaryCraftingLedger(store, new Random(1));
        assertThrows(java.io.IOException.class, () -> start(ledger, LegendaryWeapon.BLOODLUST, 5));
        assertTrue(ledger.all().isEmpty());
    }
}
