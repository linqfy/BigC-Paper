package dev.linqfy.bigCasares.modules.mythicdungeons.boss.came;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameDuoHealthTest {

    @Test
    void theBarIsTheSumOfBothLords() {
        CameDuoHealth health = new CameDuoHealth(720.0);
        assertEquals(360.0, health.lordMaximum());
        assertEquals(720.0, health.total());
        health.hit(CameLord.HUN, 50.0, 0L);
        assertEquals(310.0, health.health(CameLord.HUN));
        assertEquals(670.0, health.total());
    }

    @Test
    void eachGoalHurtsBothLordsAndThreeGoalsLeaveThemStanding() {
        CameDuoHealth health = new CameDuoHealth(720.0);
        assertEquals(72.0, health.goal(), 1.0e-9);
        health.goal();
        health.goal();
        assertEquals(252.0, health.health(CameLord.HUN), 1.0e-9);
        assertEquals(252.0, health.health(CameLord.VUCUB), 1.0e-9);
        for (int extra = 0; extra < 20; extra++) {
            health.goal();
        }
        assertTrue(health.health(CameLord.HUN) >= 1.0 - 1.0e-9, "a goal never kills");
        assertFalse(health.down(CameLord.HUN));
    }

    @Test
    void aFallenLordIsRevivedAfterTenSecondsAndTheBarNeverReachesZero() {
        CameDuoHealth health = new CameDuoHealth(720.0);
        CameDuoHealth.Hit hit = health.hit(CameLord.VUCUB, 1000.0, 100L);
        assertEquals(360.0, hit.removed());
        assertTrue(hit.downed());
        assertFalse(hit.bothDown());
        assertEquals(360.0, health.total(), "the bar shows the one still standing");
        assertEquals(Optional.of(CameLord.VUCUB), health.awaitingRevive());
        // golpes a un caido no hacen nada
        assertEquals(0.0, health.hit(CameLord.VUCUB, 10.0, 120L).removed());
        assertEquals(CameDuoHealth.REVIVE_TICKS - 50, health.reviveTicksLeft(150L));
        assertEquals(Optional.empty(), health.tick(100L + CameDuoHealth.REVIVE_TICKS - 1));
        Optional<CameDuoHealth.Revival> revival = health.tick(100L + CameDuoHealth.REVIVE_TICKS);
        assertEquals(Optional.of(new CameDuoHealth.Revival(CameLord.VUCUB, 180.0)), revival);
        assertFalse(health.down(CameLord.VUCUB));
        assertEquals(540.0, health.total());
    }

    @Test
    void eachReviveIsWeaker() {
        CameDuoHealth health = new CameDuoHealth(100.0);
        double[] expected = {25.0, 20.0, 15.0, 12.5, 12.5};
        long now = 0L;
        for (double restored : expected) {
            health.hit(CameLord.HUN, 1000.0, now);
            now += CameDuoHealth.REVIVE_TICKS;
            assertEquals(restored, health.tick(now).orElseThrow().restored(), 1.0e-9);
        }
        assertEquals(5, health.revives());
    }

    @Test
    void theReviverTakesMoreDamageAndKillingHimInTimeDownsBoth() {
        CameDuoHealth health = new CameDuoHealth(720.0);
        health.hit(CameLord.HUN, 1000.0, 0L);
        CameDuoHealth.Hit hit = health.hit(CameLord.VUCUB, 100.0, 50L);
        assertEquals(100.0 * CameDuoHealth.REVIVING_MULTIPLIER, hit.removed(), 1.0e-9);
        CameDuoHealth.Hit last = health.hit(CameLord.VUCUB, 1000.0, 150L);
        assertTrue(last.downed());
        assertTrue(last.bothDown());
        assertEquals(0.0, health.total());
        // nadie revive a nadie
        assertEquals(Optional.empty(), health.tick(10_000L));
        assertTrue(health.bothDown());
    }

    @Test
    void killingTheSecondTooLateIsNotEnough() {
        CameDuoHealth health = new CameDuoHealth(720.0);
        health.hit(CameLord.HUN, 1000.0, 0L);
        assertFalse(health.hit(CameLord.VUCUB, 100.0, 10L).downed());
        assertTrue(health.tick(CameDuoHealth.REVIVE_TICKS).isPresent());
        CameDuoHealth.Hit hit = health.hit(CameLord.VUCUB, 1000.0, CameDuoHealth.REVIVE_TICKS + 1);
        assertTrue(hit.downed());
        assertFalse(hit.bothDown(), "Hun is back on his feet");
        // sin el bonus de daño: ya nadie esta reviviendo cuando le pegan... hasta que cae
        assertEquals(Optional.of(CameLord.VUCUB), health.awaitingRevive());
    }

    @Test
    void ignoresNonsense() {
        CameDuoHealth health = new CameDuoHealth(100.0);
        assertEquals(0.0, health.hit(CameLord.HUN, -5.0, 0L).removed());
        assertEquals(0.0, health.hit(CameLord.HUN, Double.NaN, 0L).removed());
        assertThrows(IllegalArgumentException.class, () -> new CameDuoHealth(0.0));
    }
}
