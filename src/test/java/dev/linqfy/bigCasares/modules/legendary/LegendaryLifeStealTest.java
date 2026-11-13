package dev.linqfy.bigCasares.modules.legendary;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegendaryLifeStealTest {
    @Test
    void onePrimaryHitRestoresHalfAHeartThroughoutTheActiveWindow() {
        for (int age : new int[]{0, 100, 199}) {
            assertEquals(1, new LegendaryLifeSteal.Hit(age, true, .1, 10, 20).healing());
        }
    }

    @Test
    void inactiveExpiredAndSecondaryHitsNeverRestoreHealth() {
        for (int age : new int[]{-1, 200, 300}) {
            assertEquals(0, new LegendaryLifeSteal.Hit(age, true, 20, 10, 20).healing());
        }
        assertEquals(0, new LegendaryLifeSteal.Hit(50, false, 20, 10, 20).healing());
    }

    @Test
    void onlyPositiveFiniteDamageCanProvideHealing() {
        for (double damage : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertEquals(0, new LegendaryLifeSteal.Hit(10, true, damage, 10, 20).healing());
        }
    }

    @Test
    void healingFitsTheRemainingHealthAndCannotReviveADeadPlayer() {
        assertEquals(.25, new LegendaryLifeSteal.Hit(10, true, 10, 19.75, 20).healing());
        assertEquals(.5, new LegendaryLifeSteal.Hit(10, true, 10, 39.5, 40).healing());
        assertEquals(0, new LegendaryLifeSteal.Hit(10, true, 10, 20, 20).healing());
        assertEquals(0, new LegendaryLifeSteal.Hit(10, true, 10, 0, 20).healing());
    }

    @Test
    void invalidHealthSamplesCannotProduceAHeal() {
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertEquals(0, new LegendaryLifeSteal.Hit(10, true, 10, invalid, 20).healing());
            assertEquals(0, new LegendaryLifeSteal.Hit(10, true, 10, 10, invalid).healing());
        }
    }
}
