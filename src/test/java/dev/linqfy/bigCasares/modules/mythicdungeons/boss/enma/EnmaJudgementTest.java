package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enma;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnmaJudgementTest {

    private static final EnmaJudgement.Situation FAR_FULL = new EnmaJudgement.Situation(3, 3, false, 1.0);

    /** Los ataques que empiezan entre {@code from} y {@code to}, con la misma situacion todo el tiempo. */
    private static List<EnmaJudgement.Attack> run(EnmaJudgement judgement, int from, int to,
                                                  EnmaJudgement.Situation situation) {
        List<EnmaJudgement.Attack> started = new ArrayList<>();
        for (int tick = from; tick < to; tick++) {
            for (EnmaJudgement.Event event : judgement.advance(tick, situation)) {
                if (event.kind() == EnmaJudgement.Kind.WINDUP) {
                    started.add(event.attack());
                }
            }
        }
        return started;
    }

    @Test
    void theJudgementOpensWithASlamThatCallsTheOni() {
        EnmaJudgement judgement = new EnmaJudgement(0, new SplittableRandom(3));
        List<EnmaJudgement.Attack> started = run(judgement, 0, EnmaJudgement.FIRST_ATTACK_DELAY + 1,
            new EnmaJudgement.Situation(0, 1, true, 1.0));
        assertEquals(List.of(EnmaJudgement.Attack.SLAM), started);
    }

    @Test
    void everyAttackIsTelegraphedThenReleasedThenEnds() {
        EnmaJudgement judgement = new EnmaJudgement(0, new SplittableRandom(5));
        int windup = -1;
        int release = -1;
        int end = -1;
        for (int tick = 0; tick < 200 && end < 0; tick++) {
            for (EnmaJudgement.Event event : judgement.advance(tick, FAR_FULL)) {
                switch (event.kind()) {
                    case WINDUP -> windup = tick;
                    case RELEASE -> release = tick;
                    case END -> end = tick;
                }
            }
        }
        assertTrue(windup >= 0 && release > windup && end > release);
        for (EnmaJudgement.Attack attack : EnmaJudgement.Attack.values()) {
            assertTrue(attack.windupTicks() >= 16, attack + " needs at least 0.8 s of warning");
        }
    }

    @Test
    void spinsOnlyWhenSomeoneIsCloseAndRespectsItsCooldown() {
        EnmaJudgement judgement = new EnmaJudgement(0, new SplittableRandom(7));
        EnmaJudgement.Situation close = new EnmaJudgement.Situation(3, 3, true, 1.0);
        List<EnmaJudgement.Attack> started = run(judgement, 0, EnmaJudgement.SPIN_COOLDOWN, close);
        assertEquals(EnmaJudgement.Attack.SPIN, started.get(0));
        assertEquals(1, started.stream().filter(a -> a == EnmaJudgement.Attack.SPIN).count());
        EnmaJudgement far = new EnmaJudgement(0, new SplittableRandom(7));
        assertTrue(run(far, 0, 2000, FAR_FULL).stream().noneMatch(a -> a == EnmaJudgement.Attack.SPIN));
    }

    @Test
    void neverSlamsWithTheOniAtTheirCapAndRespectsTheSlamCooldown() {
        EnmaJudgement judgement = new EnmaJudgement(0, new SplittableRandom(11));
        assertTrue(run(judgement, 0, 2000, FAR_FULL).stream().noneMatch(a -> a == EnmaJudgement.Attack.SLAM));
        EnmaJudgement hungry = new EnmaJudgement(0, new SplittableRandom(11));
        List<EnmaJudgement.Attack> started = run(hungry, 0, EnmaJudgement.SLAM_COOLDOWN,
            new EnmaJudgement.Situation(0, 2, false, 1.0));
        assertEquals(1, started.stream().filter(a -> a == EnmaJudgement.Attack.SLAM).count());
    }

    @Test
    void bulletPatternsNeverRepeatBackToBack() {
        EnmaJudgement judgement = new EnmaJudgement(0, new SplittableRandom(13));
        List<EnmaJudgement.Attack> started = run(judgement, 0, 4000, FAR_FULL);
        assertTrue(started.size() > 20);
        for (int i = 1; i < started.size(); i++) {
            assertNotEquals(started.get(i - 1), started.get(i));
            assertNotNull(started.get(i).pattern());
        }
    }

    @Test
    void frenzyShortensThePause() {
        EnmaJudgement calm = new EnmaJudgement(0, new SplittableRandom(17));
        EnmaJudgement frenzied = new EnmaJudgement(0, new SplittableRandom(17));
        int calmCount = run(calm, 0, 3000, FAR_FULL).size();
        int frenzyCount = run(frenzied, 0, 3000, new EnmaJudgement.Situation(3, 3, false, 0.2)).size();
        assertTrue(frenzyCount > calmCount, calmCount + " vs " + frenzyCount);
    }

    @Test
    void oniCapGrowsWithThePartyUpToThree() {
        assertEquals(1, EnmaJudgement.oniCap(1));
        assertEquals(2, EnmaJudgement.oniCap(2));
        assertEquals(2, EnmaJudgement.oniCap(3));
        assertEquals(3, EnmaJudgement.oniCap(4));
        assertEquals(3, EnmaJudgement.oniCap(10));
    }
}
