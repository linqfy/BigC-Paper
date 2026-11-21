package dev.linqfy.bigCasares.modules.mythicdungeons;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MandingaGuitarPactRuleTest {

    @Test
    void aHealthyPlayerWhoNeverPaidCanTrade() {
        assertEquals(MandingaGuitarPactRule.Outcome.ACCEPT, MandingaGuitarPactRule.decide(false, 20.0));
        assertEquals(MandingaGuitarPactRule.Outcome.ACCEPT, MandingaGuitarPactRule.decide(false, 8.0));
        assertEquals(2.0, MandingaGuitarPactRule.HEART);
    }

    @Test
    void oneGuitarPerPlayerEver() {
        assertEquals(MandingaGuitarPactRule.Outcome.ALREADY_PAID, MandingaGuitarPactRule.decide(true, 20.0));
        assertEquals(MandingaGuitarPactRule.Outcome.ALREADY_PAID, MandingaGuitarPactRule.decide(true, 4.0));
    }

    @Test
    void neverBelowTheFloor() {
        assertEquals(MandingaGuitarPactRule.Outcome.TOO_WEAK, MandingaGuitarPactRule.decide(false, 7.0));
        assertEquals(MandingaGuitarPactRule.Outcome.TOO_WEAK, MandingaGuitarPactRule.decide(false, Double.NaN));
        assertTrue(MandingaGuitarPactRule.MIN_MAX_HEALTH >= 2.0);
    }

    @Test
    void theHeartIsPutBackOnlyWhenTheModifierWasLost() {
        assertTrue(MandingaGuitarPactRule.restore(true, false, 20.0));
        assertFalse(MandingaGuitarPactRule.restore(true, true, 18.0));
        assertFalse(MandingaGuitarPactRule.restore(false, false, 20.0));
        assertFalse(MandingaGuitarPactRule.restore(true, false, 6.0));
    }
}
