package dev.linqfy.bigCasares.modules.mythicdungeons.boss.pachamama;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PachamamaRulesTest {

    @Test
    void phaseTwoStartsAtHalfHealth() {
        assertFalse(PachamamaRules.entersPhaseTwo(701.0, 1400.0));
        assertTrue(PachamamaRules.entersPhaseTwo(700.0, 1400.0));
        assertFalse(PachamamaRules.entersPhaseTwo(0.0, 0.0));
    }

    @Test
    void phaseOneHitsStopAtTheThresholdSoMandingaAlwaysComesOut() {
        assertEquals(10.0, PachamamaRules.admit(true, true, false, 1400.0, 1400.0, 10.0));
        assertEquals(5.0, PachamamaRules.admit(true, true, false, 705.0, 1400.0, 10.0));
        assertEquals(0.0, PachamamaRules.admit(true, true, false, 700.0, 1400.0, 10.0));
        assertEquals(10.0, PachamamaRules.admit(false, true, false, 300.0, 1400.0, 10.0));
    }

    @Test
    void guitarNotesHurtALot() {
        assertEquals(12.0 * PachamamaTuning.NOTE_MULTIPLIER, PachamamaRules.admit(false, true, true, 1000.0, 1400.0,
            12.0));
        assertTrue(PachamamaTuning.NOTE_MULTIPLIER >= 2.0);
    }

    @Test
    void nothingGetsThroughWhileSheRisesOrChangesPhase() {
        assertEquals(0.0, PachamamaRules.admit(true, false, true, 1400.0, 1400.0, 50.0));
        assertEquals(0.0, PachamamaRules.admit(false, true, false, 1400.0, 1400.0, Double.NaN));
        assertEquals(0.0, PachamamaRules.admit(false, true, false, 1400.0, 1400.0, -3.0));
    }

    @Test
    void standingOnTheFloorMeansFeetNearTheWalkingLevel() {
        assertTrue(PachamamaRules.onFloor(64.0, 64));
        assertTrue(PachamamaRules.onFloor(65.0, 64));
        assertFalse(PachamamaRules.onFloor(65.4, 64));
        assertFalse(PachamamaRules.onFloor(62.9, 64));
    }

    @Test
    void theDealNeedsThreeSecondsOfSneakAndRestartsWhenInterrupted() {
        PachamamaDealHold hold = new PachamamaDealHold();
        UUID player = UUID.randomUUID();
        for (int tick = 1; tick < PachamamaTuning.DEAL_SNEAK_TICKS; tick++) {
            assertNull(hold.tick(Set.of(player)));
        }
        assertEquals(PachamamaTuning.DEAL_SNEAK_TICKS - 1, hold.progress(player));
        assertNull(hold.tick(Set.of()));
        assertEquals(0, hold.progress(player));
        for (int tick = 1; tick < PachamamaTuning.DEAL_SNEAK_TICKS; tick++) {
            hold.tick(Set.of(player));
        }
        assertEquals(player, hold.tick(Set.of(player)));
        assertEquals(0, hold.progress(player));
        assertEquals(60, PachamamaTuning.DEAL_SNEAK_TICKS);
    }
}
