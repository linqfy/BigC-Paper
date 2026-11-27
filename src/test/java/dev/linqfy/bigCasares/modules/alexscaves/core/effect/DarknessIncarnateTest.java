package dev.linqfy.bigCasares.modules.alexscaves.core.effect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarknessIncarnateTest {
    @Test
    void celestialAngleIsZeroAtNoonAndHalfAtMidnight() {
        assertEquals(0.0, DarknessIncarnate.celestialAngle(6000), 1.0e-9);
        assertEquals(0.5, DarknessIncarnate.celestialAngle(18000), 1.0e-9);
    }

    @Test
    void openSkyIsBrightByDayOnly() {
        assertTrue(DarknessIncarnate.lit(0, true, 6000, 11));
        assertTrue(DarknessIncarnate.lit(0, true, 1000, 11));
        assertFalse(DarknessIncarnate.lit(0, true, 18000, 11));
        assertFalse(DarknessIncarnate.lit(0, true, 14000, 11));
    }

    @Test
    void daylightEdgesMatchTheModThresholds() {
        // Dusk turns dark between ticks 12900 and 13100, dawn light returns between 22900 and 23100.
        assertTrue(DarknessIncarnate.lit(0, true, 12900, 11));
        assertFalse(DarknessIncarnate.lit(0, true, 13100, 11));
        assertFalse(DarknessIncarnate.lit(0, true, 22900, 11));
        assertTrue(DarknessIncarnate.lit(0, true, 23100, 11));
    }

    @Test
    void caveLightUsesTheBlockLightThreshold() {
        assertFalse(DarknessIncarnate.lit(10, false, 6000, 11));
        assertTrue(DarknessIncarnate.lit(11, false, 6000, 11));
        assertFalse(DarknessIncarnate.lit(0, false, 6000, 11));
    }
}
