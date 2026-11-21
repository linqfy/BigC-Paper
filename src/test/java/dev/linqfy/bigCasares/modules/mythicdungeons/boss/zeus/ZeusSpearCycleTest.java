package dev.linqfy.bigCasares.modules.mythicdungeons.boss.zeus;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZeusSpearCycleTest {

    @Test
    void eventsComeInOrderAndTheWindowIsOnlyWhileTheSpearIsStuck() {
        ZeusSpearCycle cycle = new ZeusSpearCycle(100);
        int first = 100 + ZeusSpearCycle.FIRST_DELAY;
        List<ZeusSpearCycle.Event> events = new ArrayList<>();
        List<Integer> at = new ArrayList<>();
        for (int tick = 101; tick <= first + 2 * ZeusSpearCycle.cycleLength(); tick++) {
            for (ZeusSpearCycle.Event event : cycle.advance(tick)) {
                events.add(event);
                at.add(tick);
            }
        }
        assertEquals(List.of(
            ZeusSpearCycle.Event.WINDUP, ZeusSpearCycle.Event.THROW, ZeusSpearCycle.Event.IMPACT,
            ZeusSpearCycle.Event.WINDOW_CLOSE,
            ZeusSpearCycle.Event.WINDUP, ZeusSpearCycle.Event.THROW, ZeusSpearCycle.Event.IMPACT,
            ZeusSpearCycle.Event.WINDOW_CLOSE,
            ZeusSpearCycle.Event.WINDUP), events);
        assertEquals(first, at.get(0));
        assertEquals(first + ZeusSpearCycle.IMPACT_AT, at.get(2));

        assertFalse(cycle.vulnerable(first));
        assertFalse(cycle.vulnerable(first + ZeusSpearCycle.IMPACT_AT - 1));
        assertTrue(cycle.vulnerable(first + ZeusSpearCycle.IMPACT_AT));
        assertTrue(cycle.vulnerable(first + ZeusSpearCycle.IMPACT_AT + ZeusSpearCycle.WINDOW - 1));
        assertFalse(cycle.vulnerable(first + ZeusSpearCycle.IMPACT_AT + ZeusSpearCycle.WINDOW));
        assertEquals(ZeusSpearCycle.WINDOW, cycle.windowRemaining(first + ZeusSpearCycle.IMPACT_AT));
        assertEquals(0, cycle.windowRemaining(first));
    }

    @Test
    void impactIsTelegraphedLongEnoughAndTheWindowLastsSeconds() {
        assertTrue(ZeusSpearCycle.IMPACT_AT >= 16, "impact marked at least 0.8 s ahead");
        assertTrue(ZeusSpearCycle.WINDOW >= 60, "window of a few seconds");
        assertTrue(ZeusSpearCycle.cycleLength() <= 200, "a spear every few seconds");
    }

    @Test
    void flightGoesFromHandToGround() {
        ZeusSpearCycle cycle = new ZeusSpearCycle(0);
        int first = ZeusSpearCycle.FIRST_DELAY;
        assertTrue(cycle.windingUp(first));
        assertEquals(0.0, cycle.flight(first + ZeusSpearCycle.THROW_AT - 1));
        assertEquals(0.0, cycle.flight(first + ZeusSpearCycle.THROW_AT));
        assertEquals(1.0, cycle.flight(first + ZeusSpearCycle.IMPACT_AT));
        double middle = cycle.flight(first + (ZeusSpearCycle.THROW_AT + ZeusSpearCycle.IMPACT_AT) / 2);
        assertTrue(middle > 0.0 && middle < 1.0);
        assertTrue(cycle.advance(0).isEmpty());
    }
}
