package dev.linqfy.bigCasares.modules.mythicdungeons.boss.odin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OdinArenaTest {

    private final OdinArena arena = OdinArena.around(100.5, 64.0, -20.5);

    @Test
    void playersOnTheRoomFloorAreTargets() {
        assertTrue(arena.contains(100.5, 64.0, -20.5));
        assertTrue(arena.contains(100.5 + 16.0, 64.0, -20.5));
        assertTrue(arena.contains(100.5 + 11.0, 65.0, -20.5 + 11.0)); // esquina de una sala de 33x33
        assertTrue(arena.contains(100.5, 64.0 + 9.0, -20.5)); // saltando o sobre algo alto
    }

    @Test
    void theAudienceBeyondTheRoomIsNot() {
        // la audiencia del jefe llega a 48 bloques: un pasillo a 25 bloques detras de la pared no es la sala
        assertFalse(arena.contains(100.5 + 25.0, 64.0, -20.5));
        assertFalse(arena.contains(100.5 + 13.0, 64.0, -20.5 + 13.0));
        // un piso de abajo o de arriba tampoco
        assertFalse(arena.contains(100.5, 64.0 - 6.0, -20.5));
        assertFalse(arena.contains(100.5, 64.0 + 12.0, -20.5));
    }

    @Test
    void rejectsNonsenseSizes() {
        assertThrows(IllegalArgumentException.class, () -> new OdinArena(0, 0, 0, 0.0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new OdinArena(0, 0, 0, 5.0, -1, 1));
    }
}
