package dev.linqfy.bigCasares.modules.mutango;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MutangoDeathMessagesTest {

    @Test
    void losMensajesDeLosMutangosSonLosQueSePidieron() {
        assertEquals(List.of(
            "Lef te ha matado",
            "Moriste a pies de Lef",
            "El poder mapuche de Lef te mató"),
            List.of(MutangoDeathMessages.byMutango("Lef", 0), MutangoDeathMessages.byMutango("Lef", 1),
                MutangoDeathMessages.byMutango("Lef", 2)));
    }

    @Test
    void losMensajesDeNeguechenSonLosQueSePidieron() {
        assertEquals(List.of(
            "Neguechen te mató",
            "Neguechen te destruyó",
            "Se nota que sos tan malo que una diosa te ganó",
            "Sos malísimo, te ganó una mapuche"), MutangoDeathMessages.NEGUECHEN);
    }

    @Test
    void elNombreDeCadaMutangoVaEnSuMensaje() {
        for (String name : List.of("Lef", "Lihuen", "Lican")) {
            for (int roll = 0; roll < MutangoDeathMessages.MUTANGO.size(); roll++) {
                assertTrue(MutangoDeathMessages.byMutango(name, roll).contains(name));
            }
        }
    }

    @Test
    void elMensajeDelCagonEsElQueSePidio() {
        assertEquals("Moriste por cagón, las zonas protegidas como que no son para pelear, eh",
            MutangoDeathMessages.COWARD);
        assertEquals("Hola Gente, Juan es un cagón, no entiende que la idea de las peleas es pelear y no es meterse en "
            + "zonas protegidas para evitar ataques, no sean como Juan", MutangoDeathMessages.cowardAnnouncement("Juan"));
    }

    @Test
    void cualquierNumeroDaUnMensajeValido() {
        for (int roll : new int[] {-7, -1, 0, 3, 4, 100, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
            assertTrue(MutangoDeathMessages.NEGUECHEN.contains(MutangoDeathMessages.byNeguechen(roll)));
            assertTrue(MutangoDeathMessages.byMutango("Lican", roll).contains("Lican"));
        }
    }
}
