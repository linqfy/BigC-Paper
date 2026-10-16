package dev.linqfy.bigCasares.modules.mutango;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lo que ya ganaste y todavia no te dieron: no se pierde si el servidor se apaga. */
class MutangoPendingTest {

    @TempDir
    File folder;

    private File file() {
        return new File(folder, "data/pendientes.yml");
    }

    @Test
    void sinNadaPendienteNoHayNada() {
        MutangoPending pending = new MutangoPending(file());
        assertFalse(pending.has(UUID.randomUUID()));
        assertTrue(pending.take(UUID.randomUUID()).empty());
    }

    @Test
    void sobreviveAUnReinicioDelServidor() {
        UUID player = UUID.randomUUID();
        MutangoPending before = new MutangoPending(file());
        before.addWin(player);
        before.addReward(player);
        before.addReward(player);

        MutangoPending afterRestart = new MutangoPending(file());
        assertTrue(afterRestart.has(player));
        assertEquals(new MutangoPending.Entry(1, 2), afterRestart.take(player));
    }

    @Test
    void sePuedeCobrarUnaSolaVez() {
        UUID player = UUID.randomUUID();
        MutangoPending pending = new MutangoPending(file());
        pending.addReward(player);
        assertEquals(1, pending.take(player).rewards());
        assertTrue(pending.take(player).empty(), "el segundo cobro no da nada");
        assertTrue(new MutangoPending(file()).take(player).empty(), "ni despues de reiniciar: ya se cobro");
    }

    @Test
    void cadaJugadorTieneLoSuyo() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        MutangoPending pending = new MutangoPending(file());
        pending.addReward(first);
        pending.addWin(second);
        assertEquals(new MutangoPending.Entry(0, 1), pending.take(first));
        assertEquals(new MutangoPending.Entry(1, 0), pending.take(second));
    }

    @Test
    void unRegistroRotoNoImpideCargarLosDemas() throws Exception {
        UUID player = UUID.randomUUID();
        File file = file();
        Files.createDirectories(file.getParentFile().toPath());
        Files.writeString(file.toPath(), "pendientes:\n  no-es-un-uuid:\n    premios: 3\n  " + player
            + ":\n    victorias: 1\n    premios: 1\n");
        MutangoPending pending = new MutangoPending(file);
        assertEquals(new MutangoPending.Entry(1, 1), pending.take(player));
    }
}
