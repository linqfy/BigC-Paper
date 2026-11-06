package dev.linqfy.bigCasares.modules.mutango;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La cuenta del rayo se cancela si el jugador se fue de la pelea. */
class MutangoZoneRulesTest {

    @Test
    void sigueEnLaPeleaEnElMismoMundoYCerca() {
        assertTrue(MutangoZoneRules.inFight(true, 10 * 10));
        assertTrue(MutangoZoneRules.inFight(true, MutangoZoneRules.FIGHT_RADIUS * MutangoZoneRules.FIGHT_RADIUS));
    }

    @Test
    void unTeleportALejosOAOtraDimensionLaCancela() {
        assertFalse(MutangoZoneRules.inFight(true, 5000.0 * 5000.0), "a otro claim lejos");
        assertFalse(MutangoZoneRules.inFight(false, 0), "a otro mundo, aunque las coordenadas coincidan");
    }
}
