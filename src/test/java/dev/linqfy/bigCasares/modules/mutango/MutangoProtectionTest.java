package dev.linqfy.bigCasares.modules.mutango;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MutangoProtectionTest {

    @Test
    void primeroSeProbaDondeIbaASer() {
        assertArrayEquals(new int[] {0, 0}, MutangoProtection.offsetsByDistance(5).get(0));
    }

    @Test
    void despuesLosMasCercanosPrimero() {
        List<int[]> offsets = MutangoProtection.offsetsByDistance(6);
        int last = -1;
        for (int[] offset : offsets) {
            int distance = offset[0] * offset[0] + offset[1] * offset[1];
            assertTrue(distance >= last, "no esta ordenado por distancia");
            last = distance;
        }
    }

    @Test
    void estanTodosLosLugaresDelCuadradoUnaVezCadaUno() {
        List<int[]> offsets = MutangoProtection.offsetsByDistance(4);
        assertEquals(81, offsets.size());
        assertEquals(81, offsets.stream().map(o -> o[0] + "," + o[1]).distinct().count());
    }

    @Test
    void elOrdenEsSiempreElMismo() {
        List<int[]> first = MutangoProtection.offsetsByDistance(3);
        List<int[]> second = MutangoProtection.offsetsByDistance(3);
        for (int i = 0; i < first.size(); i++) {
            assertArrayEquals(first.get(i), second.get(i));
        }
    }
}
