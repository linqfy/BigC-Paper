package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.LiveText;
import java.util.HashMap;
import java.util.Map;

/**
 * The project 5x7 font beyond upper-case ASCII: real lower-case letters, accented letters and symbols.
 * Handwritten for Tideframe like {@link PackGenerator#pattern(char)}; no system or third-party font is read.
 * Accented capitals keep the accent inside the 7-row cell (accent in rows 0-1, a 5-row body below), so line
 * height and every layout rule stay exactly as they are for plain ASCII.
 */
public final class Glyphs {
    private Glyphs() { }

    private static final String[] LOWER = {
            "00000/00000/01110/00001/01111/10001/01111", // a
            "10000/10000/10110/11001/10001/10001/11110", // b
            "00000/00000/01110/10000/10000/10001/01110", // c
            "00001/00001/01101/10011/10001/10001/01111", // d
            "00000/00000/01110/10001/11111/10000/01110", // e
            "00110/01001/01000/11100/01000/01000/01000", // f
            "00000/00000/01111/10001/01111/00001/01110", // g
            "10000/10000/10110/11001/10001/10001/10001", // h
            "00100/00000/01100/00100/00100/00100/01110", // i
            "00010/00000/00110/00010/00010/10010/01100", // j
            "10000/10000/10010/10100/11000/10100/10010", // k
            "01100/00100/00100/00100/00100/00100/01110", // l
            "00000/00000/11010/10101/10101/10001/10001", // m
            "00000/00000/10110/11001/10001/10001/10001", // n
            "00000/00000/01110/10001/10001/10001/01110", // o
            "00000/00000/11110/10001/11110/10000/10000", // p
            "00000/00000/01101/10011/01111/00001/00001", // q
            "00000/00000/10110/11001/10000/10000/10000", // r
            "00000/00000/01110/10000/01110/00001/11110", // s
            "01000/01000/11100/01000/01000/01001/00110", // t
            "00000/00000/10001/10001/10001/10011/01101", // u
            "00000/00000/10001/10001/10001/01010/00100", // v
            "00000/00000/10001/10001/10101/10101/01010", // w
            "00000/00000/10001/01010/00100/01010/10001", // x
            "00000/00000/10001/10001/01111/00001/01110", // y
            "00000/00000/11111/00010/00100/01000/11111"  // z
    };
    private static final Map<Character, String> EXTRA = new HashMap<>();
    private static void accented(String letters, String accent, String... bodies) {
        for (int i = 0; i < letters.length(); i++) EXTRA.put(letters.charAt(i), accent + "/" + bodies[i]);
    }
    static {
        // Five-row bodies under a two-row accent.
        String capA = "01110/10001/11111/10001/10001", capE = "11111/10000/11110/10000/11111", capI = "01110/00100/00100/00100/01110",
                capO = "01110/10001/10001/10001/01110", capU = "10001/10001/10001/10001/01110", capN = "10001/11001/10101/10011/10001";
        String a = "01110/00001/01111/10001/01111", e = "01110/10001/11111/10000/01110", i = "01100/00100/00100/00100/01110",
                o = "01110/10001/10001/10001/01110", u = "10001/10001/10001/10011/01101";
        String acute = "00010/00100", grave = "01000/00100", circumflex = "00100/01010", diaeresis = "01010/00000", tilde = "01101/10010";
        accented("ÁÉÍÓÚ", acute, capA, capE, capI, capO, capU);
        accented("ÀÈÌÒÙ", grave, capA, capE, capI, capO, capU);
        accented("ÂÊÎÔÛ", circumflex, capA, capE, capI, capO, capU);
        accented("ÄËÏÖÜ", diaeresis, capA, capE, capI, capO, capU);
        accented("ÃÕÑ", tilde, capA, capO, capN);
        accented("áéíóú", acute, a, e, i, o, u);
        accented("àèìòù", grave, a, e, i, o, u);
        accented("âêîôû", circumflex, a, e, i, o, u);
        accented("äëïöü", diaeresis, a, e, i, o, u);
        accented("ãõ", tilde, a, o);
        EXTRA.put('ñ', "01101/10010/00000/10110/11001/10001/10001"); // a gap row keeps the tilde off the n
        EXTRA.put('Ç', "01110/10001/10000/10000/10001/01110/00100");
        EXTRA.put('ç', "00000/00000/01110/10000/10000/01110/00100");
        EXTRA.put('¡', "00100/00000/00100/00100/00100/00100/00100");
        EXTRA.put('¿', "00100/00000/00100/01000/10000/10001/01110");
        EXTRA.put('°', "01100/10010/10010/01100/00000/00000/00000");
        EXTRA.put('º', "01100/10010/10010/01100/00000/11110/00000");
        EXTRA.put('ª', "01110/10010/10010/01101/00000/11110/00000");
        EXTRA.put('«', "00000/00101/01010/10100/01010/00101/00000");
        EXTRA.put('»', "00000/10100/01010/00101/01010/10100/00000");
        EXTRA.put('·', "00000/00000/00000/00100/00000/00000/00000");
        EXTRA.put('€', "00111/01000/11110/01000/11110/01000/00111");
        EXTRA.put('×', "00000/10001/01010/00100/01010/10001/00000");
        EXTRA.put('÷', "00000/00100/00000/11111/00000/00100/00000");
        EXTRA.put('±', "00100/00100/11111/00100/00100/00000/11111");
        EXTRA.put('²', "01100/10010/00100/01000/11110/00000/00000");
        EXTRA.put('³', "11100/00010/01100/00010/11100/00000/00000");
        EXTRA.put('•', "00000/00000/01110/01110/01110/00000/00000");
        EXTRA.put('…', "00000/00000/00000/00000/00000/00000/10101");
        EXTRA.put('←', "00000/00100/01000/11111/01000/00100/00000");
        EXTRA.put('↑', "00100/01110/10101/00100/00100/00100/00100");
        EXTRA.put('→', "00000/00100/00010/11111/00010/00100/00000");
        EXTRA.put('↓', "00100/00100/00100/00100/10101/01110/00100");
        EXTRA.put('★', "00100/00100/11111/01110/01010/10001/00000");
        EXTRA.put('❤', "00000/01010/11111/11111/01110/00100/00000");
        EXTRA.put('✔', "00000/00001/00011/10110/11100/01000/00000");
        EXTRA.put('✖', "00000/11011/01110/00100/01110/11011/00000");
        if (EXTRA.size() != LiveText.EXTRA.length()) throw new IllegalStateException("Glyph table and LiveText.EXTRA differ");
        for (char c : LiveText.EXTRA.toCharArray()) if (!EXTRA.containsKey(c)) throw new IllegalStateException("Missing glyph " + c);
    }

    /** Exact 5x7 rows of a drawable character, case as given ("0"/"1", rows separated by "/"). */
    public static String pattern(char c) {
        if (c >= 'a' && c <= 'z') return LOWER[c - 'a'];
        String extra = EXTRA.get(c);
        return extra != null ? extra : PackGenerator.pattern(c);
    }
    /** Rows for {@code c} drawn in capitals ({@code upper}) or as written. */
    public static String pattern(char c, boolean upper) { return pattern(upper ? LiveText.upper(c) : c); }
    /** True when {@link #pattern(char)} has a glyph for {@code c}. */
    public static boolean has(char c) { return c == ' ' || c >= 'a' && c <= 'z' || EXTRA.containsKey(c) || c >= '!' && c <= '~'; }
}
