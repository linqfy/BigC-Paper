package dev.linqfy.bigCasares.modules.villagermood;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerMafiaSpawnTest {

    @Test
    void spawnsOutsideTheTradingCellOnSupportedGround() {
        var result = VillagerMafiaSpawn.find(new FlatGround(4), 0, 4, 0, false, 0);
        assertTrue(result.isPresent());
        var at = result.orElseThrow();
        assertEquals(4, at.y());
        assertTrue(at.x() * at.x() + at.z() * at.z() >= 100);
    }

    @Test
    void mountedCrewsNeedSpaceForTheHorseAndRider() {
        VillagerMafiaSpawn.Terrain lowCeiling = new FlatGround(0) {
            @Override
            public boolean clear(int x, int y, int z) {
                return y >= 0 && y < 2;
            }
        };
        assertTrue(VillagerMafiaSpawn.find(lowCeiling, 0, 0, 0, false, 0).isPresent());
        assertFalse(VillagerMafiaSpawn.find(lowCeiling, 0, 0, 0, true, 0).isPresent());
        assertTrue(VillagerMafiaSpawn.find(new FlatGround(0), 0, 0, 0, true, 0).isPresent());
    }

    @Test
    void refusesUnloadedUnsafeOrUnsupportedTerrain() {
        VillagerMafiaSpawn.Terrain unavailable = new VillagerMafiaSpawn.Terrain() {
            @Override
            public boolean clear(int x, int y, int z) {
                return false;
            }

            @Override
            public boolean supported(int x, int y, int z) {
                return false;
            }
        };
        assertTrue(VillagerMafiaSpawn.find(unavailable, 0, 0, 0, true, 0).isEmpty());
        VillagerMafiaSpawn.Terrain voidWorld = new FlatGround(0) {
            @Override
            public boolean supported(int x, int y, int z) {
                return false;
            }
        };
        assertTrue(VillagerMafiaSpawn.find(voidWorld, 0, 0, 0, false, 0).isEmpty());
    }

    @Test
    void searchesNearbyHeightsAndSpreadsCrewMembers() {
        var first = VillagerMafiaSpawn.find(new FlatGround(2), 0, 0, 0, true, 0).orElseThrow();
        var second = VillagerMafiaSpawn.find(new FlatGround(2), 0, 0, 0, true, 1).orElseThrow();
        assertEquals(2, first.y());
        assertFalse(first.equals(second));
    }

    private static class FlatGround implements VillagerMafiaSpawn.Terrain {
        private final int floor;

        FlatGround(int floor) {
            this.floor = floor;
        }

        @Override
        public boolean clear(int x, int y, int z) {
            return y >= floor;
        }

        @Override
        public boolean supported(int x, int y, int z) {
            return y == floor - 1;
        }
    }
}
