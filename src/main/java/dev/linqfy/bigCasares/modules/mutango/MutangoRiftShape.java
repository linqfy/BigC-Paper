package dev.linqfy.bigCasares.modules.mutango;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * La forma de la grieta por donde aparece y se va Neguechen, sin Bukkit: una raja larga, con la linea del
 * medio que zigzaguea, mas ancha y mas honda en el centro y que se cierra en punta en los dos extremos. Se abre del
 * centro hacia las puntas (y se cierra al reves). En el fondo, sobre la linea del medio, van las luces verdes.
 * <p>
 * Cada celda esta en el sistema de la grieta: along a lo largo (de -halfLength a halfLength) y across a lo ancho.
 */
final class MutangoRiftShape {

    /** Ticks entre que se abre una fila y la siguiente (hacia las puntas). */
    static final int TICKS_PER_ROW = 2;
    /** Hondo en las puntas y en el medio. */
    static final int MIN_DEPTH = 2;
    static final int MAX_DEPTH = 5;

    /**
     * Una columna de la grieta: cuantos bloques se sacan para abajo (depth), si en el fondo va una luz y en que tick
     * se abre (contando desde que empieza a abrirse).
     */
    record Cell(int along, int across, int depth, boolean light, int openAt) {
    }

    private MutangoRiftShape() {
    }

    /** La grieta de largo 2*halfLength+1. La misma semilla da siempre la misma grieta. */
    static List<Cell> shape(long seed, int halfLength) {
        if (halfLength < 1) {
            throw new IllegalArgumentException("la grieta tiene que tener largo");
        }
        Random random = new Random(seed);
        List<Cell> cells = new ArrayList<>();
        int middle = 0;
        // la linea del medio arranca en el centro y zigzaguea hacia cada punta (sin irse mas de 1 del eje)
        int[] center = new int[2 * halfLength + 1];
        for (int side : new int[] {1, -1}) {
            middle = 0;
            for (int step = 1; step <= halfLength; step++) {
                middle = Math.max(-1, Math.min(1, middle + random.nextInt(3) - 1));
                center[halfLength + side * step] = middle;
            }
        }
        for (int along = -halfLength; along <= halfLength; along++) {
            double closeness = 1.0 - Math.abs(along) / (double) (halfLength + 1);
            // 5 de ancho en el medio (Neguechen mide casi 4 de hombro a hombro), 3 despues y 1 en las puntas
            int halfWidth = closeness > 0.75 ? 2 : closeness > 0.45 ? 1 : 0;
            int depth = MIN_DEPTH + (int) Math.round((MAX_DEPTH - MIN_DEPTH) * closeness * closeness);
            int openAt = Math.abs(along) * TICKS_PER_ROW;
            int line = center[halfLength + along];
            for (int across = line - halfWidth; across <= line + halfWidth; across++) {
                // en el medio de la grieta, las luces del fondo; en los bordes, un poco menos hondo
                boolean onLine = across == line;
                cells.add(new Cell(along, across, Math.max(1, depth - Math.abs(across - line)), onLine, openAt));
            }
        }
        return cells;
    }

    /** Cuantos ticks tarda en abrirse entera (o en cerrarse). */
    static int duration(int halfLength) {
        return halfLength * TICKS_PER_ROW + 1;
    }
}
