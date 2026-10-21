package dev.linqfy.bigCasares.modules.mutango;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MutangoBootsJumpTest {

    @Test
    void enElAireSePuedeSaltarUnaSolaVez() {
        MutangoBootsJump jump = new MutangoBootsJump();
        assertTrue(jump.canDoubleJump());
        assertTrue(jump.doubleJump());
        assertFalse(jump.canDoubleJump());
        assertFalse(jump.doubleJump(), "un tercer salto no");
    }

    @Test
    void alTocarElPisoSeRecarga() {
        MutangoBootsJump jump = new MutangoBootsJump();
        jump.doubleJump();
        assertTrue(jump.land(), "venia de un doble salto");
        assertTrue(jump.canDoubleJump());
        assertFalse(jump.land(), "caminando no hubo doble salto");
    }

    @Test
    void despuesDeAterrizarSePuedeSaltarOtraVez() {
        MutangoBootsJump jump = new MutangoBootsJump();
        jump.doubleJump();
        jump.land();
        assertTrue(jump.doubleJump());
    }

    @Test
    void elSaltoEsMasAltoQueUnoComun() {
        // un salto comun sale a 0.42
        assertTrue(MutangoBootsJump.JUMP_UP > 0.42);
        assertTrue(MutangoBootsJump.JUMP_FORWARD > 0);
    }
}
