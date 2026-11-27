package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CycadRulesTest {
    @Test
    void aCycadIsTheTipOnlyWithNothingAbove() {
        assertTrue(CycadRules.tip(false));
        assertFalse(CycadRules.tip(true));
    }

    @Test
    void itStandsOnDirtOrOnAnotherCycad() {
        assertTrue(CycadRules.mayStandOn(true, false));
        assertTrue(CycadRules.mayStandOn(false, true));
        assertFalse(CycadRules.mayStandOn(false, false));
    }

    @Test
    void boneMealGrowsOnlyATipUnderFourBlocks() {
        assertTrue(CycadRules.canGrow(true, 1));
        assertTrue(CycadRules.canGrow(true, 3));
        assertFalse(CycadRules.canGrow(true, 4));
        assertFalse(CycadRules.canGrow(false, 1), "only the tip grows");
    }

    @Test
    void boneMealWorksHalfOfTheTime() {
        assertTrue(CycadRules.boneMealWorks(true));
        assertFalse(CycadRules.boneMealWorks(false));
    }

    @Test
    void generatedHeightIsCeilOfTwoAndAHalfRolls() {
        assertEquals(0, CycadRules.generatedHeight(0.0F));
        assertEquals(1, CycadRules.generatedHeight(0.2F));
        assertEquals(1, CycadRules.generatedHeight(0.4F));
        assertEquals(2, CycadRules.generatedHeight(0.41F));
        assertEquals(2, CycadRules.generatedHeight(0.8F));
        assertEquals(3, CycadRules.generatedHeight(0.81F));
        assertEquals(3, CycadRules.generatedHeight(0.9999F));
    }
}
