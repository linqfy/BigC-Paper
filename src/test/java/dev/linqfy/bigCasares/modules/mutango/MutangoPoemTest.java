package dev.linqfy.bigCasares.modules.mutango;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MutangoPoemTest {

    private static List<String> allVerses() {
        return Stream.of(MutangoPoem.AWAKENING, MutangoPoem.END_OF_PHASE_ONE, MutangoPoem.END_OF_PHASE_TWO,
            MutangoPoem.END_OF_PHASE_THREE, MutangoPoem.FAREWELL).flatMap(List::stream).toList();
    }

    private static String strip(String letter) {
        return letter.replace(MutangoPoem.VERSE, "").replace(MutangoPoem.WORD, "");
    }

    @Test
    void elPoemaEsElQuePediste() {
        assertEquals(List.of(
            "Ahora sé, que el kultrún, nuestro instrumento sagrado,",
            "representa al planeta,",
            "a wenu Mapu que es el espacio de la atmósfera,"), MutangoPoem.AWAKENING);
        assertEquals(List.of(
            "a trufken Mapu que es la superficie",
            "y a minche Mapu que es el subsuelo. [...]"), MutangoPoem.END_OF_PHASE_ONE);
        assertEquals(List.of(
            "Ahora que las fuerzas de la naturaleza",
            "están cortadas por alambrados, cables y caños."), MutangoPoem.END_OF_PHASE_TWO);
        assertEquals(List.of(
            "Minche Mapu entubada.",
            "Trufken Mapu habitada por la prisa..."), MutangoPoem.END_OF_PHASE_THREE);
        assertEquals(List.of(
            "Elueyu tami elun.",
            "Minche Mapu mew küpan.",
            "Weichayal wedake che mew.",
            "Pepilan ñi newen mew."), MutangoPoem.FAREWELL);
    }

    @Test
    void lasUltimasPalabrasDeNeguechenVanEnterasEnDorado() {
        for (String verse : MutangoPoem.FAREWELL) {
            assertTrue(MutangoPoem.letters(verse).stream().allMatch(l -> l.startsWith(MutangoPoem.WORD)), verse);
        }
        // y no le cambia el color al resto del poema: "Minche Mapu entubada." sigue con "entubada" en blanco
        assertTrue(MutangoPoem.letters("Minche Mapu entubada.").stream().anyMatch(l -> l.startsWith(MutangoPoem.VERSE)));
    }

    @Test
    void laEñeYLaDieresisNoSePierden() {
        StringBuilder joined = new StringBuilder();
        MutangoPoem.letters("Pepilan ñi newen mew.").forEach(l -> joined.append(strip(l)));
        assertEquals("Pepilan ñi newen mew.", joined.toString());
        assertTrue(String.join("", MutangoPoem.letters("Minche Mapu mew küpan.")).contains("ü"));
    }

    @Test
    void unaLetraPorCaracterSinPerderNinguna() {
        for (String verse : allVerses()) {
            List<String> letters = MutangoPoem.letters(verse);
            assertEquals(verse.length(), letters.size(), verse);
            StringBuilder joined = new StringBuilder();
            letters.forEach(letter -> joined.append(strip(letter)));
            assertEquals(verse, joined.toString());
        }
    }

    @Test
    void cadaLetraTraeSuColorAsiNuncaSeCortaUnCodigo() {
        for (String verse : allVerses()) {
            for (String letter : MutangoPoem.letters(verse)) {
                assertTrue(letter.startsWith(MutangoPoem.VERSE) || letter.startsWith(MutangoPoem.WORD), letter);
                assertEquals(1, strip(letter).length(), "una sola letra visible: " + letter);
            }
        }
    }

    @Test
    void lasPalabrasEnMapudungunVanEnDorado() {
        List<String> letters = MutangoPoem.letters("a wenu Mapu que es");
        StringBuilder gold = new StringBuilder();
        for (String letter : letters) {
            if (letter.startsWith(MutangoPoem.WORD)) {
                gold.append(strip(letter));
            }
        }
        assertEquals("wenu Mapu", gold.toString());
        assertTrue(MutangoPoem.letters("kultrún").stream().allMatch(l -> l.startsWith(MutangoPoem.WORD)));
    }

    @Test
    void cadaPalabraEnMapudungunApareceEnElPoema() {
        String poem = String.join(" ", allVerses());
        for (String word : MutangoPoem.MAPUDUNGUN) {
            assertTrue(poem.contains(word), "no aparece: " + word);
        }
    }

    @Test
    void elRestoVaEnBlanco() {
        assertTrue(MutangoPoem.letters("representa al planeta,").stream()
            .allMatch(l -> l.startsWith(MutangoPoem.VERSE)));
    }

    @Test
    void ningunVersoSeSaleDeLaPantalla() {
        for (String verse : allVerses()) {
            for (String piece : MutangoPoem.fit(verse, MutangoPoem.MAX_WIDTH)) {
                assertTrue(MutangoPoem.width(piece) <= MutangoPoem.MAX_WIDTH, piece);
            }
        }
    }

    @Test
    void partirUnVersoNoPierdeNiCambiaPalabras() {
        for (String verse : allVerses()) {
            assertEquals(verse, String.join(" ", MutangoPoem.fit(verse, MutangoPoem.MAX_WIDTH)));
        }
    }

    @Test
    void elVersoLargoSePartePeroElCortoQuedaEntero() {
        assertEquals(2, MutangoPoem.fit(MutangoPoem.AWAKENING.get(0), MutangoPoem.MAX_WIDTH).size());
        assertEquals(List.of("Minche Mapu entubada."), MutangoPoem.fit("Minche Mapu entubada.", MutangoPoem.MAX_WIDTH));
    }

    @Test
    void laPalabraEnMapudungunDeDosPalabrasNoSeParte() {
        for (String verse : allVerses()) {
            for (String phrase : List.of("trufken Mapu", "Trufken Mapu", "minche Mapu", "Minche Mapu", "wenu Mapu")) {
                if (verse.contains(phrase)) {
                    assertTrue(MutangoPoem.fit(verse, 100).stream().anyMatch(p -> p.contains(phrase)), verse);
                }
            }
        }
    }
}
