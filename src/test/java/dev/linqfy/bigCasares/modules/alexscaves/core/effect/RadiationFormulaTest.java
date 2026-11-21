package dev.linqfy.bigCasares.modules.alexscaves.core.effect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadiationFormulaTest {

    @Test
    void eachHazmatPieceRemovesAQuarterOfTheDamage() {
        assertEquals(1.0, RadiationFormula.damage(0));
        assertEquals(0.75, RadiationFormula.damage(1));
        assertEquals(0.5, RadiationFormula.damage(2));
        assertEquals(0.25, RadiationFormula.damage(3));
        assertEquals(0.0, RadiationFormula.damage(4));
        assertEquals(0.0, RadiationFormula.damage(9), "pieces are clamped to a full set");
        assertEquals(1.0, RadiationFormula.damage(-2));
    }

    @Test
    void damageChanceIsDamagePlusTenPercent() {
        assertEquals(1.0, RadiationFormula.damageChance(0));
        assertEquals(0.85, RadiationFormula.damageChance(1), 1e-9);
        assertEquals(0.1, RadiationFormula.damageChance(4), 1e-9);
    }

    @Test
    void amplifierZeroNeverProcs() {
        for (int tick = 0; tick < 1000; tick++) {
            assertFalse(RadiationFormula.procsOnTick(tick, 0));
        }
    }

    @Test
    void procIntervalIsTwoHundredOverAmplifier() {
        assertEquals(5, procsIn(1000, 1), "every 200 ticks");
        assertEquals(10, procsIn(1000, 2), "every 100 ticks");
        assertEquals(15, procsIn(990, 3), "every 66 ticks");
        assertEquals(20, procsIn(1000, 4), "every 50 ticks");
        assertTrue(RadiationFormula.procsOnTick(100, 1));
        assertFalse(RadiationFormula.procsOnTick(0, 1));
        assertEquals(1000, procsIn(1000, 250), "very high amplifiers proc every tick");
    }

    @Test
    void onlyUnprotectedPlayersGetExhausted() {
        assertTrue(RadiationFormula.causesExhaustion(0));
        assertFalse(RadiationFormula.causesExhaustion(1));
    }

    @Test
    void carriedItemChanceScalesWithStackAndHazmat() {
        assertEquals(0.064, RadiationFormula.carriedItemChance(64, 0.001, 0), 1e-12);
        assertEquals(0.032, RadiationFormula.carriedItemChance(64, 0.001, 2), 1e-12);
        assertEquals(0.0, RadiationFormula.carriedItemChance(64, 0.001, 4), 1e-12);
        assertEquals(1.0, RadiationFormula.carriedItemChance(1000, 0.01, 0), "chance is clamped");
    }

    private static int procsIn(int ticks, int amplifier) {
        int procs = 0;
        for (int tick = 0; tick < ticks; tick++) {
            if (RadiationFormula.procsOnTick(tick, amplifier)) {
                procs++;
            }
        }
        return procs;
    }
}
