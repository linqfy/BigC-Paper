package dev.linqfy.bigCasares.modules.mutango;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MutangoFlightWatchTest {

    private final UUID player = UUID.randomUUID();

    @Test
    void unosPocosIntentosNoAvisan() {
        MutangoFlightWatch watch = new MutangoFlightWatch();
        for (int i = 0; i < MutangoFlightWatch.LIMIT - 1; i++) {
            assertFalse(watch.attempt(player, i * 100L));
        }
    }

    @Test
    void muchosIntentosSeguidosAvisan() {
        MutangoFlightWatch watch = new MutangoFlightWatch();
        boolean warned = false;
        for (int i = 0; i < MutangoFlightWatch.LIMIT; i++) {
            warned = watch.attempt(player, i * 100L);
        }
        assertTrue(warned, "el intento numero LIMIT dentro de la ventana avisa");
    }

    @Test
    void siSePasanDeLaVentanaNoCuentan() {
        MutangoFlightWatch watch = new MutangoFlightWatch();
        for (int i = 0; i < MutangoFlightWatch.LIMIT * 3; i++) {
            // uno cada 1.5 s: nunca hay LIMIT adentro de una ventana
            assertFalse(watch.attempt(player, i * 1_500L));
        }
    }

    @Test
    void avisaUnaSolaVezYDespuesEsperaAntesDeVolver() {
        MutangoFlightWatch watch = new MutangoFlightWatch();
        int warnings = 0;
        for (int i = 0; i < MutangoFlightWatch.LIMIT * 4; i++) {
            if (watch.attempt(player, i * 10L)) {
                warnings++;
            }
        }
        assertTrue(warnings == 1, "una sola vez mientras sigue el mismo intento");
        long later = MutangoFlightWatch.COOLDOWN_MILLIS + 1_000;
        boolean again = false;
        for (int i = 0; i < MutangoFlightWatch.LIMIT; i++) {
            again = watch.attempt(player, later + i * 10L);
        }
        assertTrue(again, "pasado el tiempo, si lo vuelve a hacer avisa de nuevo");
    }

    @Test
    void cadaJugadorTieneLaSuya() {
        MutangoFlightWatch watch = new MutangoFlightWatch();
        UUID other = UUID.randomUUID();
        for (int i = 0; i < MutangoFlightWatch.LIMIT - 1; i++) {
            watch.attempt(player, i * 10L);
        }
        assertFalse(watch.attempt(other, 100L), "los intentos de uno no cuentan para otro");
    }
}
