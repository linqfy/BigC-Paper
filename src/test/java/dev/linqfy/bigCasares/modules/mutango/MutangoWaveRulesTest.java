package dev.linqfy.bigCasares.modules.mutango;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La onda no le pega al que esta minando muy abajo del piso. */
class MutangoWaveRulesTest {

    /** Superficie en Y=65: el piso que usa la onda es el bloque de encima (66). */
    private static final double FLOOR = 66;

    @Test
    void reachesWhoStandsOnTheFloorOrOnAHalfSlab() {
        assertTrue(MutangoWaveRules.reaches(66 - FLOOR));
        assertTrue(MutangoWaveRules.reaches(65.5 - FLOOR));
    }

    @Test
    void doesNotReachWhoIsJumpingAboveIt() {
        assertFalse(MutangoWaveRules.reaches(67 - FLOOR));
    }

    @Test
    void doesNotReachWhoIsMiningFarBelowTheFloor() {
        assertFalse(MutangoWaveRules.reaches(5 - FLOOR), "60 bloques abajo");
        assertFalse(MutangoWaveRules.reaches(64 - FLOOR - 0.1));
    }
}
