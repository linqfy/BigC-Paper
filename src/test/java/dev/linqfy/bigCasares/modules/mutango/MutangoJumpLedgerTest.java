package dev.linqfy.bigCasares.modules.mutango;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El doble salto solo se recarga al aterrizar: nada de renovarlo sacandose las botas o desconectandose en el aire. */
class MutangoJumpLedgerTest {

    private final UUID player = UUID.randomUUID();

    @Test
    void takingTheBootsOffAndOnAgainInTheAirDoesNotRenewTheJump() {
        MutangoJumpLedger ledger = new MutangoJumpLedger();
        assertTrue(ledger.of(player, false).doubleJump());       // lo usa en el aire
        ledger.tick(player, false, false);                       // se saca las botas: sigue en el aire
        assertFalse(ledger.of(player, true).doubleJump());       // se las vuelve a poner: sigue gastado
        assertFalse(ledger.of(player, true).doubleJump());
    }

    @Test
    void leavingAndComingBackInTheAirKeepsTheJumpSpent() {
        MutangoJumpLedger ledger = new MutangoJumpLedger();
        assertTrue(ledger.of(player, false).doubleJump());
        ledger.forget(player);                                   // se desconecta
        assertFalse(ledger.of(player, true).doubleJump());       // vuelve: el jugador guardo que lo gasto
    }

    @Test
    void aServerReloadInTheAirKeepsItToo() {
        MutangoJumpLedger afterReload = new MutangoJumpLedger();
        assertFalse(afterReload.of(player, true).doubleJump());
    }

    @Test
    void landingRechargesItAndSaysTheStoredMarkCanBeCleared() {
        MutangoJumpLedger ledger = new MutangoJumpLedger();
        assertTrue(ledger.of(player, false).doubleJump());
        assertFalse(ledger.tick(player, true, false), "en el aire no se limpia nada");
        assertTrue(ledger.tick(player, true, true), "aterrizo");
        assertTrue(ledger.of(player, false).doubleJump());
    }

    @Test
    void landingWithoutBootsForgetsTheStateSoTheNextPairStartsFresh() {
        MutangoJumpLedger ledger = new MutangoJumpLedger();
        assertTrue(ledger.of(player, false).doubleJump());
        assertTrue(ledger.tick(player, false, true));
        assertTrue(ledger.of(player, false).doubleJump());
    }

    @Test
    void theReportedSequenceUsedToGiveTrueFalseTrueAndNowGivesTrueFalseFalse() {
        MutangoJumpLedger ledger = new MutangoJumpLedger();
        boolean first = ledger.of(player, false).doubleJump();
        ledger.tick(player, false, false);
        boolean second = ledger.of(player, true).doubleJump();
        ledger.forget(player);
        boolean third = ledger.of(player, true).doubleJump();
        assertTrue(first);
        assertFalse(second);
        assertFalse(third);
    }
}
