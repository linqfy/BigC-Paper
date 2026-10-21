package dev.linqfy.bigCasares.modules.mutango;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Los tiempos de los versos en pantalla (la cinematica). */
class MutangoVerseTimingTest {

    @Test
    void unVersoSeEscribeUnaLetraPorTick() {
        assertEquals(10, MutangoVerseTiming.reveal(10));
        assertEquals(1, MutangoVerseTiming.shown(10, 1));
        assertEquals(7, MutangoVerseTiming.shown(10, 7));
        assertEquals(10, MutangoVerseTiming.shown(10, 50), "nunca muestra mas letras de las que hay");
    }

    @Test
    void lasEtapasVanEnOrdenYSeCierranAlFinal() {
        int letters = 12;
        assertEquals(MutangoVerseTiming.Stage.TYPING, MutangoVerseTiming.stage(letters, 1));
        assertEquals(MutangoVerseTiming.Stage.TYPING, MutangoVerseTiming.stage(letters, 12));
        assertEquals(MutangoVerseTiming.Stage.COMPLETE, MutangoVerseTiming.stage(letters, 13));
        assertEquals(MutangoVerseTiming.Stage.HOLDING, MutangoVerseTiming.stage(letters, 14));
        int total = MutangoVerseTiming.total(letters);
        assertEquals(MutangoVerseTiming.Stage.HOLDING, MutangoVerseTiming.stage(letters, total - 1));
        assertEquals(MutangoVerseTiming.Stage.FINISHED, MutangoVerseTiming.stage(letters, total));
    }

    @Test
    void elVersoDuraEscrituraMasEsperaMasDesvanecidoMasRespiro() {
        assertEquals(12 + 55 + 20 + 6, MutangoVerseTiming.total(12));
    }

    @Test
    void laBarraDeAbajoSeReenviaCadaVeinteTicksMientrasSeQueda() {
        int letters = 10;
        int reveal = MutangoVerseTiming.reveal(letters);
        assertFalse(MutangoVerseTiming.resendOnActionBar(letters, reveal + 1), "el tick completo ya lo manda aparte");
        assertTrue(MutangoVerseTiming.resendOnActionBar(letters, reveal + 20));
        assertTrue(MutangoVerseTiming.resendOnActionBar(letters, reveal + 40));
        assertFalse(MutangoVerseTiming.resendOnActionBar(letters, reveal + 21));
        assertFalse(MutangoVerseTiming.resendOnActionBar(letters, reveal + 60), "pasado el tiempo de espera no");
    }

    @Test
    void unVersoVacioTerminaIgual() {
        assertEquals(MutangoVerseTiming.Stage.COMPLETE, MutangoVerseTiming.stage(0, 1));
        assertEquals(MutangoVerseTiming.Stage.FINISHED, MutangoVerseTiming.stage(0, MutangoVerseTiming.total(0)));
    }
}
