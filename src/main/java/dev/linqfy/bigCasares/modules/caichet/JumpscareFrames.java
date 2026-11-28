package dev.linqfy.bigCasares.modules.caichet;

/**
 * Las imagenes de los sustos de las entidades de los backrooms, a pantalla completa. Cada entidad tiene una animacion
 * de {@link #ANIMATED} cuadros (se acerca, tiembla, se pone roja), menos la Entidad 666, que es una sola imagen quieta.
 * Los cuadros van en el orden de {@link BackroomsKind}. Mismo esquema que {@link MemoryFrames}: cada cuadro va partido
 * en mosaicos, que son letras seguidas de la fuente {@code caichet:sustos}.
 */
final class JumpscareFrames {

    /** Cuadros de la animacion de cada entidad que se mueve. */
    static final int ANIMATED = 6;
    /** Todos los cuadros de la fuente. */
    static final int COUNT = countAll();
    static final int COLUMNS = MemoryFrames.COLUMNS;
    static final int ROWS = MemoryFrames.ROWS;
    /** La primera letra de la primera imagen; las demas siguen en orden. */
    static final int FIRST_GLYPH = 0xE100;
    /** Los mismos espacios de ancho negativo que usa la fuente de las memorias. */
    static final char NO_GAP = MemoryFrames.NO_GAP;
    static final char ROW_START = MemoryFrames.ROW_START;

    private JumpscareFrames() {
    }

    /** La letra del mosaico (fila, columna) de una imagen. */
    static char glyph(int frame, int row, int column) {
        return (char) (FIRST_GLYPH + frame * COLUMNS * ROWS + row * COLUMNS + column);
    }

    /** El texto que dibuja la imagen numero frame: sus mosaicos, fila por fila, sin huecos entre ellos. */
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

    /** Cuantos cuadros tiene el susto de esta entidad. */
    static int frames(BackroomsKind kind) {
        return kind == BackroomsKind.E666 ? 1 : ANIMATED;
    }

    /** El primer cuadro de esta entidad en la fuente. */
    static int first(BackroomsKind kind) {
        int frame = 0;
        for (BackroomsKind before : BackroomsKind.values()) {
            if (before == kind) {
                return frame;
            }
            frame += frames(before);
        }
        throw new IllegalArgumentException(kind.name());
    }

    private static int countAll() {
        int total = 0;
        for (BackroomsKind kind : BackroomsKind.values()) {
            total += frames(kind);
        }
        return total;
    }

    /**
     * El cuadro que se ve en este paso del susto: la animacion avanza un cuadro cada {@code ticksPerFrame} y, al
     * terminar, los dos ultimos se alternan (la imagen sigue temblando).
     */
    static int frameAt(BackroomsKind kind, int tick, int ticksPerFrame) {
        int count = frames(kind);
        if (count == 1) {
            return first(kind);
        }
        int step = Math.max(0, tick) / Math.max(1, ticksPerFrame);
        if (step < count) {
            return first(kind) + step;
        }
        return first(kind) + count - 1 - (step - count) % 2;
    }

    /** El texto del primer cuadro del susto de una entidad. */
    static String glyphs(BackroomsKind kind) {
        return glyphs(first(kind));
    }
}
