package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enma;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnmaMirrorTrialTest {

    /** Corre hasta el primer tick con {@code wanted}, con {@code alive} reflejos vivos; devuelve ese tick. */
    private static int runUntil(EnmaMirrorTrial trial, int from, int alive, EnmaMirrorTrial.Event wanted) {
        for (int tick = from; tick < from + 5000; tick++) {
            if (trial.advance(tick, alive).contains(wanted)) {
                return tick;
            }
        }
        throw new AssertionError("never reached " + wanted);
    }

    @Test
    void telegraphsTheFirstWaveAndThenSummonsIt() {
        EnmaMirrorTrial trial = new EnmaMirrorTrial(0, 60.0);
        int telegraph = runUntil(trial, 1, 0, EnmaMirrorTrial.Event.TELEGRAPH);
        assertEquals(EnmaMirrorTrial.INTRO_TICKS, telegraph);
        assertEquals(EnmaMirrorTrial.State.SUMMONING, trial.state());
        int summon = runUntil(trial, telegraph + 1, 0, EnmaMirrorTrial.Event.SUMMON);
        assertEquals(telegraph + EnmaMirrorTrial.SUMMON_TELEGRAPH_TICKS, summon);
        assertTrue(EnmaMirrorTrial.SUMMON_TELEGRAPH_TICKS >= 16, "at least 0.8 s of warning");
        assertEquals(1, trial.waves());
    }

    @Test
    void theMirrorIsShieldedWhileAnyReflectionLives() {
        EnmaMirrorTrial trial = new EnmaMirrorTrial(0, 60.0);
        int summon = runUntil(trial, 1, 0, EnmaMirrorTrial.Event.SUMMON);
        for (int tick = summon + 1; tick < summon + 400; tick++) {
            assertFalse(trial.advance(tick, 2).contains(EnmaMirrorTrial.Event.EXPOSED));
        }
        EnmaMirrorTrial.Hit hit = trial.hit(10.0);
        assertTrue(hit.shielded());
        assertEquals(0.0, hit.accepted());
        assertEquals(1.0, trial.integrity());
    }

    @Test
    void killingEveryReflectionExposesTheMirrorForAWindowThenANewWaveComes() {
        EnmaMirrorTrial trial = new EnmaMirrorTrial(0, 60.0);
        int summon = runUntil(trial, 1, 0, EnmaMirrorTrial.Event.SUMMON);
        // el mismo tick en que nacen todavia no se cuentan
        assertFalse(trial.advance(summon, 0).contains(EnmaMirrorTrial.Event.EXPOSED));
        assertTrue(trial.advance(summon + 1, 0).contains(EnmaMirrorTrial.Event.EXPOSED));
        assertTrue(trial.exposed());
        assertEquals(EnmaMirrorTrial.EXPOSED_TICKS, trial.exposedRemaining(summon + 1));
        int again = runUntil(trial, summon + 2, 0, EnmaMirrorTrial.Event.TELEGRAPH);
        assertEquals(summon + 1 + EnmaMirrorTrial.EXPOSED_TICKS, again);
        runUntil(trial, again + 1, 0, EnmaMirrorTrial.Event.SUMMON);
        assertEquals(2, trial.waves());
    }

    @Test
    void hitsWhileExposedWearItDownAndTheLastOneBreaksIt() {
        EnmaMirrorTrial trial = new EnmaMirrorTrial(0, 60.0);
        int summon = runUntil(trial, 1, 0, EnmaMirrorTrial.Event.SUMMON);
        trial.advance(summon + 1, 0);
        EnmaMirrorTrial.Hit first = trial.hit(25.0);
        assertEquals(25.0, first.accepted());
        assertFalse(first.broke());
        assertEquals(1, trial.crackStage());
        trial.hit(25.0);
        assertEquals(2, trial.crackStage());
        EnmaMirrorTrial.Hit last = trial.hit(25.0);
        assertEquals(10.0, last.accepted(), 1.0e-9);
        assertTrue(last.broke());
        assertTrue(trial.broken());
        assertEquals(0.0, trial.integrity());
        assertTrue(trial.advance(summon + 500, 0).isEmpty());
        assertEquals(0.0, trial.hit(5.0).accepted());
    }

    @Test
    void theMirrorTransfersFifteenPercentOfEnmaAcrossItsWholeDurability() {
        EnmaMirrorTrial trial = new EnmaMirrorTrial(0, 90.0);
        assertEquals(0.15 * 2000.0, trial.transfer(90.0, 2000.0), 1.0e-9);
        assertEquals(0.05 * 2000.0, trial.transfer(30.0, 2000.0), 1.0e-9);
    }

    @Test
    void durabilityScalesWithPlayers() {
        assertEquals(60.0, EnmaMirrorTrial.durabilityFor(0));
        assertEquals(60.0, EnmaMirrorTrial.durabilityFor(1));
        assertEquals(150.0, EnmaMirrorTrial.durabilityFor(4));
        assertEquals(150.0, EnmaMirrorTrial.durabilityFor(9));
        assertThrows(IllegalArgumentException.class, () -> new EnmaMirrorTrial(0, 0.0));
    }

    @Test
    void enmaFiresATelegraphedVolleyEverySoOften() {
        EnmaMirrorTrial trial = new EnmaMirrorTrial(0, 60.0);
        List<Integer> windups = new ArrayList<>();
        List<Integer> fires = new ArrayList<>();
        for (int tick = 1; tick < 800; tick++) {
            List<EnmaMirrorTrial.Event> events = trial.advance(tick, 1);
            if (events.contains(EnmaMirrorTrial.Event.VOLLEY_WINDUP)) {
                windups.add(tick);
            }
            if (events.contains(EnmaMirrorTrial.Event.VOLLEY_FIRE)) {
                fires.add(tick);
            }
        }
        assertTrue(windups.size() >= 4, windups.toString());
        assertTrue(windups.get(0) > EnmaMirrorTrial.INTRO_TICKS);
        for (int i = 0; i < fires.size(); i++) {
            assertEquals(windups.get(i) + EnmaMirrorTrial.VOLLEY_WINDUP_TICKS, fires.get(i));
        }
        assertTrue(EnmaMirrorTrial.VOLLEY_WINDUP_TICKS >= 16, "at least 0.8 s of warning");
    }

    @Test
    void aLostMirrorCountsAsBrokenSoEnmaIsNotImmuneForever() {
        EnmaMirrorTrial trial = new EnmaMirrorTrial(0, 60.0);
        runUntil(trial, 1, 0, EnmaMirrorTrial.Event.EXPOSED);
        trial.hit(15.0);
        assertEquals(45.0, trial.forfeit(), 1.0e-9);
        assertTrue(trial.broken());
        assertEquals(0.0, trial.forfeit());
        assertEquals(0.0, trial.hit(10.0).accepted());
    }
}
