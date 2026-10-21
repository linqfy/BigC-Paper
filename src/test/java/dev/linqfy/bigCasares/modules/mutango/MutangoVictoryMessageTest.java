package dev.linqfy.bigCasares.modules.mutango;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MutangoVictoryMessageTest {

    @Test
    void unJugadorLeGanoALaDiosa() {
        assertEquals("Juan le ha ganado a la diosa mapuche Neguechen", MutangoVictoryMessage.of(List.of("Juan")));
    }

    @Test
    void variosJugadoresLeHanGanado() {
        assertEquals("Juan y Ana le han ganado a la diosa mapuche Neguechen",
            MutangoVictoryMessage.of(List.of("Juan", "Ana")));
        assertEquals("Juan, Ana y Leo le han ganado a la diosa mapuche Neguechen",
            MutangoVictoryMessage.of(List.of("Juan", "Ana", "Leo")));
    }

    @Test
    void siSonMuchosNombraAlosPrimerosYCuentaElResto() {
        assertEquals("A, B, C, D y 2 más le han ganado a la diosa mapuche Neguechen",
            MutangoVictoryMessage.of(List.of("A", "B", "C", "D", "E", "F")));
    }

    @Test
    void sinNombresDiceQueFueVencida() {
        assertEquals("Neguechen fue vencida", MutangoVictoryMessage.of(List.of()));
    }
}
