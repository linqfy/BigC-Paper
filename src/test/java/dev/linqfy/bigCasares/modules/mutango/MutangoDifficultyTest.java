package dev.linqfy.bigCasares.modules.mutango;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La dificultad sube logaritmicamente: +50% la primera vez y despues cada vez menos (de la pelea 8 casi no cambia). */
class MutangoDifficultyTest {

    @Test
    void laPrimeraVezEsComoSiempre() {
        MutangoDifficulty normal = new MutangoDifficulty(0);
        assertEquals(1.0, normal.factor());
        assertEquals(10.0, normal.damage(10.0));
        assertEquals(100, normal.cooldownTicks(100));
        assertEquals(3, normal.count(3), "exactamente 3 vacas");
        assertEquals(0.25, normal.speed(0.25));
    }

    @Test
    void laPrimeraVictoriaSumaUnCincuentaPorCiento() {
        assertEquals(1.5, new MutangoDifficulty(1).factor(), 1e-9);
        assertEquals(15.0, new MutangoDifficulty(1).damage(10.0), 1e-9);
    }

    @Test
    void cadaVezQueSeDuplicanLasVictoriasMasUnoSumaOtroCincuentaPorCiento() {
        assertEquals(2.0, new MutangoDifficulty(3).factor(), 1e-9);
        assertEquals(2.5, new MutangoDifficulty(7).factor(), 1e-9);
        assertEquals(3.0, new MutangoDifficulty(15).factor(), 1e-9);
        assertEquals(20.0, new MutangoDifficulty(3).damage(10.0), 1e-9);
    }

    @Test
    void sigueSubiendoPeroCadaVezMasDespacio() {
        double previousStep = Double.MAX_VALUE;
        for (int level = 0; level < 40; level++) {
            double step = new MutangoDifficulty(level + 1).factor() - new MutangoDifficulty(level).factor();
            assertTrue(step > 0, "nunca baja");
            assertTrue(step < previousStep, "cada aumento es menor que el anterior (nivel " + level + ")");
            previousStep = step;
        }
    }

    @Test
    void desdeLaPeleaOchoYaCasiNoCambia() {
        // la pelea 8 es la de 7 victorias previas: la 9 sube menos del 4% sobre la 8, y de ahi para adelante menos todavia
        MutangoDifficulty eighth = new MutangoDifficulty(7);
        MutangoDifficulty ninth = new MutangoDifficulty(8);
        assertTrue(ninth.factor() / eighth.factor() < 1.04, "de la 8 a la 9 sube menos del 4%");
        assertTrue(new MutangoDifficulty(30).factor() / eighth.factor() < 1.4, "en la pelea 31 es menos de 1.4 veces la 8");
        assertTrue(new MutangoDifficulty(7).factor() / new MutangoDifficulty(1).factor() < 1.7);
    }

    @Test
    void atacaMasSeguidoPeroNuncaMasDeUnaVezPorSegundo() {
        assertEquals(67, new MutangoDifficulty(1).cooldownTicks(100));
        assertEquals(56, new MutangoDifficulty(2).cooldownTicks(100));
        assertEquals(20, new MutangoDifficulty(1000).cooldownTicks(100));
        // lo que ya era mas seguido que un segundo no se alarga
        assertEquals(12, new MutangoDifficulty(0).cooldownTicks(12));
        assertEquals(12, new MutangoDifficulty(5).cooldownTicks(12));
    }

    @Test
    void masAnimalesQueNoPasanDelDobleDeRapido() {
        assertEquals(5, new MutangoDifficulty(1).count(3));
        assertEquals(9, new MutangoDifficulty(2).count(5));
        assertEquals(0.375, new MutangoDifficulty(1).speed(0.25), 1e-9);
        assertEquals(0.5, new MutangoDifficulty(10).speed(0.25), 1e-9);
    }

    @Test
    void tambienTienenMasVidaHastaLoQueDejaMinecraft() {
        assertEquals(100.0, new MutangoDifficulty(0).health(100.0), "Lef, Lihuen y Lican: 100 la primera vez");
        assertEquals(150.0, new MutangoDifficulty(1).health(100.0));
        assertEquals(600.0, new MutangoDifficulty(3).health(300.0), 1e-9, "Neguechen");
        assertEquals(1024.0, new MutangoDifficulty(1000).health(300.0), "Minecraft no deja mas de 1024");
        assertEquals(15.0, new MutangoDifficulty(1).health(10.0), "una vaca");
    }

    @Test
    void nuncaMenosQueLaPrimeraVez() {
        assertEquals(0, new MutangoDifficulty(-3).level());
    }

    @Test
    void cadaPeleaTieneSuPropiaDificultad() {
        MutangoDifficulty first = new MutangoDifficulty(0);
        MutangoDifficulty second = new MutangoDifficulty(3);
        assertEquals(1.0, first.factor());
        assertEquals(2.0, second.factor(), 1e-9);
        assertEquals(10.0, first.damage(10.0), "una pelea nueva no cambia a la que ya estaba andando");
        assertEquals(20.0, second.damage(10.0), 1e-9);
    }

    @Test
    void elMensajeDiceCuantoMasFuerteYQueArmadura() {
        assertTrue(MutangoDifficulty.summonMessage(0).contains("nunca"));
        String twice = MutangoDifficulty.summonMessage(2);
        assertTrue(twice.contains("2") && twice.contains("veces") && twice.contains("mas fuertes"), twice);
        assertFalse(twice.contains("%"), "no dice el porcentaje: " + twice);
        assertTrue(twice.contains("hierro"), twice);
        assertTrue(MutangoDifficulty.summonMessage(1).contains(" vez "), MutangoDifficulty.summonMessage(1));
    }
}
