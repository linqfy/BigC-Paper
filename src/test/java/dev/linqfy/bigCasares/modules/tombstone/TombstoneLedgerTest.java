package dev.linqfy.bigCasares.modules.tombstone;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import dev.linqfy.bigCasares.testsupport.CompiledClass;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        // The runtime needs a live server, so its entity setup is read from the bytecode.
        CompiledClass runtime = CompiledClass.of(getClass(), "dev.linqfy.bigCasares.modules.tombstone.TombstoneRuntime");

        assertTrue(runtime.calls().stream().anyMatch(call -> call.name().equals("setPersistent")));
    }

    private static TombstoneRecord record(Instant expiresAt) {
        return new TombstoneRecord(
            UUID.randomUUID(), UUID.randomUUID(), "Jugador", UUID.randomUUID(),
            1.5, 64.0, 2.5, 0.0f, expiresAt,
            List.of(new ItemStack(Material.STONE))
        );
    }
}
