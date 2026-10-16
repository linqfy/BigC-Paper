package dev.linqfy.bigCasares.modules.mutango;

import java.util.ArrayList;
import java.util.List;

/**
 * El poema que sale en pantalla en los cuatro momentos de la pelea (lo muestra MutangoCinematic), y como se parte un
 * verso en letras para la maquina de escribir. Sin Bukkit.
 */
final class MutangoPoem {

    /** Al correr el cubo del santuario. */
    static final List<String> AWAKENING = List.of(
        "Ahora sé, que el kultrún, nuestro instrumento sagrado,",
        "representa al planeta,",
        "a wenu Mapu que es el espacio de la atmósfera,");
    /** Al terminar la fase 1. */
    static final List<String> END_OF_PHASE_ONE = List.of(
        "a trufken Mapu que es la superficie",
        "y a minche Mapu que es el subsuelo. [...]");
    /** Al terminar la fase 2. */
    static final List<String> END_OF_PHASE_TWO = List.of(
        "Ahora que las fuerzas de la naturaleza",
        "están cortadas por alambrados, cables y caños.");
    /** Al terminar la fase 3. */
    static final List<String> END_OF_PHASE_THREE = List.of(
        "Minche Mapu entubada.",
        "Trufken Mapu habitada por la prisa...");
    /**
     * Lo ultimo que dice Neguechen cuando la venciste, flotando antes de irse (despues de END_OF_PHASE_THREE). Esta
     * todo en mapudungun: va entero en dorado.
     */
    static final List<String> FAREWELL = List.of(
        "Elueyu tami elun.",
        "Minche Mapu mew küpan.",
        "Weichayal wedake che mew.",
        "Pepilan ñi newen mew.");

    /** Palabras en mapudungun: van en dorado. Las mas largas primero, para que ganen si empiezan igual. */
    static final List<String> MAPUDUNGUN = List.of("trufken Mapu", "Trufken Mapu", "minche Mapu", "Minche Mapu",
        "wenu Mapu", "kultrún");
    static final String VERSE = "§f§o";
    static final String WORD = "§6§o";

    private MutangoPoem() {
    }

    /**
     * El verso en "letras" que ya traen su color (codigo de color + la letra), asi la maquina de escribir nunca corta un
     * codigo de color al mostrar un pedazo.
     */
    static List<String> letters(String line) {
        List<String> out = new ArrayList<>();
        if (FAREWELL.contains(line)) {
            for (char c : line.toCharArray()) {
                out.add(WORD + c);
            }
            return out;
        }
        int i = 0;
        while (i < line.length()) {
            String word = null;
            for (String candidate : MAPUDUNGUN) {
                if (line.startsWith(candidate, i)) {
                    word = candidate;
                    break;
                }
            }
            if (word != null) {
                for (char c : word.toCharArray()) {
                    out.add(WORD + c);
                }
                i += word.length();
            } else {
                out.add(VERSE + line.charAt(i));
                i++;
            }
        }
        return out;
    }

    /** Ancho maximo (en unidades de la letra de Minecraft) de lo que entra en un subtitulo en cualquier pantalla. */
    static final int MAX_WIDTH = 190;

    /** Ancho aproximado de un texto con la letra de Minecraft (las letras finas ocupan menos). */
    static int width(String text) {
        int total = 0;
        for (char c : text.toCharArray()) {
            total += switch (c) {
                case 'i', 'l', '!', '.', ',', ':', ';', '\'', '|' -> 3;
                case 't', 'I', '[', ']', ' ' -> 4;
                case 'f', 'k' -> 5;
                default -> 6;
            };
        }
        return total;
    }

    /**
     * Parte un verso en pedazos que entren en la pantalla (cada uno de a lo sumo maxWidth), cortando solo en espacios y
     * sin partir una palabra en mapudungun de dos palabras (para que no pierda el dorado). Un verso que ya entra queda
     * entero.
     */
    static List<String> fit(String line, int maxWidth) {
        List<String> pieces = new ArrayList<>();
        if (width(line) <= maxWidth) {
            pieces.add(line);
            return pieces;
        }
        List<String> tokens = new ArrayList<>();
        int i = 0;
        while (i < line.length()) {
            int end = -1;
            for (String candidate : MAPUDUNGUN) {
                if (line.startsWith(candidate, i)) {
                    end = i + candidate.length();
                    break;
                }
            }
            // hasta el proximo espacio (asi la coma o el punto que sigue a la palabra va pegado a ella)
            end = line.indexOf(' ', Math.max(end, i));
            end = end < 0 ? line.length() : end;
            tokens.add(line.substring(i, end));
            i = end;
            while (i < line.length() && line.charAt(i) == ' ') {
                i++;
            }
        }
        StringBuilder current = new StringBuilder();
        for (String token : tokens) {
            String joined = current.isEmpty() ? token : current + " " + token;
            if (!current.isEmpty() && width(joined) > maxWidth) {
                pieces.add(current.toString());
                current = new StringBuilder(token);
            } else {
                current = new StringBuilder(joined);
            }
        }
        if (!current.isEmpty()) {
            pieces.add(current.toString());
        }
        return pieces;
    }
}
