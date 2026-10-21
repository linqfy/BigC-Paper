package dev.linqfy.bigCasares.modules.mutango;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Donde esta el piso en mundos con techo (Nether) y cuanto relleno necesitan las ruinas. */
class MutangoGroundTest {

    /** Un Nether de juguete: piso macizo hasta y=32, aire hasta y=100 y techo macizo de ahi para arriba. */
    private static boolean nether(int y) {
        return y <= 32 || y >= 100;
    }

    @Test
    void enElNetherBuscaElPisoDebajoDeLaAlturaDeReferenciaYNoElTecho() {
        assertEquals(32, MutangoGround.floorBelow(0, 50, 6, MutangoGroundTest::nether));
        assertEquals(32, MutangoGround.floorBelow(0, 99, 6, MutangoGroundTest::nether));
    }

    @Test
    void siLaReferenciaEstaAdentroDeLaRocaSubeHastaElAireYDevuelveLaSuperficieDeEsaRoca() {
        // referencia adentro del piso: sube hasta el aire y el piso es el tope de esa roca
        assertEquals(32, MutangoGround.floorBelow(0, 30, 6, MutangoGroundTest::nether));
    }

    @Test
    void sinPisoDevuelveElMinimo() {
        assertEquals(-64, MutangoGround.floorBelow(-64, 20, 6, y -> false));
    }

    @Test
    void elRellenoEsLoQueFaltaParaTocarSuelo() {
        assertEquals(0, MutangoRuinsLayout.fillDepth(70, 0, 12, y -> y <= 70));
        assertEquals(3, MutangoRuinsLayout.fillDepth(70, 0, 12, y -> y <= 67));
        assertEquals(11, MutangoRuinsLayout.fillDepth(70, 0, 12, y -> y <= 59));
    }

    @Test
    void conUnaCuevaOUnBarrancoMuyHondoNoSeConstruye() {
        assertEquals(-1, MutangoRuinsLayout.fillDepth(70, 0, 12, y -> y <= 58));
        assertEquals(-1, MutangoRuinsLayout.fillDepth(70, 0, 12, y -> false));
    }

    @Test
    void elFondoDelMundoLimitaElRelleno() {
        // si el mundo se acaba antes del tope, es lo que hay que rellenar
        assertEquals(5, MutangoRuinsLayout.fillDepth(5, 0, 12, y -> false));
    }
}
