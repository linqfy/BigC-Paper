package dev.linqfy.bigCasares.modules.alexscaves.core.ability;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CooldownLedgerTest {

    @Test
    void cooldownBlocksUntilItsTickArrives() {
        AtomicLong tick = new AtomicLong(100);
        CooldownLedger ledger = new CooldownLedger(tick::get);
        UUID player = UUID.randomUUID();
        assertTrue(ledger.tryUse(player, "gauntlet", 5));
        assertFalse(ledger.tryUse(player, "gauntlet", 5));
        assertEquals(5, ledger.remainingTicks(player, "gauntlet"));
        tick.set(104);
        assertFalse(ledger.isReady(player, "gauntlet"));
        tick.set(105);
        assertTrue(ledger.isReady(player, "gauntlet"));
        assertTrue(ledger.tryUse(player, "gauntlet", 5));
    }

    @Test
    void abilitiesAndOwnersAreIndependentAndClearable() {
        AtomicLong tick = new AtomicLong();
        CooldownLedger ledger = new CooldownLedger(tick::get);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ledger.tryUse(first, "a", 100);
        assertTrue(ledger.tryUse(first, "b", 100));
        assertTrue(ledger.tryUse(second, "a", 100));
        ledger.clear(first);
        assertTrue(ledger.isReady(first, "a"));
        assertFalse(ledger.isReady(second, "a"));
        assertEquals(1, ledger.size());
    }
}
