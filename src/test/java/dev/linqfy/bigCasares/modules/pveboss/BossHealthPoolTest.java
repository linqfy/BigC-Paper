package dev.linqfy.bigCasares.modules.pveboss;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossHealthPoolTest {

    @Test
    void keepsBossHealthIndependentFromTheVanillaAttributeCap() {
        BossHealthPool health = new BossHealthPool(20_000.0);

        health.damage(8.0);

        assertEquals(19_992.0, health.current());
        assertEquals(0.9996, health.fraction(), 0.00001);
        health.damage(30_000.0);
        assertTrue(health.destroyed());
        assertEquals(0.0, health.current());
    }

    @Test
    void healsUpToTheMaximumOnly() {
        BossHealthPool health = new BossHealthPool(100.0);
        health.damage(40.0);

        assertEquals(70.0, health.heal(10.0));
        assertEquals(100.0, health.heal(500.0));
        assertThrows(IllegalArgumentException.class, () -> health.heal(-1.0));
        assertThrows(IllegalArgumentException.class, () -> health.heal(Double.NaN));
    }
}
