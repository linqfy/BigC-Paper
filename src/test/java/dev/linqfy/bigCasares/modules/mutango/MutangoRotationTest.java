package dev.linqfy.bigCasares.modules.mutango;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MutangoRotationTest {

    private static final int WIDTH = 42;
    private static final int LENGTH = 52;

    @Test
    void sinGiroQuedaIgual() {
        assertArrayEquals(new int[]{5, 7}, MutangoRotation.toWorld(0, WIDTH, LENGTH, 5, 7));
        assertArrayEquals(new int[]{WIDTH, LENGTH}, MutangoRotation.size(0, WIDTH, LENGTH));
    }

    @Test
    void losGirosImparesCambianAnchoPorLargo() {
        assertArrayEquals(new int[]{LENGTH, WIDTH}, MutangoRotation.size(1, WIDTH, LENGTH));
        assertArrayEquals(new int[]{WIDTH, LENGTH}, MutangoRotation.size(2, WIDTH, LENGTH));
        assertArrayEquals(new int[]{LENGTH, WIDTH}, MutangoRotation.size(3, WIDTH, LENGTH));
    }

    @Test
    void cadaColumnaCaeAdentroDeLaEstructuraGirada() {
        for (int rotation = 0; rotation < 4; rotation++) {
            int[] size = MutangoRotation.size(rotation, WIDTH, LENGTH);
            for (int tx = 0; tx < WIDTH; tx++) {
                for (int tz = 0; tz < LENGTH; tz++) {
                    int[] world = MutangoRotation.toWorld(rotation, WIDTH, LENGTH, tx, tz);
                    assertTrue(world[0] >= 0 && world[0] < size[0], "x fuera en giro " + rotation);
                    assertTrue(world[1] >= 0 && world[1] < size[1], "z fuera en giro " + rotation);
                }
            }
        }
    }

    @Test
    void ningunaColumnaSePisaConOtra() {
        for (int rotation = 0; rotation < 4; rotation++) {
            Set<String> used = new HashSet<>();
            for (int tx = 0; tx < WIDTH; tx++) {
                for (int tz = 0; tz < LENGTH; tz++) {
                    int[] world = MutangoRotation.toWorld(rotation, WIDTH, LENGTH, tx, tz);
                    assertTrue(used.add(world[0] + "," + world[1]), "columna repetida en giro " + rotation);
                }
            }
            assertEquals(WIDTH * LENGTH, used.size());
        }
    }

    @Test
    void volverALaPlantillaDaLaMismaColumna() {
        for (int rotation = 0; rotation < 4; rotation++) {
            for (int tx = 0; tx < WIDTH; tx++) {
                for (int tz = 0; tz < LENGTH; tz++) {
                    int[] world = MutangoRotation.toWorld(rotation, WIDTH, LENGTH, tx, tz);
                    assertArrayEquals(new int[]{tx, tz},
                        MutangoRotation.toTemplate(rotation, WIDTH, LENGTH, world[0], world[1]),
                        "giro " + rotation + " columna " + tx + "," + tz);
                }
            }
        }
    }

    @Test
    void unCuartoDeVueltaEsHorario() {
        // la esquina noroeste de la plantilla (0, 0) queda en el noreste: x al maximo, z en 0
        assertArrayEquals(new int[]{LENGTH - 1, 0}, MutangoRotation.toWorld(1, WIDTH, LENGTH, 0, 0));
        // y +X de la plantilla pasa a ser +Z en el mundo
        int[] origin = MutangoRotation.toWorld(1, WIDTH, LENGTH, 0, 0);
        int[] plusX = MutangoRotation.toWorld(1, WIDTH, LENGTH, 1, 0);
        assertArrayEquals(new int[]{0, 1}, new int[]{plusX[0] - origin[0], plusX[1] - origin[1]});
    }
}
