package dev.linqfy.bigCasares.modules.caichet;

import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoundGateTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();

    @Test
    void allowCapsTheRatePerPlayerAndKey() {
        SoundGate gate = new SoundGate();
        assertTrue(gate.allow(a, "flicker", 100, 60));
        assertFalse(gate.allow(a, "flicker", 101, 60));
        assertFalse(gate.allow(a, "flicker", 159, 60));
        assertTrue(gate.allow(a, "flicker", 160, 60));
        assertTrue(gate.allow(a, "other", 101, 60), "otra clave tiene su propio tope");
        assertTrue(gate.allow(b, "flicker", 101, 60), "otro jugador tiene su propio tope");
    }

    @Test
    void dueOnlySchedulesTheFirstTimeAndThenFiresWithinTheRange() {
        SoundGate gate = new SoundGate();
        Random random = new Random(7);
        assertFalse(gate.due(a, "chimes", 0, 1200, 2400, random), "la primera vez solo agenda");
        long firedAt = -1;
        for (long now = 1; now <= 2400; now++) {
            if (gate.due(a, "chimes", now, 1200, 2400, random)) {
                firedAt = now;
                break;
            }
        }
        assertTrue(firedAt >= 1200 && firedAt <= 2400, "sono en " + firedAt);
        assertFalse(gate.due(a, "chimes", firedAt + 1, 1200, 2400, random), "despues de sonar vuelve a esperar");
        assertFalse(gate.due(a, "chimes", firedAt + 1199, 1200, 2400, random));
    }

    @Test
    void forgetAndClearDropTheEntries() {
        SoundGate gate = new SoundGate();
        gate.allow(a, "x", 0, 10);
        gate.allow(b, "x", 0, 10);
        assertEquals(2, gate.players());
        gate.forget(a);
        assertEquals(1, gate.players());
        assertTrue(gate.allow(a, "x", 1, 10), "tras olvidarlo, vuelve a sonar");
        gate.clear();
        assertEquals(0, gate.players());
    }

    @Test
    void trainEngineIsReEmittedEveryTwentySeconds() {
        assertTrue(Train.engineDue(1));
        assertFalse(Train.engineDue(2));
        assertFalse(Train.engineDue(200));
        assertTrue(Train.engineDue(1 + 20 * 20));
        assertEquals(20 * 20, Train.ENGINE_PERIOD_TICKS);
    }

    @Test
    void trainSteamOnlyOnItsCadenceAndLowRolls() {
        assertTrue(Train.steamDue(30, 0));
        assertFalse(Train.steamDue(30, 99));
        assertFalse(Train.steamDue(31, 0));
        assertFalse(Train.steamDue(0, 0));
    }
}
