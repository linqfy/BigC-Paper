package dev.linqfy.bigCasares.modules.mutango;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MutangoRiftShapeTest {

    private static final int HALF = MutangoRift.HALF_LENGTH;

    @Test
    void laMismaSemillaDaLaMismaGrieta() {
        assertEquals(MutangoRiftShape.shape(42L, HALF), MutangoRiftShape.shape(42L, HALF));
        boolean someDifferent = false;
        for (long seed = 0; seed < 10; seed++) {
            someDifferent |= !MutangoRiftShape.shape(seed, HALF).equals(MutangoRiftShape.shape(seed + 100, HALF));
        }
        assertTrue(someDifferent, "otra semilla, otra forma");
    }

    @Test
    void cubreTodoElLargoSinRepetirColumnas() {
        for (long seed = 0; seed < 50; seed++) {
            List<MutangoRiftShape.Cell> cells = MutangoRiftShape.shape(seed, HALF);
            Set<String> seen = new HashSet<>();
            Set<Integer> rows = new HashSet<>();
            for (MutangoRiftShape.Cell cell : cells) {
                assertTrue(seen.add(cell.along() + ";" + cell.across()), "columna repetida");
                rows.add(cell.along());
            }
            assertEquals(2 * HALF + 1, rows.size(), "una fila por cada bloque de largo");
        }
    }

    @Test
    void noSeVaDelEje() {
        for (long seed = 0; seed < 50; seed++) {
            for (MutangoRiftShape.Cell cell : MutangoRiftShape.shape(seed, HALF)) {
                assertTrue(Math.abs(cell.across()) <= 3, "a lo sumo 3 del eje: " + cell);
            }
        }
    }

    @Test
    void masAnchaYMasHondaEnElMedioQueEnLasPuntas() {
        List<MutangoRiftShape.Cell> cells = MutangoRiftShape.shape(7L, HALF);
        long middleWidth = cells.stream().filter(c -> c.along() == 0).count();
        long tipWidth = cells.stream().filter(c -> c.along() == HALF).count();
        assertEquals(5, middleWidth, "en el medio entra Neguechen (casi 4 de ancho)");
        assertEquals(1, tipWidth, "en la punta, un bloque");
        int middleDepth = cells.stream().filter(c -> c.along() == 0).mapToInt(MutangoRiftShape.Cell::depth).max()
            .orElseThrow();
        int tipDepth = cells.stream().filter(c -> c.along() == HALF).mapToInt(MutangoRiftShape.Cell::depth).max()
            .orElseThrow();
        assertTrue(middleDepth > tipDepth);
        cells.forEach(c -> assertTrue(c.depth() >= 1 && c.depth() <= MutangoRiftShape.MAX_DEPTH, c.toString()));
    }

    @Test
    void lasLucesVerdesVanEnElFondoDeLaLineaDelMedio() {
        for (long seed = 0; seed < 20; seed++) {
            List<MutangoRiftShape.Cell> cells = MutangoRiftShape.shape(seed, HALF);
            for (int along = -HALF; along <= HALF; along++) {
                final int row = along;
                List<MutangoRiftShape.Cell> lights = cells.stream().filter(c -> c.along() == row && c.light())
                    .toList();
                assertEquals(1, lights.size(), "una luz por fila");
                // la luz va en la columna mas honda de la fila
                int deepest = cells.stream().filter(c -> c.along() == row).mapToInt(MutangoRiftShape.Cell::depth)
                    .max().orElseThrow();
                assertEquals(deepest, lights.get(0).depth());
            }
        }
    }

    @Test
    void seAbreDelCentroALasPuntas() {
        List<MutangoRiftShape.Cell> cells = MutangoRiftShape.shape(3L, HALF);
        for (MutangoRiftShape.Cell cell : cells) {
            assertEquals(Math.abs(cell.along()) * MutangoRiftShape.TICKS_PER_ROW, cell.openAt());
            assertTrue(cell.openAt() < MutangoRiftShape.duration(HALF), "se abre antes de terminar");
        }
        assertEquals(0, cells.stream().filter(c -> c.along() == 0).findFirst().orElseThrow().openAt());
        assertNotEquals(0, cells.stream().filter(c -> c.along() == HALF).findFirst().orElseThrow().openAt());
    }

    @Test
    void sinLargoNoHayGrieta() {
        assertThrows(IllegalArgumentException.class, () -> MutangoRiftShape.shape(1L, 0));
    }
}
