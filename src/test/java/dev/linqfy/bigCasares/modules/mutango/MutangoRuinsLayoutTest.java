package dev.linqfy.bigCasares.modules.mutango;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MutangoRuinsLayoutTest {

    private static final int SPACING = 800;
    private static final int WIDTH = 42;
    private static final int LENGTH = 52;

    private static MutangoRuinsLayout layout(long seed, double chance, int minDistance) {
        return new MutangoRuinsLayout(seed, SPACING, chance, minDistance, WIDTH, LENGTH);
    }

    @Test
    void laMismaSemillaDaLasMismasRuinas() {
        MutangoRuinsLayout a = layout(1234L, 0.7, 250);
        MutangoRuinsLayout b = layout(1234L, 0.7, 250);
        for (int cx = -5; cx <= 5; cx++) {
            for (int cz = -5; cz <= 5; cz++) {
                assertEquals(a.inCell(cx, cz), b.inCell(cx, cz));
            }
        }
    }

    @Test
    void otraSemillaLasPoneEnOtrosLugares() {
        MutangoRuinsLayout a = layout(1L, 1.0, 0);
        MutangoRuinsLayout b = layout(2L, 1.0, 0);
        int distintas = 0;
        for (int cx = 1; cx <= 10; cx++) {
            if (!a.inCell(cx, cx).equals(b.inCell(cx, cx))) {
                distintas++;
            }
        }
        assertTrue(distintas > 5, "con otra semilla casi todas tienen que cambiar");
    }

    @Test
    void conProbabilidadCeroNoHayNinguna() {
        MutangoRuinsLayout none = layout(99L, 0.0, 0);
        for (int cx = -5; cx <= 5; cx++) {
            for (int cz = -5; cz <= 5; cz++) {
                assertTrue(none.inCell(cx, cz).isEmpty());
            }
        }
    }

    @Test
    void conProbabilidadUnoHayEnTodasLasCeldasLejosDelSpawn() {
        MutangoRuinsLayout all = layout(99L, 1.0, 0);
        for (int cx = -5; cx <= 5; cx++) {
            for (int cz = -5; cz <= 5; cz++) {
                assertTrue(all.inCell(cx, cz).isPresent());
            }
        }
    }

    @Test
    void laProbabilidadSeRespetaMasOMenos() {
        MutangoRuinsLayout some = layout(7L, 0.7, 0);
        int con = 0;
        int total = 0;
        for (int cx = -20; cx < 20; cx++) {
            for (int cz = -20; cz < 20; cz++) {
                total++;
                if (some.inCell(cx, cz).isPresent()) {
                    con++;
                }
            }
        }
        double parte = con / (double) total;
        assertTrue(parte > 0.6 && parte < 0.8, "salio " + parte);
    }

    @Test
    void cadaRuinaQuedaAdentroDeSuCeldaConMargen() {
        MutangoRuinsLayout all = layout(42L, 1.0, 0);
        for (int cx = -6; cx <= 6; cx++) {
            for (int cz = -6; cz <= 6; cz++) {
                MutangoRuinsLayout.Plan plan = all.inCell(cx, cz).orElseThrow();
                assertTrue(plan.x() >= cx * SPACING + 16, "se sale por el oeste");
                assertTrue(plan.x() + plan.sizeX() <= (cx + 1) * SPACING - 16, "se sale por el este");
                assertTrue(plan.z() >= cz * SPACING + 16, "se sale por el norte");
                assertTrue(plan.z() + plan.sizeZ() <= (cz + 1) * SPACING - 16, "se sale por el sur");
            }
        }
    }

    @Test
    void elGiroDecideLasMedidas() {
        MutangoRuinsLayout all = layout(5L, 1.0, 0);
        for (int cx = 0; cx < 30; cx++) {
            MutangoRuinsLayout.Plan plan = all.inCell(cx, 3).orElseThrow();
            if (plan.rotation() % 2 == 0) {
                assertEquals(WIDTH, plan.sizeX());
                assertEquals(LENGTH, plan.sizeZ());
            } else {
                assertEquals(LENGTH, plan.sizeX());
                assertEquals(WIDTH, plan.sizeZ());
            }
        }
    }

    @Test
    void nuncaCercaDelSpawn() {
        MutangoRuinsLayout far = layout(3L, 1.0, 2000);
        for (int cx = -3; cx <= 2; cx++) {
            for (int cz = -3; cz <= 2; cz++) {
                Optional<MutangoRuinsLayout.Plan> plan = far.inCell(cx, cz);
                plan.ifPresent(p -> assertTrue(Math.hypot(p.centerX(), p.centerZ()) >= 2000));
            }
        }
        assertTrue(far.inCell(0, 0).isEmpty());
    }

    @Test
    void seConstruyeEnSuChunkAncla() {
        MutangoRuinsLayout all = layout(11L, 1.0, 0);
        MutangoRuinsLayout.Plan plan = all.inCell(4, -2).orElseThrow();
        assertEquals(Optional.of(plan), all.anchoredAt(plan.anchorChunkX(), plan.anchorChunkZ()));
        assertTrue(all.anchoredAt(plan.anchorChunkX() + 1, plan.anchorChunkZ()).isEmpty());
        assertTrue(all.anchoredAt(plan.anchorChunkX(), plan.anchorChunkZ() - 1).isEmpty());
    }

    @Test
    void laMasCercanaEsLaDeLaCeldaDondeEstas() {
        MutangoRuinsLayout all = layout(8L, 1.0, 0);
        MutangoRuinsLayout.Plan plan = all.inCell(2, 2).orElseThrow();
        assertEquals(Optional.of(plan), all.nearest(plan.centerX(), plan.centerZ(), 2));
        assertFalse(all.nearest(plan.centerX(), plan.centerZ(), 0).isEmpty());
    }

    @Test
    void cadaCeldaTieneSuClave() {
        MutangoRuinsLayout all = layout(8L, 1.0, 0);
        assertEquals("3;-4", all.inCell(3, -4).orElseThrow().key());
        assertNotEquals(all.inCell(3, -4).orElseThrow().key(), all.inCell(-4, 3).orElseThrow().key());
    }

    @Test
    void laSeparacionTieneQueEntrarLaRuina() {
        assertThrows(IllegalArgumentException.class, () -> new MutangoRuinsLayout(1L, 60, 1.0, 0, WIDTH, LENGTH));
    }
}
