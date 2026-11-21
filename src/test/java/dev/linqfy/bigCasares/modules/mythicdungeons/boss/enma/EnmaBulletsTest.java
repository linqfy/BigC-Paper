package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enma;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnmaBulletsTest {

    private static final EnmaArena ARENA = EnmaArena.around(0.5, 64.0, 0.5);
    private static final EnmaBullets.Solid OPEN = (x, y, z) -> y < 64;
    /** Una pared de x = 5 en adelante. */
    private static final EnmaBullets.Solid WALL = (x, y, z) -> y < 64 || x >= 5;

    @Test
    void aBulletTravelsInAStraightLineAndHitsAPlayerInItsWay() {
        EnmaBullets bullets = new EnmaBullets(ARENA);
        UUID player = UUID.randomUUID();
        assertTrue(bullets.fire(0.5, 65.1, 0.5, 0.0, 0.25, OPEN));
        List<EnmaBullets.Target> targets = List.of(new EnmaBullets.Target(player, 4.5, 64.0, 0.5));
        int hitAt = -1;
        for (int tick = 1; tick <= 40 && hitAt < 0; tick++) {
            if (bullets.step(OPEN, targets).hits().contains(player)) {
                hitAt = tick;
            }
        }
        assertTrue(hitAt > 10 && hitAt < 17, "hit at " + hitAt);
        assertEquals(0, bullets.size());
    }

    @Test
    void aPlayerBesideTheLineIsNotHit() {
        EnmaBullets bullets = new EnmaBullets(ARENA);
        UUID player = UUID.randomUUID();
        bullets.fire(0.5, 65.1, 0.5, 0.0, 0.25, OPEN);
        List<EnmaBullets.Target> targets = List.of(new EnmaBullets.Target(player, 4.5, 64.0, 1.5));
        for (int tick = 0; tick < 80; tick++) {
            assertFalse(bullets.step(OPEN, targets).hits().contains(player));
        }
    }

    @Test
    void bulletsDoNotPassThroughWalls() {
        EnmaBullets bullets = new EnmaBullets(ARENA);
        UUID behind = UUID.randomUUID();
        bullets.fire(0.5, 65.1, 0.5, 0.0, 0.25, WALL);
        List<EnmaBullets.Target> targets = List.of(new EnmaBullets.Target(behind, 8.5, 64.0, 0.5));
        int pops = 0;
        for (int tick = 0; tick < 80; tick++) {
            EnmaBullets.Step step = bullets.step(WALL, targets);
            assertFalse(step.hits().contains(behind));
            pops += step.pops().size();
        }
        assertEquals(1, pops);
        assertEquals(0, bullets.size());
        assertFalse(bullets.fire(6.5, 65.1, 0.5, 0.0, 0.25, WALL), "cannot be born inside a block");
    }

    @Test
    void bulletsDieAtTheEdgeOfTheRoomAndAreCapped() {
        EnmaBullets bullets = new EnmaBullets(ARENA);
        bullets.fire(0.5, 65.1, 0.5, Math.PI / 2.0, 0.3, OPEN);
        for (int tick = 0; tick < 70; tick++) {
            bullets.step(OPEN, List.of());
        }
        assertEquals(0, bullets.size());
        for (int i = 0; i < EnmaBullets.MAX_BULLETS + 20; i++) {
            bullets.fire(0.5, 65.1, 0.5, i * 0.1, 0.01, OPEN);
        }
        assertEquals(EnmaBullets.MAX_BULLETS, bullets.size());
        for (int tick = 0; tick <= EnmaBullets.MAX_AGE; tick++) {
            bullets.step(OPEN, List.of());
        }
        assertEquals(0, bullets.size());
        bullets.fire(0.5, 65.1, 0.5, 0.0, 0.1, OPEN);
        bullets.clear();
        assertEquals(0, bullets.size());
    }

    @Test
    void jumpingDoesNotDodgeButStandingOnAnotherFloorDoes() {
        EnmaBullets bullets = new EnmaBullets(ARENA);
        UUID jumper = UUID.randomUUID();
        UUID above = UUID.randomUUID();
        bullets.fire(0.5, 65.1, 0.5, 0.0, 0.25, OPEN);
        bullets.fire(0.5, 65.1, 0.5, Math.PI, 0.25, OPEN);
        List<EnmaBullets.Target> targets = List.of(
            new EnmaBullets.Target(jumper, 3.5, 65.2, 0.5),
            new EnmaBullets.Target(above, -2.5, 67.5, 0.5));
        boolean jumperHit = false;
        for (int tick = 0; tick < 80; tick++) {
            EnmaBullets.Step step = bullets.step(OPEN, targets);
            jumperHit |= step.hits().contains(jumper);
            assertFalse(step.hits().contains(above));
        }
        assertTrue(jumperHit);
    }
}
