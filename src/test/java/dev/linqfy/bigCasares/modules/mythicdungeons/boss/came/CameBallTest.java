package dev.linqfy.bigCasares.modules.mythicdungeons.boss.came;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameBallTest {

    private static final CameBall.Terrain OPEN = (x, y, z) -> false;
    private final CameHoop hoop = new CameHoop(new CameVec(0.0, 67.0, 0.0), new CameVec(0.0, 0.0, 1.0));

    @Test
    void aHeldBallCannotBeReturnedOrMove() {
        CameBall ball = new CameBall(new CameVec(0.0, 68.0, -3.0));
        assertFalse(ball.returnable());
        assertFalse(ball.strike(new CameVec(0.0, 0.0, -1.0)));
        assertEquals(CameBall.Event.NONE, ball.advanceInbound(new CameVec(0.0, 65.0, 10.0), OPEN));
        ball.hold(new CameVec(1.0, 68.0, -3.0));
        assertEquals(new CameVec(1.0, 68.0, -3.0), ball.position());
    }

    @Test
    void itSlowsDownNearThePlayerSoThereIsTimeToHitIt() {
        CameBall ball = new CameBall(new CameVec(0.0, 65.0, 0.0));
        ball.launch();
        CameVec chest = new CameVec(0.0, 65.0, 20.0);
        int ticksInReach = 0;
        CameBall.Event event = CameBall.Event.NONE;
        while (event == CameBall.Event.NONE) {
            event = ball.advanceInbound(chest, OPEN);
            if (ball.position().distance(chest) <= 3.5) {
                ticksInReach++;
            }
        }
        assertEquals(CameBall.Event.IMPACT, event);
        // a menos de 3.5 bloques (alcance de un golpe + radio de la pelota) se queda al menos ~0.4 s
        assertTrue(ticksInReach >= 8, "ticks in reach: " + ticksInReach);
    }

    @Test
    void aWallOrTakingTooLongDropsTheBall() {
        CameBall walled = new CameBall(new CameVec(0.0, 65.0, 0.0));
        walled.launch();
        assertEquals(CameBall.Event.DROPPED, walled.advanceInbound(new CameVec(0.0, 65.0, 20.0), (x, y, z) -> z > 0.3));

        CameBall chased = new CameBall(new CameVec(0.0, 65.0, 0.0));
        chased.launch();
        CameBall.Event event = CameBall.Event.NONE;
        int ticks = 0;
        while (event == CameBall.Event.NONE) {
            ticks++;
            // un jugador que siempre se aleja mas rapido de lo que llega la pelota
            CameVec runner = chased.position().add(new CameVec(0.0, 0.0, 6.0));
            event = chased.advanceInbound(runner, OPEN);
        }
        assertEquals(CameBall.Event.DROPPED, event);
        assertEquals(CameBall.MAX_INBOUND_TICKS + 1, ticks);
    }

    @Test
    void aReturnedBallThatMissesTheHoopIsOut() {
        CameBall ball = new CameBall(new CameVec(0.0, 66.0, 8.0));
        ball.launch();
        assertTrue(ball.strike(new CameVec(1.0, 0.0, 0.0)));
        assertFalse(ball.returnable());
        CameBall.Event event = CameBall.Event.NONE;
        int steps = 0;
        while (event == CameBall.Event.NONE) {
            event = ball.advanceReturn(hoop, 0, OPEN);
            steps++;
        }
        assertEquals(CameBall.Event.MISSED, event);
        assertTrue(steps > 30);
        // la segunda vez que le pegan ya no cambia nada
        assertFalse(ball.strike(new CameVec(0.0, 0.0, -1.0)));
    }

    @Test
    void aReturnedBallStopsAtAWall() {
        CameBall ball = new CameBall(new CameVec(0.0, 66.0, 8.0));
        ball.launch();
        ball.strike(new CameVec(-1.0, 0.0, 0.0));
        assertEquals(CameBall.Event.NONE, ball.advanceReturn(hoop, 0, (x, y, z) -> x < -2.5));
        assertEquals(CameBall.Event.NONE, ball.advanceReturn(hoop, 0, (x, y, z) -> x < -2.5));
        assertEquals(CameBall.Event.MISSED, ball.advanceReturn(hoop, 0, (x, y, z) -> x < -2.5));
    }

    @Test
    void touchingIsMeasuredFromTheCentre() {
        CameBall ball = new CameBall(new CameVec(0.0, 65.0, 0.0));
        assertTrue(ball.touches(new CameVec(0.0, 65.0, 1.1)));
        assertFalse(ball.touches(new CameVec(0.0, 65.0, 1.3)));
    }

    @Test
    void aMissedReturnHurtsHalfTheMaximumHealth() {
        assertEquals(new CameBallImpact(10.0, false), CameBallImpact.against(20.0, 20.0));
        assertEquals(new CameBallImpact(4.0, false), CameBallImpact.against(14.0, 20.0));
        assertTrue(CameBallImpact.against(10.0, 20.0).lethal());
        assertTrue(CameBallImpact.against(6.0, 20.0).lethal());
        assertEquals(new CameBallImpact(20.0, false), CameBallImpact.against(40.0, 40.0));
    }
}
