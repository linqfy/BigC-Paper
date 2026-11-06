package dev.linqfy.bigCasares.modules.mutango;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cuando conviene el guardado con flush que da evidencia para tachar los bloques devueltos. */
class MutangoSavePolicyTest {

    private final UUID world = UUID.randomUUID();

    @Test
    void sinBloquesEsperandoNoHayNadaQueGuardar() {
        MutangoSavePolicy policy = new MutangoSavePolicy(1000, 5000);
        assertFalse(policy.due(world, false, 0));
        assertFalse(policy.due(world, false, 1_000_000));
    }

    @Test
    void esperaUnTiempoDesdeElPrimerBloqueDevuelto() {
        MutangoSavePolicy policy = new MutangoSavePolicy(1000, 5000);
        assertFalse(policy.due(world, true, 100)); // aparece el primero
        assertFalse(policy.due(world, true, 1099));
        assertTrue(policy.due(world, true, 1100));
    }

    @Test
    void noSeRepiteAntesDelIntervaloMinimo() {
        MutangoSavePolicy policy = new MutangoSavePolicy(1000, 5000);
        policy.due(world, true, 0);
        assertTrue(policy.due(world, true, 1000));
        policy.saved(world, 1000);

        assertFalse(policy.due(world, true, 1500), "recien guardo");
        assertFalse(policy.due(world, true, 3000), "juntando lo nuevo: pasa la espera pero no el intervalo");
        assertFalse(policy.due(world, true, 5999));
        assertTrue(policy.due(world, true, 6000));
    }

    @Test
    void cadaMundoTieneLoSuyo() {
        MutangoSavePolicy policy = new MutangoSavePolicy(1000, 5000);
        UUID other = UUID.randomUUID();
        policy.due(world, true, 0);
        policy.due(other, true, 900);
        assertTrue(policy.due(world, true, 1000));
        assertFalse(policy.due(other, true, 1000));
    }
}
