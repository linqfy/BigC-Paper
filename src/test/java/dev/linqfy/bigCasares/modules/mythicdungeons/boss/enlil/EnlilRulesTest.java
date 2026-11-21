package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enlil;

import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnlilRulesTest {

    @Test
    void nothingGetsThroughOutsideAWindowOrFromProjectiles() {
        assertEquals(0.0, EnlilRules.admit(true, false, false, 1000, 1000, 8));
        assertEquals(0.0, EnlilRules.admit(true, true, true, 1000, 1000, 8));
        assertEquals(0.0, EnlilRules.admit(false, true, true, 400, 1000, 8));
        assertEquals(0.0, EnlilRules.admit(false, false, false, 400, 1000, 8));
        assertEquals(0.0, EnlilRules.admit(false, true, false, 400, 1000, Double.NaN));
    }

    @Test
    void meleeInsideAWindowCounts() {
        assertEquals(8.0, EnlilRules.admit(true, true, false, 1000, 1000, 8));
        assertEquals(8.0, EnlilRules.admit(false, true, false, 10, 1000, 8));
    }

    @Test
    void phaseOneCannotSkipTheFlood() {
        assertEquals(4.0, EnlilRules.admit(true, true, false, 504, 1000, 8));
        assertEquals(0.0, EnlilRules.admit(true, true, false, 500, 1000, 8));
        assertTrue(EnlilRules.entersPhaseTwo(500, 1000));
        assertFalse(EnlilRules.entersPhaseTwo(500.5, 1000));
        assertFalse(EnlilRules.entersPhaseTwo(0, 0));
    }

    @Test
    void scalingWithPlayers() {
        assertEquals(3, EnlilTuning.tabletHits(1));
        assertEquals(4, EnlilTuning.tabletHits(2));
        assertEquals(6, EnlilTuning.tabletHits(10));
        assertEquals(3, EnlilTuning.tabletHits(0));
        assertEquals(2, EnlilTuning.tornadoCount(1));
        assertEquals(2, EnlilTuning.tornadoCount(2));
        assertEquals(3, EnlilTuning.tornadoCount(3));
        assertEquals(4, EnlilTuning.tornadoCount(20));
    }

    @Test
    void floodRisesInTwoSlowLayers() {
        assertEquals(0, EnlilTuning.floodLayersAt(0));
        assertEquals(1, EnlilTuning.floodLayersAt(EnlilTuning.FLOOD_LAYER_ONE_AT));
        assertEquals(1, EnlilTuning.floodLayersAt(EnlilTuning.FLOOD_LAYER_TWO_AT - 1));
        assertEquals(2, EnlilTuning.floodLayersAt(100_000));
    }

    @Test
    void onlySurvivalAndAdventurePlayersAreTargeted() {
        assertTrue(EnlilRules.targetable(GameMode.SURVIVAL, false));
        assertTrue(EnlilRules.targetable(GameMode.ADVENTURE, false));
        assertFalse(EnlilRules.targetable(GameMode.CREATIVE, false));
        assertFalse(EnlilRules.targetable(GameMode.SPECTATOR, false));
        assertFalse(EnlilRules.targetable(GameMode.SURVIVAL, true));
    }
}
