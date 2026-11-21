package dev.linqfy.bigCasares.modules.tombstone;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Collection;
import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TombstoneLedgerTest {

    @Test
    void restoreRetainsLiveTombstonesEvenWhenTheirWorldIsNotLoaded() {
        Instant now = Instant.parse("2026-07-18T12:00:00Z");
        TombstoneRecord live = record(now.plusSeconds(300));
        TombstoneLedger ledger = new TombstoneLedger();

        ledger.restore(List.of(live), now);

        assertEquals(List.of(live), ledger.all());
        assertTrue(ledger.find(live.id()).isPresent());
    }

    @Test
    void restoreDropsExpiredTombstonesButKeepsTheirAbsoluteDeadline() {
        Instant now = Instant.parse("2026-07-18T12:00:00Z");
        TombstoneRecord expired = record(now.minusSeconds(1));
        TombstoneRecord live = record(now.plusSeconds(42));
        TombstoneLedger ledger = new TombstoneLedger();

        ledger.restore(List.of(expired, live), now);

        assertFalse(ledger.find(expired.id()).isPresent());
        assertEquals(now.plusSeconds(42), ledger.find(live.id()).orElseThrow().expiresAt());
    }

    @Test
    void visualsAreTransientBecauseTheLedgerOwnsPersistence() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/dev/linqfy/bigCasares/modules/tombstone/TombstoneRuntime.java"));

        assertEquals(3, source.split("setPersistent\\(false\\)", -1).length - 1);
    }

    @Test
    void failedSaveDoesNotLeaveAnUncommittedRecordInTheLedger() {
        TombstoneLedger ledger = new TombstoneLedger();
        TombstoneRecord record = record(Instant.now().plusSeconds(60));
        TombstoneStorage failing = new TombstoneStorage() {
            public List<TombstoneRecord> loadAll() { return List.of(); }
            public void saveAll(Collection<TombstoneRecord> records) { throw new IllegalStateException("disk"); }
        };

        assertThrows(IllegalStateException.class, () -> ledger.putPersisted(record, failing));
        assertTrue(ledger.find(record.id()).isEmpty());
    }

    @Test
    void successfulSaveContainsTheNewRecord() {
        TombstoneLedger ledger = new TombstoneLedger();
        TombstoneRecord record = record(Instant.now().plusSeconds(60));
        List<TombstoneRecord> saved = new ArrayList<>();
        TombstoneStorage storage = new TombstoneStorage() {
            public List<TombstoneRecord> loadAll() { return List.copyOf(saved); }
            public void saveAll(Collection<TombstoneRecord> records) { saved.addAll(records); }
        };

        ledger.putPersisted(record, storage);
        assertEquals(List.of(record), saved);
        assertEquals(List.of(record), ledger.all());
    }

    private static TombstoneRecord record(Instant expiresAt) {
        return new TombstoneRecord(
            UUID.randomUUID(), UUID.randomUUID(), "Jugador", UUID.randomUUID(),
            1.5, 64.0, 2.5, 0.0f, expiresAt,
            List.of(new ItemStack(Material.STONE))
        );
    }
}
