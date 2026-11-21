package dev.linqfy.bigCasares.modules.mythicdungeons.boss.came;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameBallGameTest {

    @Test
    void waitsForSomeoneInTheCourtBeforeTheFirstThrow() {
        CameBallGame game = new CameBallGame(0L);
        assertEquals(Optional.empty(), game.update(CameBallGame.INTRO_TICKS, false));
        assertEquals(Optional.of(CameBallGame.Stage.WINDUP), game.update(CameBallGame.INTRO_TICKS + 5, true));
        assertEquals(CameLord.HUN, game.thrower());
    }

    @Test
    void theThrowIsTelegraphedLongEnough() {
        assertTrue(CameBallGame.WINDUP_TICKS >= 16, "at least 0.8 s of warning");
        CameBallGame game = windup(0L);
        long start = CameBallGame.INTRO_TICKS;
        assertEquals(Optional.empty(), game.update(start + CameBallGame.WINDUP_TICKS - 1, true));
        assertEquals(Optional.of(CameBallGame.Stage.FLIGHT), game.update(start + CameBallGame.WINDUP_TICKS, true));
    }

    @Test
    void theLordsTakeTurnsThrowing() {
        CameBallGame game = windup(0L);
        long now = CameBallGame.INTRO_TICKS + CameBallGame.WINDUP_TICKS;
        game.update(now, true);
        assertEquals(CameBallGame.Stage.COOLDOWN, game.resolve(now + 20, CameBall.Event.IMPACT));
        assertEquals(Optional.empty(), game.update(now + 20 + CameBallGame.COOLDOWN_TICKS - 1, true));
        assertEquals(Optional.of(CameBallGame.Stage.WINDUP), game.update(now + 20 + CameBallGame.COOLDOWN_TICKS, true));
        assertEquals(CameLord.VUCUB, game.thrower());
    }

    @Test
    void threeGoalsEndThePhaseAndMissesDoNotCount() {
        CameBallGame game = new CameBallGame(0L);
        long now = 0L;
        int goals = 0;
        CameBall.Event[] outcomes = {CameBall.Event.GOAL, CameBall.Event.MISSED, CameBall.Event.DROPPED,
            CameBall.Event.GOAL, CameBall.Event.IMPACT, CameBall.Event.GOAL};
        CameBallGame.Stage last = null;
        for (CameBall.Event outcome : outcomes) {
            now += 200;
            assertEquals(Optional.of(CameBallGame.Stage.WINDUP), game.update(now, true));
            now += CameBallGame.WINDUP_TICKS;
            assertEquals(Optional.of(CameBallGame.Stage.FLIGHT), game.update(now, true));
            last = game.resolve(now + 10, outcome);
            if (outcome == CameBall.Event.GOAL) {
                goals++;
            }
            assertEquals(goals, game.goals());
        }
        assertEquals(CameBallGame.Stage.DONE, last);
        assertEquals(Optional.empty(), game.update(now + 1000, true));
        // resolver otra vez no cambia nada
        assertEquals(CameBallGame.Stage.DONE, game.resolve(now + 1001, CameBall.Event.GOAL));
        assertEquals(3, game.goals());
    }

    @Test
    void aCancelledThrowGoesBackToRest() {
        CameBallGame game = windup(0L);
        assertEquals(CameBallGame.Stage.COOLDOWN, game.resolve(CameBallGame.INTRO_TICKS + 3, CameBall.Event.DROPPED));
    }

    private static CameBallGame windup(long start) {
        CameBallGame game = new CameBallGame(start);
        game.update(start + CameBallGame.INTRO_TICKS, true);
        return game;
    }
}
