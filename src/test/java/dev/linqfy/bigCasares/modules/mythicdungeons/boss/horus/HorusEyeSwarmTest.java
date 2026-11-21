package dev.linqfy.bigCasares.modules.mythicdungeons.boss.horus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HorusEyeSwarmTest {

    @Test
    void twentyEyesComeOutInWavesOfFour() {
        HorusEyeSwarm swarm = new HorusEyeSwarm(HorusEyeSwarm.DEFAULT_TOTAL);
        assertEquals(20, swarm.total());
        int waves = 0;
        while (!swarm.allSpawned()) {
            int wave = swarm.nextWave();
            assertTrue(wave > 0 && wave <= HorusEyeSwarm.WAVE_SIZE);
            for (int i = 0; i < wave; i++) {
                swarm.spawned();
            }
            waves++;
        }
        assertEquals(5, waves);
        assertEquals(0, swarm.nextWave());
        assertThrows(IllegalStateException.class, swarm::spawned);
    }

    @Test
    void killingEveryEyeSpendsHorusHealthButLeavesTheLastPointForTheDust() {
        HorusEyeSwarm swarm = new HorusEyeSwarm(20);
        for (int i = 0; i < 20; i++) {
            swarm.spawned();
        }
        double health = 500.0;
        double total = 0.0;
        for (int i = 0; i < 20; i++) {
            assertFalse(swarm.cleared());
            double credit = swarm.killedOne(health);
            assertTrue(credit > 0.0);
            health -= credit;
            total += credit;
        }
        assertTrue(swarm.cleared());
        assertEquals(HorusEyeSwarm.RESERVED_HEALTH, health, 1.0E-9);
        assertEquals(500.0 - HorusEyeSwarm.RESERVED_HEALTH, total, 1.0E-9);
        assertEquals(0, swarm.remaining());
    }

    @Test
    void lostEyesGoBackToTheQueue() {
        HorusEyeSwarm swarm = new HorusEyeSwarm(4);
        for (int i = 0; i < 4; i++) {
            swarm.spawned();
        }
        assertTrue(swarm.allSpawned());
        swarm.lost();
        assertFalse(swarm.allSpawned());
        assertEquals(1, swarm.nextWave());
        swarm.spawned();
        assertTrue(swarm.allSpawned());
    }

    @Test
    void cannotKillAnEyeThatIsNotOut() {
        HorusEyeSwarm swarm = new HorusEyeSwarm(2);
        assertThrows(IllegalStateException.class, () -> swarm.killedOne(100.0));
        assertThrows(IllegalArgumentException.class, () -> new HorusEyeSwarm(0));
    }
}
