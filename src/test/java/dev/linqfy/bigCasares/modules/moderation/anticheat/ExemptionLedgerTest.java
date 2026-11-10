package dev.linqfy.bigCasares.modules.moderation.anticheat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ExemptionLedgerTest {

    @Test
    void keepsTheLongestGrantAndExpiresByTick() {
        ExemptionLedger ledger = new ExemptionLedger();
        UUID player = UUID.randomUUID();
        ledger.grant(player, 100, "velocity");
        ledger.grant(player, 50, "damage");

        assertEquals("velocity", ledger.active(player, 80).orElseThrow());
        assertTrue(ledger.isExempt(player, 100));
        assertFalse(ledger.isExempt(player, 101));
    }

    @Test
    void forgettingRemovesTheGrant() {
        ExemptionLedger ledger = new ExemptionLedger();
        UUID player = UUID.randomUUID();
        ledger.grant(player, 100, "teleport");
        ledger.forget(player);
        assertFalse(ledger.isExempt(player, 0));
    }
}
