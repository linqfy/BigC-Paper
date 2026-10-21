package dev.linqfy.bigCasares.modules.mutango;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Ayuda para buscar el lugar permitido mas cercano cuando donde iba a pasar algo (la grieta, las manos) esta protegido
 * (sin Bukkit). Quien pregunta si un bloque esta protegido es MutangoPhaseThree (dispara el evento de cambio de bloque
 * que escuchan los plugins de proteccion); esto solo da los lugares a probar, del mas cercano al mas lejano.
 */
final class MutangoProtection {

    private MutangoProtection() {
    }

    /**
     * Todos los corrimientos (x, z) de hasta radius bloques en cada eje, del mas cercano al mas lejano: primero (0, 0)
     * (donde iba a ser), despues los de alrededor, y asi.
     */
    static List<int[]> offsetsByDistance(int radius) {
        List<int[]> offsets = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                offsets.add(new int[] {x, z});
            }
        }
        // a igual distancia, siempre el mismo orden (asi el resultado no cambia de una vez a otra)
        offsets.sort(Comparator.<int[]>comparingInt(o -> o[0] * o[0] + o[1] * o[1])
            .thenComparingInt(o -> o[0]).thenComparingInt(o -> o[1]));
        return offsets;
    }
}
