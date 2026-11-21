package dev.linqfy.bigCasares.modules.caichet;

import java.util.List;

/**
 * Las fotos del flashback de la Jarra de Memorias. Cada foto ocupa toda la pantalla, pero el juego no dibuja letras de
 * mas de 256 pixeles: va partida en mosaicos, que son letras seguidas de la fuente {@code caichet:memorias} (de
 * izquierda a derecha y de arriba abajo; la fuente pone cada fila a su altura).
 */
final class MemoryFrames {

    /** Lo que ve Bedrock en lugar de cada foto (no tiene la fuente con las imagenes): una frase por foto. */
    static final List<String> BEDROCK_TEXT = List.of("la terraza", "el vuelo", "la selfie", "la oficina",
        "el almuerzo", "el trabajo", "el elefante", "la laguna");
    /** Una foto por frase. */
    static final int COUNT = BEDROCK_TEXT.size();
    static final int COLUMNS = 4;
    static final int ROWS = 2;
    /** La primera letra de la primera foto; las demas siguen en orden. */
    static final int FIRST_GLYPH = 0xE000;
    /** Espacio de ancho negativo de la fuente: saca el pixel que el juego deja entre dos letras. */
    static final char NO_GAP = (char) 0xF8FF;
    /** Espacio de ancho negativo de la fuente: vuelve al inicio de la fila. */
    static final char ROW_START = (char) 0xF8FE;

    private MemoryFrames() {
    }

    /** La letra del mosaico (fila, columna) de una foto. */
    static char glyph(int frame, int row, int column) {
        return (char) (FIRST_GLYPH + frame * COLUMNS * ROWS + row * COLUMNS + column);
    }

    /** El texto que dibuja la foto numero frame: sus mosaicos, fila por fila, sin huecos entre ellos. */
    static String glyphs(int frame) {
        StringBuilder text = new StringBuilder();
        for (int row = 0; row < ROWS; row++) {
            if (row > 0) {
                text.append(ROW_START);
            }
            for (int column = 0; column < COLUMNS; column++) {
                text.append(glyph(frame, row, column)).append(NO_GAP);
            }
        }
        return text.toString();
    }
}
