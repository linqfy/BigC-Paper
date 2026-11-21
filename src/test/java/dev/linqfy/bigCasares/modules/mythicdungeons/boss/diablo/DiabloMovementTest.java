package dev.linqfy.bigCasares.modules.mythicdungeons.boss.diablo;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La orbita de la fase 1, la botella que siempre pega y el cambio de fase. */
class DiabloMovementTest {

    private static final DiabloOrbit.Settings ORBIT = new DiabloOrbit.Settings(9.0, 3.5, 40, 30, 110, 16, 0.42, 0.5);

    @Test
    void retreatsApproachesCirclesAndSwipesInOrder() {
        DiabloOrbit orbit = new DiabloOrbit(ORBIT);
        DiabloOrbit.Point target = new DiabloOrbit.Point(0.0, 0.0);
        DiabloOrbit.Point boss = new DiabloOrbit.Point(2.0, 0.0);
        List<DiabloOrbit.Leg> starts = new ArrayList<>();
        int strikes = 0;
        double minimumDuringCircle = Double.MAX_VALUE;
        double maximumDuringCircle = 0.0;
        for (long tick = 0; tick < 196; tick++) {
            DiabloOrbit.Move move = orbit.next(tick, boss, target);
            if (move.legStart()) {
                starts.add(move.leg());
            }
            if (move.leg() == DiabloOrbit.Leg.RETREAT) {
                assertTrue(move.to().distance(boss) <= 0.42 + 1.0e-9, "speed limit");
            }
            if (move.leg() == DiabloOrbit.Leg.CIRCLE && tick > 75) {
                minimumDuringCircle = Math.min(minimumDuringCircle, move.to().distance(target));
                maximumDuringCircle = Math.max(maximumDuringCircle, move.to().distance(target));
            }
            if (move.strike()) {
                strikes++;
                assertEquals(DiabloOrbit.Leg.SWIPE, move.leg());
            }
            if (tick == 39) {
                assertEquals(9.0, move.to().distance(target), 1.0e-6, "retreated to the far distance");
            }
            if (tick == 69) {
                assertEquals(3.5, move.to().distance(target), 1.0e-6, "came back close");
            }
            boss = move.to();
        }
        assertEquals(List.of(DiabloOrbit.Leg.RETREAT, DiabloOrbit.Leg.APPROACH, DiabloOrbit.Leg.CIRCLE,
            DiabloOrbit.Leg.SWIPE), starts);
        assertEquals(1, strikes);
        assertEquals(3.5, minimumDuringCircle, 0.05);
        assertEquals(3.5, maximumDuringCircle, 0.05);
        assertEquals(DiabloOrbit.Leg.RETREAT, orbit.next(196, boss, target).leg(), "the pattern repeats");
    }

    @Test
    void circlesAroundAMovingTargetAndSwitchesDirectionWhenBlocked() {
        DiabloOrbit orbit = new DiabloOrbit(ORBIT);
        DiabloOrbit.Point boss = new DiabloOrbit.Point(3.5, 0.0);
        DiabloOrbit.Point target = new DiabloOrbit.Point(0.0, 0.0);
        for (long tick = 0; tick < 71; tick++) {
            boss = orbit.next(tick, boss, target).to();
        }
        assertEquals(DiabloOrbit.Leg.CIRCLE, orbit.leg());
        int before = orbit.direction();
        orbit.blocked();
        assertEquals(-before, orbit.direction());
        DiabloOrbit.Point moved = new DiabloOrbit.Point(5.0, 5.0);
        for (long tick = 71; tick < 140; tick++) {
            boss = orbit.next(tick, boss, moved).to();
        }
        assertEquals(3.5, boss.distance(moved), 0.3, "follows the player while circling");
    }

    @Test
    void theBottleAlwaysHitsEvenARunningPlayer() {
        HomingBottle bottle = new HomingBottle(new HomingBottle.Vec(0.0, 3.0, 0.0), 0.45, 0.04, 0.6, 80);
        double playerX = 4.0;
        boolean hit = false;
        int ticks = 0;
        while (!hit && ticks < 200) {
            playerX += 0.28; // corriendo en linea recta
            hit = bottle.advance(new HomingBottle.Vec(playerX, 1.3, 0.0)).hit();
            ticks++;
        }
        assertTrue(hit);
        assertTrue(ticks < 40, "catches a sprinting player quickly, took " + ticks);
        assertTrue(bottle.done());
        assertFalse(bottle.advance(new HomingBottle.Vec(0.0, 0.0, 0.0)).hit(), "hits only once");
    }

    @Test
    void theBottleHitsAtTheTickCapWhateverHappens() {
        HomingBottle bottle = new HomingBottle(new HomingBottle.Vec(0.0, 0.0, 0.0), 0.01, 0.0, 0.1, 5);
        for (int tick = 1; tick < 5; tick++) {
            assertFalse(bottle.advance(new HomingBottle.Vec(1000.0, 0.0, 0.0)).hit());
        }
        HomingBottle.Step last = bottle.advance(new HomingBottle.Vec(1000.0, 0.0, 0.0));
        assertTrue(last.hit());
        assertEquals(1000.0, last.position().x());
    }

    @Test
    void phaseTwoStartsOnceAtTheThreshold() {
        DiabloPhases phases = new DiabloPhases(0.5);
        assertFalse(phases.update(0.8));
        assertEquals(1, phases.phase());
        assertTrue(phases.update(0.5));
        assertEquals(2, phases.phase());
        assertFalse(phases.update(0.2));
        assertFalse(phases.update(1.0), "healing does not go back");
        assertEquals(2, phases.phase());
        assertThrows(IllegalArgumentException.class, () -> new DiabloPhases(1.0));
    }

    @Test
    void theOutlineBlinks() {
        int lit = 0;
        for (long tick = 0; tick < 120; tick++) {
            if (DiabloPhases.outlineLit(tick, 7, 5)) {
                lit++;
            }
        }
        assertEquals(70, lit);
        assertTrue(DiabloPhases.outlineLit(0, 7, 5));
        assertFalse(DiabloPhases.outlineLit(7, 7, 5));
        assertTrue(DiabloPhases.outlineLit(12, 7, 5));
    }
}
