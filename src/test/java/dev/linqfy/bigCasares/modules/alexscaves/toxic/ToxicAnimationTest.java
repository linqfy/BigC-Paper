package dev.linqfy.bigCasares.modules.alexscaves.toxic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ToxicAnimationTest {
    @Test
    void nucleeperClosesWhileTheFuseBurnsAndShakesAtTheEnd() {
        assertNull(ToxicMobs.Nucleeper.state(0, 300));
        assertEquals("close", ToxicMobs.Nucleeper.state(1, 300));
        assertEquals("close", ToxicMobs.Nucleeper.state(294, 300));
        assertEquals("explode", ToxicMobs.Nucleeper.state(295, 300));
    }
}
