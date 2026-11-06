package dev.linqfy.bigCasares.modules.mutango;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La secuencia del despertar del santuario. */
class MutangoShrineTimelineTest {

    @Test
    void lasEtapasVanEnOrden() {
        assertTrue(MutangoShrineTimeline.SLIDE_TICKS < MutangoShrineTimeline.RISE_END);
        assertTrue(MutangoShrineTimeline.RISE_END <= MutangoShrineTimeline.SPLIT_AT);
        assertTrue(MutangoShrineTimeline.SPLIT_AT < MutangoShrineTimeline.ARRIVE_AT);
        assertTrue(MutangoShrineTimeline.ARRIVE_AT < MutangoShrineTimeline.CLOSE_END);
    }

    @Test
    void cadaTramoVaDeCeroAUnoYNuncaSeSale() {
        assertEquals(0.0, MutangoShrineTimeline.slide(0), 1e-9);
        assertEquals(1.0, MutangoShrineTimeline.slide(MutangoShrineTimeline.SLIDE_TICKS), 1e-9);
        assertEquals(0.0, MutangoShrineTimeline.rise(MutangoShrineTimeline.SLIDE_TICKS), 1e-9);
        assertEquals(1.0, MutangoShrineTimeline.rise(MutangoShrineTimeline.RISE_END), 1e-9);
        assertEquals(1.0, MutangoShrineTimeline.rise(MutangoShrineTimeline.SPLIT_AT), 1e-9, "se queda flotando arriba");
        assertEquals(0.0, MutangoShrineTimeline.travel(MutangoShrineTimeline.SPLIT_AT), 1e-9);
        assertEquals(1.0, MutangoShrineTimeline.travel(MutangoShrineTimeline.ARRIVE_AT), 1e-9);
        assertEquals(0.0, MutangoShrineTimeline.close(MutangoShrineTimeline.ARRIVE_AT), 1e-9);
        assertEquals(1.0, MutangoShrineTimeline.close(MutangoShrineTimeline.CLOSE_END), 1e-9);
        for (int t = -5; t < 130; t++) {
            for (double s : new double[] {MutangoShrineTimeline.slide(t), MutangoShrineTimeline.rise(t),
                MutangoShrineTimeline.travel(t), MutangoShrineTimeline.close(t)}) {
                assertTrue(s >= 0.0 && s <= 1.0, "fuera de rango en el tick " + t + ": " + s);
            }
        }
    }

    @Test
    void elCuboFrenaAlCorrerse() {
        // con la curva de frenado, los primeros ticks avanzan mas que los ultimos
        double first = MutangoShrineTimeline.slide(5) - MutangoShrineTimeline.slide(0);
        double last = MutangoShrineTimeline.slide(MutangoShrineTimeline.SLIDE_TICKS)
            - MutangoShrineTimeline.slide(MutangoShrineTimeline.SLIDE_TICKS - 5);
        assertTrue(first > last);
    }

    @Test
    void losTramosSonCrecientes() {
        for (int t = 1; t < MutangoShrineTimeline.CLOSE_END; t++) {
            assertTrue(MutangoShrineTimeline.slide(t) >= MutangoShrineTimeline.slide(t - 1));
            assertTrue(MutangoShrineTimeline.rise(t) >= MutangoShrineTimeline.rise(t - 1));
            assertTrue(MutangoShrineTimeline.travel(t) >= MutangoShrineTimeline.travel(t - 1));
            assertTrue(MutangoShrineTimeline.close(t) >= MutangoShrineTimeline.close(t - 1));
        }
    }
}
