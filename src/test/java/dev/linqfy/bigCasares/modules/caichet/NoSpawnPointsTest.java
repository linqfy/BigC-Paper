package dev.linqfy.bigCasares.modules.caichet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** En las dimensiones de cai chet no se marca la reaparicion durmiendo ni con anclas; los comandos si. */
class NoSpawnPointsTest {

    @Test
    void onlyBedsAndAnchorsAreRefused() {
        assertTrue(NoSpawnPoints.isPlayerSpawn("BED"));
        assertTrue(NoSpawnPoints.isPlayerSpawn("RESPAWN_ANCHOR"));
        assertFalse(NoSpawnPoints.isPlayerSpawn("COMMAND"));
        assertFalse(NoSpawnPoints.isPlayerSpawn("PLUGIN"));
    }

    @Test
    void theMessageIsTheOneAskedFor() {
        assertEquals("No, no estoy cansado", NoSpawnPoints.MESSAGE);
        assertFalse(NoSpawnPoints.isCaiChetWorld(null));
    }
}
