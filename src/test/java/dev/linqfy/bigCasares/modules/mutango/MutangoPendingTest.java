package dev.linqfy.bigCasares.modules.mutango;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La cola de adjudicaciones: lo que ya ganaste y todavia no esta confirmado no se pierde si el servidor se apaga. */
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
        assertTrue(pending.of(UUID.randomUUID()).isEmpty());
    }

    @Test
    void laAdjudicacionYaEstaEnElArchivoAntesDeQueSeToqueAlJugador() {
        UUID player = UUID.randomUUID();
        MutangoPending.Award award = new MutangoPending(file()).add(player, 1, 1);
        // sin confirmar nada, un "reinicio" ya la ve
        List<MutangoPending.Award> reloaded = new MutangoPending(file()).of(player);
        assertEquals(List.of(award), reloaded);
    }

    @Test
    void sobreviveAUnReinicioHastaQueSeConfirma() {
        UUID player = UUID.randomUUID();
        MutangoPending before = new MutangoPending(file());
        MutangoPending.Award win = before.add(player, 1, 0);
        MutangoPending.Award reward = before.add(player, 0, 1);

        MutangoPending afterRestart = new MutangoPending(file());
        assertEquals(List.of(win, reward), afterRestart.of(player));

        afterRestart.confirm(win.id());
        assertEquals(List.of(reward), new MutangoPending(file()).of(player));
    }

    @Test
    void confirmarDosVecesNoRompeNada() {
        UUID player = UUID.randomUUID();
        MutangoPending pending = new MutangoPending(file());
        MutangoPending.Award award = pending.add(player, 0, 1);
        pending.confirm(award.id());
        pending.confirm(award.id());
        assertFalse(new MutangoPending(file()).has(player));
    }

    @Test
    void cadaJugadorTieneLoSuyo() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        MutangoPending pending = new MutangoPending(file());
        pending.add(first, 0, 1);
        pending.add(second, 1, 0);
        assertEquals(1, pending.of(first).size());
        assertEquals(0, pending.of(first).get(0).wins());
        assertEquals(1, pending.of(second).get(0).wins());
    }

    @Test
    void elArchivoNuevoLlevaLaVersion() {
        new MutangoPending(file()).add(UUID.randomUUID(), 1, 1);
        assertEquals(MutangoPending.VERSION, YamlConfiguration.loadConfiguration(file()).getInt("version"));
    }

    @Test
    void unRegistroRotoNoImpideCargarLosDemas() throws Exception {
        UUID player = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        File file = file();
        Files.createDirectories(file.getParentFile().toPath());
        Files.writeString(file.toPath(), "version: 2\nadjudicaciones:\n  - id: no-es-un-uuid\n    jugador: " + player
            + "\n    premios: 3\n  - id: " + id + "\n    jugador: " + player + "\n    victorias: 1\n    premios: 1\n");
        MutangoPending pending = new MutangoPending(file);
        assertEquals(List.of(new MutangoPending.Award(id, player, 1, 1)), pending.of(player));
    }

    @Test
    void elFormatoViejoDeCuentasPorJugadorSeMigraUnaSolaVez() throws Exception {
        UUID player = UUID.randomUUID();
        File file = file();
        Files.createDirectories(file.getParentFile().toPath());
        Files.writeString(file.toPath(), "pendientes:\n  no-es-un-uuid:\n    premios: 3\n  " + player
            + ":\n    victorias: 1\n    premios: 2\n");
        MutangoPending first = new MutangoPending(file);
        assertEquals(1, first.of(player).size());
        MutangoPending.Award migrated = first.of(player).get(0);
        assertEquals(1, migrated.wins());
        assertEquals(2, migrated.rewards());
        // leerlo otra vez antes de guardar nada da la misma adjudicacion (mismo id): no se duplica
        assertEquals(migrated, new MutangoPending(file).of(player).get(0));
        // y una vez guardado en el formato nuevo tampoco
        first.add(UUID.randomUUID(), 0, 1);
        assertEquals(List.of(migrated), new MutangoPending(file).of(player));
    }
}
