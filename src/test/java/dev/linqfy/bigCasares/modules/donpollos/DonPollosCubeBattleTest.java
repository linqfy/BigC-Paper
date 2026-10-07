package dev.linqfy.bigCasares.modules.donpollos;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DonPollosCubeBattleTest {

    /** Simula la pelea: cada bicho que sale se muere {@code lifeTicks} despues. */
    private static int[] simulate(DonPollosCubeBattle battle, long lifeTicks, int maxTicks, List<Integer> roundsSeen,
                                  int[] maxAliveSeen) {
        java.util.List<Long> labubus = new java.util.ArrayList<>();
        java.util.List<Long> colonels = new java.util.ArrayList<>();
        int spawnedLabubus = 0;
        int spawnedColonels = 0;
        for (long now = 0; now < maxTicks; now++) {
            final long t = now;
            labubus.removeIf(death -> death <= t);
            colonels.removeIf(death -> death <= t);
            DonPollosCubeBattle.Step step = battle.tick(now, labubus.size(), colonels.size());
            if (step.roundStarted() > 0) {
                roundsSeen.add(step.roundStarted());
            }
            if (step.spawnColonel()) {
                colonels.add(now + lifeTicks);
                spawnedColonels++;
            }
            if (step.spawnLabubu()) {
                labubus.add(now + lifeTicks);
                spawnedLabubus++;
            }
            maxAliveSeen[0] = Math.max(maxAliveSeen[0], labubus.size());
            if (step.finished()) {
                return new int[]{spawnedLabubus, spawnedColonels, (int) now};
            }
        }
        return new int[]{spawnedLabubus, spawnedColonels, -1};
    }

    @Test
    void defaultBattleIsThreeRoundsOfTenThirtyAndFiftyLabubusWithOneColonelEach() {
        DonPollosBossPhase3Settings phase3 = DonPollosBossPhase3Settings.defaults();
        assertEquals(List.of(10, 30, 50), phase3.rounds().stream().map(DonPollosCubeRound::labubus).toList());
        DonPollosCubeBattle battle = new DonPollosCubeBattle(phase3.rounds(), phase3.maxAlive(), 40, 0);
        List<Integer> rounds = new java.util.ArrayList<>();
        int[] maxAlive = {0};
        int[] result = simulate(battle, 30, 20 * 60 * 10, rounds, maxAlive);
        assertEquals(90, result[0]);
        assertEquals(3, result[1]);
        assertTrue(result[2] > 0, "la batalla tiene que terminar");
        assertEquals(List.of(1, 2, 3), rounds);
        assertTrue(battle.finished());
        assertTrue(maxAlive[0] <= phase3.maxAlive());
    }

    @Test
    void labubusComeFasterAndTheColonelGetsFasterAndStrongerEachRound() {
        List<DonPollosCubeRound> rounds = DonPollosBossPhase3Settings.defaults().rounds();
        for (int i = 1; i < rounds.size(); i++) {
            assertTrue(rounds.get(i).spawnSeconds() < rounds.get(i - 1).spawnSeconds());
            assertTrue(rounds.get(i).colonelSpeed() > rounds.get(i - 1).colonelSpeed());
            assertTrue(rounds.get(i).colonelDamage() > rounds.get(i - 1).colonelDamage());
        }
    }

    @Test
    void roundOnlyEndsWhenEverythingIsDead() {
        DonPollosCubeBattle battle = new DonPollosCubeBattle(List.of(new DonPollosCubeRound(2, 0.05, 1, 0)), 10, 0, 0);
        DonPollosCubeBattle.Step first = battle.tick(0, 0, 0);
        assertEquals(1, first.roundStarted());
        assertTrue(first.spawnColonel() && first.spawnLabubu());
        assertTrue(battle.tick(1, 1, 1).spawnLabubu());
        assertFalse(battle.tick(2, 2, 1).finished());
        assertFalse(battle.tick(3, 0, 1).finished(), "el Coronel sigue vivo");
        assertEquals(1.0 / 3, battle.remaining(0, 1), 1e-9);
        assertTrue(battle.tick(4, 0, 0).finished());
        assertEquals(0, battle.remaining(0, 0));
    }

    @Test
    void neverPassesTheAliveCap() {
        DonPollosCubeBattle battle = new DonPollosCubeBattle(List.of(new DonPollosCubeRound(50, 0.05, 1, 0)), 5, 0, 0);
        int alive = 0;
        for (long now = 0; now < 100; now++) {
            if (battle.tick(now, alive, 1).spawnLabubu()) {
                alive++;
            }
        }
        assertEquals(5, alive);
    }

    @Test
    void rejectsSillyRounds() {
        assertThrows(IllegalArgumentException.class, () -> new DonPollosCubeRound(-1, 1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new DonPollosCubeBattle(List.of(), 10, 0, 0));
    }
}
