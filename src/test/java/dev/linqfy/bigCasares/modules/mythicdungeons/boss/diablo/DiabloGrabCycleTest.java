package dev.linqfy.bigCasares.modules.mythicdungeons.boss.diablo;

import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiabloGrabCycleTest {

    private static final DiabloGrabCycle.Settings SETTINGS = new DiabloGrabCycle.Settings(10, 5, 100, 20, 8);

    @Test
    void enrageReachGrabThenStunWhenNobodyIsHeld() {
        DiabloGrabCycle cycle = new DiabloGrabCycle(SETTINGS);
        assertEquals(DiabloGrabCycle.Event.NONE, cycle.tick(0, true), "idle before the phase starts");
        cycle.start(0);
        assertEquals(DiabloGrabCycle.Event.NONE, cycle.tick(9, true));
        assertEquals(DiabloGrabCycle.Event.REACH, cycle.tick(10, true));
        assertEquals(DiabloGrabCycle.Event.NONE, cycle.tick(14, true));
        assertEquals(DiabloGrabCycle.Event.GRAB, cycle.tick(15, true));
        assertEquals(1, cycle.grabs());
        assertFalse(cycle.vulnerable());
        assertEquals(DiabloGrabCycle.Event.NONE, cycle.tick(16, false), "someone is still held");
        assertEquals(DiabloGrabCycle.Event.STUN, cycle.tick(40, true));
        assertTrue(cycle.vulnerable());
        assertEquals(20, cycle.stunTicksLeft(40));
        assertEquals(DiabloGrabCycle.Event.NONE, cycle.tick(59, true));
        assertEquals(DiabloGrabCycle.Event.STAND_UP, cycle.tick(60, true));
        assertFalse(cycle.vulnerable());
        assertEquals(DiabloGrabCycle.Event.REACH, cycle.tick(68, true), "repeats until he dies");
        assertEquals(DiabloGrabCycle.Event.GRAB, cycle.tick(73, false));
        assertEquals(2, cycle.grabs());
    }

    @Test
    void holdingTooLongSlamsAndStunsAnyway() {
        DiabloGrabCycle cycle = new DiabloGrabCycle(SETTINGS);
        cycle.start(0);
        cycle.tick(10, false);
        cycle.tick(15, false);
        assertEquals(DiabloGrabCycle.Event.NONE, cycle.tick(114, false));
        assertEquals(DiabloGrabCycle.Event.SLAM, cycle.tick(115, false));
        assertEquals(DiabloGrabCycle.Stage.STUNNED, cycle.stage());
        assertTrue(cycle.vulnerable(), "exhausted after the slam: the damage window always comes");
        assertEquals(DiabloGrabCycle.Event.STAND_UP, cycle.tick(135, false));
        assertEquals(DiabloGrabCycle.Event.REACH, cycle.tick(143, false));
    }

    @Test
    void somebodyAlwaysHeldStillOpensDamageWindowsWithRealTimings() {
        // los tiempos de DiabloController: un jugador AFK nunca se suelta
        DiabloGrabCycle cycle = new DiabloGrabCycle(new DiabloGrabCycle.Settings(60, 20, 600, 160, 60));
        cycle.start(0);
        long firstWindow = -1;
        int windows = 0;
        boolean wasVulnerable = false;
        for (long tick = 1; tick <= 20 * 60 * 10; tick++) {
            cycle.tick(tick, false);
            if (cycle.vulnerable() && !wasVulnerable) {
                windows++;
                if (firstWindow < 0) {
                    firstWindow = tick;
                }
            }
            wasVulnerable = cycle.vulnerable();
        }
        assertTrue(firstWindow > 0 && firstWindow <= 60 + 20 + 600, "first window by the hold cap: " + firstWindow);
        assertTrue(windows >= 10, "one window per grab in 10 minutes: " + windows);
    }

    @Test
    void completingTheQteStillStunsEarlier() {
        DiabloGrabCycle cycle = new DiabloGrabCycle(SETTINGS);
        cycle.start(0);
        cycle.tick(10, false);
        cycle.tick(15, false);
        assertEquals(DiabloGrabCycle.Event.STUN, cycle.tick(30, true));
        assertTrue(cycle.vulnerable());
    }

    @Test
    void onlyLivingSurvivalOrAdventurePlayersFight() {
        assertTrue(DiabloGrabCycle.fights(GameMode.SURVIVAL, false));
        assertTrue(DiabloGrabCycle.fights(GameMode.ADVENTURE, false));
        assertFalse(DiabloGrabCycle.fights(GameMode.CREATIVE, false));
        assertFalse(DiabloGrabCycle.fights(GameMode.SPECTATOR, false));
        assertFalse(DiabloGrabCycle.fights(GameMode.SURVIVAL, true));
    }

    @Test
    void rejectsInvalidTimings() {
        assertThrows(IllegalArgumentException.class, () -> new DiabloGrabCycle.Settings(0, 0, 10, 10, 0));
        assertThrows(IllegalArgumentException.class, () -> new DiabloGrabCycle.Settings(0, 1, 10, 0, 0));
    }
}
