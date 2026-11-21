package dev.linqfy.bigCasares.modules.mythicdungeons;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AltarRuleTest {

    // sala del jefe generada en (100, 20, 200): 35x35, piso en y=20, altar en el local (17, 1, 20)
    private static final int MIN_X = 100;
    private static final int MIN_Y = 20;
    private static final int MIN_Z = 200;
    private static final int MAX_X = MIN_X + 34;
    private static final int MAX_Z = MIN_Z + 34;

    @Test
    void theGeneratedAltarIsAccepted() {
        assertTrue(AltarRule.isAltarSpot(MIN_X, MIN_Y, MIN_Z, MAX_X, MAX_Z, MIN_X + 17, MIN_Y + 1, MIN_Z + 20));
    }

    @Test
    void theAltarIsAcceptedForEveryRotationOfTheRoom() {
        // el local (17, 20) rotado cae en (17, 14), (20, 17) o (14, 17)
        assertTrue(AltarRule.isAltarSpot(MIN_X, MIN_Y, MIN_Z, MAX_X, MAX_Z, MIN_X + 17, MIN_Y + 1, MIN_Z + 14));
        assertTrue(AltarRule.isAltarSpot(MIN_X, MIN_Y, MIN_Z, MAX_X, MAX_Z, MIN_X + 20, MIN_Y + 1, MIN_Z + 17));
        assertTrue(AltarRule.isAltarSpot(MIN_X, MIN_Y, MIN_Z, MAX_X, MAX_Z, MIN_X + 14, MIN_Y + 1, MIN_Z + 17));
    }

    @Test
    void aBlockPlacedAwayFromTheCentreOrOffTheFloorIsNotTheAltar() {
        assertFalse(AltarRule.isAltarSpot(MIN_X, MIN_Y, MIN_Z, MAX_X, MAX_Z, MIN_X + 3, MIN_Y + 1, MIN_Z + 3));
        assertFalse(AltarRule.isAltarSpot(MIN_X, MIN_Y, MIN_Z, MAX_X, MAX_Z, MIN_X + 17, MIN_Y + 4, MIN_Z + 20));
        assertFalse(AltarRule.isAltarSpot(MIN_X, MIN_Y, MIN_Z, MAX_X, MAX_Z, MIN_X + 17, MIN_Y, MIN_Z + 20));
    }

    @Test
    void piecesThatAreNotTheBossRoomNeverHoldAnAltar() {
        // una sala comun de 15x15 y un pasillo de 9x11
        assertFalse(AltarRule.isArena(0, 0, 14, 14));
        assertFalse(AltarRule.isAltarSpot(0, 20, 0, 14, 14, 7, 21, 7));
        assertFalse(AltarRule.isAltarSpot(0, 20, 0, 8, 10, 4, 21, 5));
        assertTrue(AltarRule.isArena(MIN_X, MIN_Z, MAX_X, MAX_Z));
    }
}
