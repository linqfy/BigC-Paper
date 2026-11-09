package dev.linqfy.bigCasares.modules.staffevents;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifestealRulesTest {

    private final LifestealRules rules = new LifestealRules(1, 3, 20, 12);

    @Test
    void aKillMovesOneHeart() {
        assertEquals(new LifestealRules.KillOutcome(1, 1, false), rules.resolveKill(10, 10));
    }

    @Test
    void victimsAtTheFloorAreNotWorthKilling() {
        LifestealRules.KillOutcome outcome = rules.resolveKill(3, 10);
        assertTrue(outcome.floorProtected());
        assertEquals(0, outcome.victimLoss());
        assertEquals(0, outcome.killerGain());
    }

    @Test
    void theFloorLimitsLargerSteals() {
        LifestealRules greedy = new LifestealRules(3, 3, 20, 12);
        assertEquals(new LifestealRules.KillOutcome(1, 1, false), greedy.resolveKill(4, 10));
    }

    @Test
    void theCapStopsTheKillerButStillTakesTheHeart() {
        assertEquals(new LifestealRules.KillOutcome(1, 0, false), rules.resolveKill(10, 20));
    }

    @Test
    void twelveHeartsEarnThePermanentHeart() {
        assertTrue(rules.earnsPermanentHeart(12));
        assertTrue(rules.earnsPermanentHeart(15));
        assertFalse(rules.earnsPermanentHeart(11));
    }

    @Test
    void invalidRulesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new LifestealRules(0, 3, 20, 12));
        assertThrows(IllegalArgumentException.class, () -> new LifestealRules(1, 5, 4, 12));
    }
}
