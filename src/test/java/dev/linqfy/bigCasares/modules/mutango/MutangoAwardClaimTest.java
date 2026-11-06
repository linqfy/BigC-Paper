package dev.linqfy.bigCasares.modules.mutango;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La entrega de un premio: un corte entre cualquier par de pasos no pierde ni duplica nada, y un guardado que falla EN
 * SILENCIO (Player#saveData no lanza) se detecta al volver a entrar el jugador. El jugador guarda sus datos (victorias,
 * items, recibos) todos juntos al hacer persist(); un corte antes de guardar los deja como estaban.
 */
class MutangoAwardClaimTest {

    @TempDir
    File folder;

    private final UUID player = UUID.randomUUID();

    /** Un jugador de mentira con dos estados: el de memoria (se pierde en un corte) y el guardado. */
    private static final class FakePlayer implements MutangoAwardClaim.Receipts, MutangoAwardClaim.Target {
        int wins;
        int rewards;
        final Set<UUID> receipts = new HashSet<>();
        int savedWins;
        int savedRewards;
        final Set<UUID> savedReceipts = new HashSet<>();
        int freeSlots = 36;
        /** En que paso se corta (el nombre del paso), o null. */
        String crashAt;
        /** persist() vuelve normal pero no guarda nada (como Player#saveData cuando falla la escritura). */
        boolean silentSaveFailure;

        void crashPoint(String step) {
            if (step.equals(crashAt)) {
                throw new IllegalStateException("corte en " + step);
            }
        }

        /** El servidor se reinicia: de memoria queda solo lo que se guardo. */
        void restart() {
            wins = savedWins;
            rewards = savedRewards;
            receipts.clear();
            receipts.addAll(savedReceipts);
            crashAt = null;
            silentSaveFailure = false;
        }

        @Override
        public boolean has(UUID award) {
            return receipts.contains(award);
        }

        @Override
        public void add(UUID award) {
            crashPoint("receipt");
            receipts.add(award);
        }

        @Override
        public void remove(UUID award) {
            receipts.remove(award);
        }

        private int winsBefore;
        private int rewardsBefore;
        private int slotsBefore;

        @Override
        public void begin() {
            winsBefore = wins;
            rewardsBefore = rewards;
            slotsBefore = freeSlots;
        }

        @Override
        public void rollback() {
            wins = winsBefore;
            rewards = rewardsBefore;
            freeSlots = slotsBefore;
        }

        @Override
        public boolean hasRoom(int sets) {
            return freeSlots >= 2 * sets;
        }

        @Override
        public void addWins(int amount) {
            crashPoint("wins");
            wins += amount;
        }

        @Override
        public void giveRewards(int sets) {
            crashPoint("items");
            rewards += sets;
            freeSlots -= 2 * sets;
        }

        @Override
        public void persist() {
            crashPoint("persist");
            if (silentSaveFailure) {
                return;
            }
            savedWins = wins;
            savedRewards = rewards;
            savedReceipts.clear();
            savedReceipts.addAll(receipts);
        }
    }

    private File file() {
        return new File(folder, "pendientes.yml");
    }

    private MutangoPending.Award add(MutangoPending queue, int wins, int rewards) {
        return queue.add(UUID.randomUUID(), player, wins, rewards);
    }

    /** Lo que hace MutangoRewards al entrar el jugador: con sus datos leidos del disco, confirma o vuelve a pendiente. */
    private void verifyAtJoin(MutangoPending queue, FakePlayer fake) {
        for (MutangoPending.Award award : queue.appliedOf(player)) {
            if (MutangoAwardClaim.verifyAtJoin(award, fake) == MutangoAwardClaim.Verdict.CONFIRM) {
                queue.remove(award.id());
            } else {
                queue.markPending(award.id());
            }
        }
    }

    private void claimAll(MutangoPending queue, FakePlayer fake) {
        for (MutangoPending.Award award : queue.pendingOf(player)) {
            MutangoAwardClaim.claim(award, fake, fake, queue::markApplied);
        }
    }

    @Test
    void aNormalClaimAppliesEverythingOnceAndLeavesItAppliedUntilTheDiskProvesIt() {
        MutangoPending queue = new MutangoPending(file());
        MutangoPending.Award award = add(queue, 1, 1);
        FakePlayer fake = new FakePlayer();

        assertEquals(MutangoAwardClaim.Result.CLAIMED,
            MutangoAwardClaim.claim(award, fake, fake, queue::markApplied));

        assertEquals(1, fake.savedWins);
        assertEquals(1, fake.savedRewards);
        assertTrue(fake.savedReceipts.contains(award.id()));
        assertTrue(queue.pendingOf(player).isEmpty());
        assertEquals(1, new MutangoPending(file()).appliedOf(player).size(), "sigue en la cola, aplicada");
    }

    @Test
    void aCutAtAnyStepNeverLosesNorDuplicatesTheAward() {
        for (String step : new String[] {"wins", "items", "receipt", "persist"}) {
            MutangoPending queue = new MutangoPending(new File(folder, "cola-" + step + ".yml"));
            MutangoPending.Award award = add(queue, 1, 1);
            FakePlayer fake = new FakePlayer();
            fake.crashAt = step;

            assertThrows(IllegalStateException.class,
                () -> MutangoAwardClaim.claim(award, fake, fake, queue::markApplied), step);

            // el servidor se cae y vuelve: la cola sale del archivo y el jugador de lo que guardo
            fake.restart();
            MutangoPending afterRestart = new MutangoPending(new File(folder, "cola-" + step + ".yml"));
            verifyAtJoin(afterRestart, fake);
            claimAll(afterRestart, fake);
            verifyAtJoin(afterRestart, fake); // y vuelve a entrar: el recibo ya esta guardado

            assertEquals(1, fake.savedWins, "una victoria, ni mas ni menos (corte en " + step + ")");
            assertEquals(1, fake.savedRewards, "un juego de premios (corte en " + step + ")");
            assertTrue(new MutangoPending(new File(folder, "cola-" + step + ".yml")).of(player).isEmpty(),
                "la cola queda vacia cuando el disco lo prueba (corte en " + step + ")");
        }
    }

    @Test
    void aSilentSaveFailureIsDetectedWhenThePlayerComesBackAndTheAwardIsAppliedAgain() {
        MutangoPending queue = new MutangoPending(file());
        MutangoPending.Award award = add(queue, 1, 1);
        FakePlayer fake = new FakePlayer();
        fake.silentSaveFailure = true; // saveData() capturo la excepcion de escritura y volvio normal

        // desde el punto de vista del codigo, todo salio bien: no hay excepcion, el rollback no corre
        assertEquals(MutangoAwardClaim.Result.CLAIMED, MutangoAwardClaim.claim(award, fake, fake, queue::markApplied));
        assertEquals(0, fake.savedWins, "pero el disco no tiene nada");
        assertEquals(1, new MutangoPending(file()).appliedOf(player).size(), "la cola NO la borro");

        // el servidor reinicia, el jugador entra y sus datos vienen del disco (sin premio ni recibo)
        fake.restart();
        MutangoPending afterRestart = new MutangoPending(file());
        verifyAtJoin(afterRestart, fake);
        assertEquals(1, afterRestart.pendingOf(player).size(), "vuelve a estar por aplicar");
        claimAll(afterRestart, fake);

        assertEquals(1, fake.savedWins);
        assertEquals(1, fake.savedRewards);
        verifyAtJoin(afterRestart, fake);
        assertTrue(afterRestart.of(player).isEmpty());
    }

    @Test
    void whenTheDiskDoesHaveTheReceiptTheAwardIsConfirmedAndNeverAppliedAgain() {
        MutangoPending queue = new MutangoPending(file());
        MutangoPending.Award award = add(queue, 1, 1);
        FakePlayer fake = new FakePlayer();
        MutangoAwardClaim.claim(award, fake, fake, queue::markApplied);

        fake.restart();
        verifyAtJoin(queue, fake);

        assertTrue(queue.of(player).isEmpty());
        assertEquals(1, fake.savedWins);
        assertEquals(1, fake.savedRewards);
    }

    @Test
    void aCutAfterSavingThePlayerButBeforeMarkingItAppliedIsOnlyMarked() {
        MutangoPending queue = new MutangoPending(file());
        MutangoPending.Award award = add(queue, 1, 1);
        FakePlayer fake = new FakePlayer();

        // se guarda al jugador y el servidor se cae justo antes de marcar la adjudicacion
        assertThrows(IllegalStateException.class, () -> MutangoAwardClaim.claim(award, fake, fake, id -> {
            throw new IllegalStateException("corte antes de marcar");
        }));
        fake.restart();
        MutangoPending afterRestart = new MutangoPending(file());

        MutangoAwardClaim.Result result = MutangoAwardClaim.claim(afterRestart.pendingOf(player).get(0), fake, fake,
            afterRestart::markApplied);

        assertEquals(MutangoAwardClaim.Result.ALREADY_APPLIED, result);
        assertEquals(1, fake.savedWins);
        assertEquals(1, fake.savedRewards);
    }

    @Test
    void withoutRoomNothingIsTouchedAndItStaysPendingUntilThereIsRoom() {
        MutangoPending queue = new MutangoPending(file());
        MutangoPending.Award award = add(queue, 0, 1);
        FakePlayer fake = new FakePlayer();
        fake.freeSlots = 1;

        assertEquals(MutangoAwardClaim.Result.NO_ROOM, MutangoAwardClaim.claim(award, fake, fake, queue::markApplied));
        assertEquals(0, fake.rewards);
        assertEquals(0, fake.savedRewards);
        assertEquals(1, new MutangoPending(file()).pendingOf(player).size(),
            "sigue pendiente, sin depender de un drop en el mundo");

        fake.freeSlots = 2;
        assertEquals(MutangoAwardClaim.Result.CLAIMED, MutangoAwardClaim.claim(award, fake, fake, queue::markApplied));
        assertEquals(1, fake.savedRewards);
    }

    @Test
    void aWinWithoutRewardsDoesNotNeedRoom() {
        MutangoPending queue = new MutangoPending(file());
        MutangoPending.Award award = add(queue, 1, 0);
        FakePlayer fake = new FakePlayer();
        fake.freeSlots = 0;

        assertEquals(MutangoAwardClaim.Result.CLAIMED, MutangoAwardClaim.claim(award, fake, fake, queue::markApplied));
        assertEquals(1, fake.savedWins);
    }

    @Test
    void claimingTheSameAwardTwiceInARowOnlyAppliesItOnce() {
        MutangoPending queue = new MutangoPending(file());
        MutangoPending.Award award = add(queue, 1, 1);
        FakePlayer fake = new FakePlayer();

        MutangoAwardClaim.claim(award, fake, fake, queue::markApplied);
        assertEquals(MutangoAwardClaim.Result.ALREADY_APPLIED,
            MutangoAwardClaim.claim(award, fake, fake, queue::markApplied));
        assertEquals(1, fake.savedWins);
        assertEquals(1, fake.savedRewards);
    }

    @Test
    void aFailureInTheMiddleAndARetryInTheSameSessionAppliesItOnlyOnce() {
        MutangoPending queue = new MutangoPending(file());
        MutangoPending.Award award = add(queue, 1, 1);
        FakePlayer fake = new FakePlayer();
        fake.crashAt = "items"; // las victorias ya se aplicaron en memoria cuando falla la entrega de items

        assertThrows(IllegalStateException.class, () -> MutangoAwardClaim.claim(award, fake, fake, queue::markApplied));
        fake.crashAt = null; // sin reinicio: el mismo jugador, la misma sesion
        MutangoAwardClaim.claim(award, fake, fake, queue::markApplied);

        assertEquals(1, fake.wins, "una victoria, no dos");
        assertEquals(1, fake.rewards);
        assertEquals(1, fake.savedWins);
        assertEquals(1, fake.savedRewards);
    }

    @Test
    void aFailureWhileSavingThePlayerUndoesTheReceiptSoTheRetryDoesNotJustMarkIt() {
        MutangoPending queue = new MutangoPending(file());
        MutangoPending.Award award = add(queue, 1, 1);
        FakePlayer fake = new FakePlayer();
        fake.crashAt = "persist";

        assertThrows(IllegalStateException.class, () -> MutangoAwardClaim.claim(award, fake, fake, queue::markApplied));
        assertFalse(fake.receipts.contains(award.id()), "sin guardar, el recibo no queda en memoria");
        assertEquals(1, new MutangoPending(file()).pendingOf(player).size(), "sigue por aplicar");

        fake.crashAt = null;
        assertEquals(MutangoAwardClaim.Result.CLAIMED, MutangoAwardClaim.claim(award, fake, fake, queue::markApplied));
        assertEquals(1, fake.savedWins);
        assertEquals(1, fake.savedRewards);
    }

    @Test
    void aCutAtEveryStepInTheSameSessionAlsoAppliesItOnlyOnce() {
        for (String step : new String[] {"wins", "items", "receipt", "persist"}) {
            MutangoPending queue = new MutangoPending(new File(folder, "sesion-" + step + ".yml"));
            MutangoPending.Award award = add(queue, 1, 1);
            FakePlayer fake = new FakePlayer();
            fake.crashAt = step;

            assertThrows(IllegalStateException.class,
                () -> MutangoAwardClaim.claim(award, fake, fake, queue::markApplied), step);
            fake.crashAt = null; // reintento sin reiniciar
            MutangoAwardClaim.claim(award, fake, fake, queue::markApplied);

            assertEquals(1, fake.wins, "victoria unica en memoria (corte en " + step + ")");
            assertEquals(1, fake.rewards, "premios unicos en memoria (corte en " + step + ")");
            assertEquals(1, fake.savedWins, step);
            assertEquals(1, fake.savedRewards, step);
        }
    }

    @Test
    void resolvingTheSameFightTwiceGivesTheSameAwardAndAppliesItOnce() {
        UUID fight = UUID.randomUUID();
        MutangoPending queue = new MutangoPending(file());
        FakePlayer fake = new FakePlayer();

        MutangoPending.Award first = queue.add(MutangoPending.idFor(fight, player), player, 1, 1);
        MutangoAwardClaim.claim(first, fake, fake, queue::markApplied);
        // el boss se recupera con su estado anterior y lo vencen de nuevo: la misma pelea, el mismo jugador
        MutangoPending.Award again = queue.add(MutangoPending.idFor(fight, player), player, 1, 1);
        assertEquals(first.id(), again.id());
        MutangoAwardClaim.claim(again, fake, fake, queue::markApplied);

        assertEquals(1, fake.savedWins);
        assertEquals(1, fake.savedRewards);
        assertEquals(1, queue.of(player).size());
    }

    @Test
    void theVictoryAndTheRewardAreOneOperationSoACutCannotLeaveAWinWithoutPrize() {
        UUID fight = UUID.randomUUID();
        MutangoPending queue = new MutangoPending(file());
        MutangoPending.Award award = queue.add(MutangoPending.idFor(fight, player), player, 1, 1);

        // reabrir la cola despues de la unica llamada: victoria y premio estan juntos
        List<MutangoPending.Award> reopened = new MutangoPending(file()).of(player);
        assertEquals(List.of(award), reopened);
        assertEquals(1, reopened.get(0).wins());
        assertEquals(1, reopened.get(0).rewards());
    }

    @Test
    void lasVictoriasPendientesCuentanParaLaDificultad() {
        MutangoPending pending = new MutangoPending(new File(folder, "data/dif.yml"));
        pending.add(UUID.randomUUID(), player, 1, 1);
        pending.add(UUID.randomUUID(), player, 1, 1);
        // 0 aplicadas, 2 ganadas con el inventario lleno: la proxima pelea ya es de nivel 2
        assertEquals(2, MutangoAwardClaim.difficultyWins(0, pending.pendingOf(player)));
    }

    @Test
    void unaVictoriaAplicadaNoSeCuentaDosVeces() {
        MutangoPending pending = new MutangoPending(new File(folder, "data/dif2.yml"));
        MutangoPending.Award applied = pending.add(UUID.randomUUID(), player, 1, 1);
        pending.markApplied(applied.id());
        // la aplicada ya esta en las victorias del jugador (1) y sigue en la cola esperando confirmacion
        assertEquals(1, MutangoAwardClaim.difficultyWins(1, pending.pendingOf(player)));
    }

    @Test
    void losRecibosDeAdjudicacionesSinConfirmarNoSeRecortan() {
        List<String> receipts = new java.util.ArrayList<>();
        for (int i = 0; i < 130; i++) {
            receipts.add("r" + i);
        }
        Set<String> needed = new HashSet<>(List.of("r0", "r1"));
        Set<String> kept = MutangoAwardClaim.trimReceipts(receipts, needed, 128);
        assertEquals(128, kept.size());
        assertTrue(kept.contains("r0") && kept.contains("r1"), "los necesarios se conservan aunque sean los mas viejos");
        assertFalse(kept.contains("r2") || kept.contains("r3"));
    }

    @Test
    void sinNadaQueConservarSeRecortanLosMasViejos() {
        List<String> receipts = new java.util.ArrayList<>();
        for (int i = 0; i < 130; i++) {
            receipts.add("r" + i);
        }
        Set<String> kept = MutangoAwardClaim.trimReceipts(receipts, Set.of(), 128);
        assertEquals(128, kept.size());
        assertFalse(kept.contains("r0") || kept.contains("r1"));
        assertTrue(kept.contains("r129"));
    }
}
