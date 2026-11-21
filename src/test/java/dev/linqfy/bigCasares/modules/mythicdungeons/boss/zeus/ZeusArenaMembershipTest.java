package dev.linqfy.bigCasares.modules.mythicdungeons.boss.zeus;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZeusArenaMembershipTest {

    /** Sala de 33x33 (x, z de 1 a 33, piso en y = 64) con la grilla centrada en el altar (17.5, 20.5). */
    private static ZeusArenaMembership arena() {
        ZeusGrid grid = new ZeusGrid(17.5, 20.5, 3, 6);
        Map<ZeusCell, Integer> floors = new HashMap<>();
        for (ZeusCell cell : grid.cells()) {
            double x = grid.centerX(cell);
            double z = grid.centerZ(cell);
            if (x >= 1 && x < 34 && z >= 1 && z < 34) {
                floors.put(cell, 64);
            }
        }
        return new ZeusArenaMembership(grid, floors);
    }

    @Test
    void playersOnTheArenaFloorCount() {
        ZeusArenaMembership arena = arena();
        assertTrue(arena.contains(17.5, 65.0, 20.5));
        assertTrue(arena.contains(2.5, 65.0, 2.5));
        assertTrue(arena.contains(30.2, 67.5, 30.1), "jumping still counts");
    }

    @Test
    void playersInCorridorsBehindTheWallsOrOnOtherFloorsDoNot() {
        ZeusArenaMembership arena = arena();
        // pasillo detras de la pared sur (z = 34 es la pared): afuera de toda baldosa con piso
        assertFalse(arena.contains(17.5, 65.0, 38.5));
        // pasillo al oeste, mas alla de la grilla
        assertFalse(arena.contains(-6.5, 65.0, 20.5));
        // otro nivel de la mazmorra, arriba o abajo de la sala
        assertFalse(arena.contains(17.5, 80.0, 20.5));
        assertFalse(arena.contains(17.5, 55.0, 20.5));
        // a 48 bloques (audiencia del runtime) pero lejos de la sala
        assertFalse(arena.contains(60.5, 65.0, 20.5));
    }
}
