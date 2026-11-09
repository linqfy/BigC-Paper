package dev.linqfy.bigCasares.modules.customcrossbow;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SniperAimServiceTest {

    private final UUID player = UUID.randomUUID();
    private final UUID weapon = UUID.randomUUID();
    private final SniperAimService service = new SniperAimService();

    @Test
    void holdingUseDoesNotSpendHeartsAndReleasingConsumesTheCharge() {
        service.begin(player, weapon, 100);
        assertEquals(6, service.hearts(player, weapon, 280, 20));
        assertEquals(6, service.hearts(player, weapon, 280, 20));
        assertEquals(6, service.fire(player, weapon, 280, 20));
        assertEquals(0, service.fire(player, weapon, 280, 20));
        assertEquals(0, service.hearts(player, weapon, 900, 20));
    }

    @Test
    void earlyReleaseDiscardsChargeAndIdleTimeCannotChargeAnotherShot() {
        service.begin(player, weapon, 100);
        assertEquals(0, service.fire(player, weapon, 129, 20));
        assertEquals(0, service.fire(player, weapon, 900, 20));
        service.begin(player, weapon, 900);
        assertEquals(0, service.hearts(player, weapon, 929, 20));
        assertEquals(1, service.fire(player, weapon, 930, 20));
        assertEquals(0, service.hearts(player, weapon, 1800, 18));
    }

    @Test
    void duplicateUsePacketsDoNotRestartAnExistingLoad() {
        service.begin(player, weapon, 100);
        service.begin(player, weapon, 120);
        assertEquals(1, service.fire(player, weapon, 130, 20));
    }

    @Test
    void damageTakenWhileAimingReducesTheAffordableShot() {
        service.begin(player, weapon, 100);
        assertEquals(6, service.hearts(player, weapon, 280, 20));
        assertEquals(2, service.fire(player, weapon, 280, 6));
    }

    @Test
    void changingCrossbowsCannotTransferAChargedShot() {
        service.begin(player, weapon, 100);
        assertEquals(0, service.fire(player, UUID.randomUUID(), 280, 20));
        assertEquals(0, service.fire(player, weapon, 280, 20));
    }

    @Test
    void cancellationAndShutdownDiscardChargeWithoutFiring() {
        service.begin(player, weapon, 100);
        service.cancel(player);
        assertEquals(0, service.fire(player, weapon, 280, 20));
        service.begin(player, weapon, 100);
        service.clear();
        assertEquals(0, service.fire(player, weapon, 280, 20));
    }
}
