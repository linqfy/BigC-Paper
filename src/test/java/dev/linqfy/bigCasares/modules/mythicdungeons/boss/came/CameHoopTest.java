package dev.linqfy.bigCasares.modules.mythicdungeons.boss.came;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameHoopTest {

    /** Aro a 3 bloques del piso, mirando hacia +Z (los jugadores estan en +Z). */
    private final CameHoop hoop = new CameHoop(new CameVec(0.0, 67.0, 0.0), new CameVec(0.0, 0.0, 1.0));

    @Test
    void theOpeningShrinksWithEveryGoal() {
        assertEquals(2.2, CameHoop.opening(0));
        assertEquals(1.6, CameHoop.opening(1));
        assertEquals(1.1, CameHoop.opening(2));
        assertEquals(1.1, CameHoop.opening(7));
        assertTrue(CameHoop.assistDegrees(0) > CameHoop.assistDegrees(1));
        assertTrue(CameHoop.assistDegrees(1) > CameHoop.assistDegrees(2));
    }

    @Test
    void aBallThroughTheCentreIsAGoal() {
        assertTrue(hoop.passes(new CameVec(0.0, 67.0, 0.5), new CameVec(0.0, 67.0, -0.5), 0));
        assertTrue(hoop.passes(new CameVec(0.0, 67.0, 0.5), new CameVec(0.0, 67.0, -0.5), 2));
    }

    @Test
    void theSameShotCanMissOnceTheHoopIsNarrower() {
        CameVec from = new CameVec(2.0, 67.0, 0.5);
        CameVec to = new CameVec(2.0, 67.0, -0.5);
        // a 2 bloques del centro: entra con la abertura de 2.2 pero no con la de 1.1 (+ holgura 0.55)
        assertTrue(hoop.passes(from, to, 0));
        assertTrue(hoop.passes(from, to, 1));
        assertFalse(hoop.passes(from, to, 2));
    }

    @Test
    void onlyCountsFromThePlayersSideAndOnlyWhenCrossing() {
        assertFalse(hoop.passes(new CameVec(0.0, 67.0, -0.5), new CameVec(0.0, 67.0, 0.5), 0), "from behind");
        assertFalse(hoop.passes(new CameVec(0.0, 67.0, 3.0), new CameVec(0.0, 67.0, 2.0), 0), "not yet there");
        assertFalse(hoop.passes(new CameVec(0.0, 71.0, 0.5), new CameVec(0.0, 71.0, -0.5), 0), "over the top");
    }

    @Test
    void aShotRoughlyAtTheHoopIsHelpedIntoTheCentre() {
        CameVec ball = new CameVec(0.0, 66.0, 8.0);
        // apunta 3 bloques a la izquierda del aro: ~20 grados, dentro del cono del principio
        CameVec look = new CameVec(3.0, 1.0, -8.0);
        CameVec aimed = hoop.assist(ball, look, 0);
        CameVec toCentre = hoop.center().subtract(ball);
        assertEquals(0.0, aimed.angleTo(toCentre), 1.0e-6);
        // con el aro chico (15 grados) ese mismo golpe ya no se corrige
        CameVec unaided = hoop.assist(ball, look, 2);
        assertTrue(unaided.angleTo(toCentre) > 15.0);
        // y una pelota golpeada para cualquier lado sigue para ese lado
        assertTrue(hoop.assist(ball, new CameVec(1.0, 0.0, 0.0), 0).angleTo(new CameVec(1.0, 0.0, 0.0)) < 1.0e-6);
    }

    @Test
    void anAssistedShotFliesThroughEvenWhenTheHoopIsAtItsSmallest() {
        CameVec ball = new CameVec(1.0, 66.2, 9.0);
        CameVec look = hoop.center().subtract(ball).add(new CameVec(1.0, 0.4, 0.0));
        CameBall flight = new CameBall(ball);
        flight.launch();
        assertTrue(flight.strike(hoop.assist(ball, look, 2)));
        CameBall.Event event = CameBall.Event.NONE;
        for (int step = 0; step < 40 && event == CameBall.Event.NONE; step++) {
            event = flight.advanceReturn(hoop, 2, (x, y, z) -> false);
        }
        assertEquals(CameBall.Event.GOAL, event);
    }

    @Test
    void theNormalMustBeHorizontal() {
        assertThrows(IllegalArgumentException.class, () -> new CameHoop(CameVec.ZERO, new CameVec(0.0, 1.0, 0.0)));
        assertEquals(1.0, new CameHoop(CameVec.ZERO, new CameVec(3.0, 2.0, 0.0)).normal().length(), 1.0e-9);
    }
}
