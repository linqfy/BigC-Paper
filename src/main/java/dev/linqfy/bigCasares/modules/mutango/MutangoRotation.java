package dev.linqfy.bigCasares.modules.mutango;

/**
 * Giro de una estructura de width x length columnas en cuartos de vuelta (0-3, horario), sin Bukkit: de una columna
 * de la plantilla a su lugar en el mundo (relativo a la esquina noroeste ya girada) y al reves.
 */
final class MutangoRotation {

    private MutangoRotation() {
    }

    /** Desplazamiento en el mundo de la columna (tx, tz) de la plantilla segun el giro. */
    static int[] toWorld(int rotation, int width, int length, int tx, int tz) {
        return switch (rotation) {
            case 1 -> new int[]{length - 1 - tz, tx};
            case 2 -> new int[]{width - 1 - tx, length - 1 - tz};
            case 3 -> new int[]{tz, width - 1 - tx};
            default -> new int[]{tx, tz};
        };
    }

    /** Al reves que toWorld: de un lugar en el mundo (relativo a la esquina) a la columna de la plantilla. */
    static int[] toTemplate(int rotation, int width, int length, int dx, int dz) {
        return switch (rotation) {
            case 1 -> new int[]{dz, length - 1 - dx};
            case 2 -> new int[]{width - 1 - dx, length - 1 - dz};
            case 3 -> new int[]{width - 1 - dz, dx};
            default -> new int[]{dx, dz};
        };
    }

    /** Medidas en el mundo (ancho en X, largo en Z) de la estructura girada. */
    static int[] size(int rotation, int width, int length) {
        return rotation % 2 == 0 ? new int[]{width, length} : new int[]{length, width};
    }
}
