package dev.linqfy.bigCasares.modules.mutango;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MutangoDirectionsTest {

    private static final double EPS = 1e-9;

    private static void assertVector(double x, double z, Vector v) {
        assertEquals(x, v.getX(), EPS);
        assertEquals(0, v.getY(), EPS);
        assertEquals(z, v.getZ(), EPS);
    }

    @Test
    void yawCeroMiraAlSur() {
        assertVector(0, 1, MutangoDirections.forward(0));
    }

    @Test
    void comoEnMinecraft() {
        assertVector(-1, 0, MutangoDirections.forward(90));
        assertVector(0, -1, MutangoDirections.forward(180));
        assertVector(1, 0, MutangoDirections.forward(-90));
    }

    @Test
    void suIzquierdaEsPerpendicularYDeLargoUno() {
        for (float yaw = -180; yaw <= 180; yaw += 15) {
            Vector forward = MutangoDirections.forward(yaw);
            Vector left = MutangoDirections.leftOf(yaw);
            assertEquals(0, forward.dot(left), EPS, "yaw " + yaw);
            assertEquals(1, left.length(), EPS);
            assertEquals(1, forward.length(), EPS);
        }
    }

    @Test
    void mirandoAlSurSuIzquierdaQuedaAlOeste() {
        // el modelo mira a +Z: su +X (su izquierda) queda en -X del mundo, a la izquierda de quien lo mira de frente
        assertVector(-1, 0, MutangoDirections.leftOf(0));
        // girando de a 90 su izquierda va siempre un cuarto de vuelta despues que su frente
        assertVector(0, -1, MutangoDirections.leftOf(90));
    }

    @Test
    void elYawVuelveAMirarParaElMismoLado() {
        for (float yaw = -170; yaw <= 180; yaw += 10) {
            float back = MutangoDirections.yaw(MutangoDirections.forward(yaw));
            Vector again = MutangoDirections.forward(back);
            Vector expected = MutangoDirections.forward(yaw);
            assertEquals(expected.getX(), again.getX(), 1e-6, "yaw " + yaw);
            assertEquals(expected.getZ(), again.getZ(), 1e-6, "yaw " + yaw);
        }
    }

    @Test
    void elYawIgnoraLaAltura() {
        float flat = MutangoDirections.yaw(new Vector(1, 0, 1));
        float up = MutangoDirections.yaw(new Vector(1, 5, 1));
        assertEquals(flat, up, 1e-6);
    }
}
