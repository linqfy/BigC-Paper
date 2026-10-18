package dev.linqfy.bigCasares.modules.mutango;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La entrega de un premio: un corte entre cualquier par de pasos no pierde ni duplica nada. El jugador guarda sus datos
 * (victorias, items, recibos) todos juntos al hacer persist(); un corte antes de persist() los deja como estaban.
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
            savedWins = wins;
            savedRewards = rewards;
            savedReceipts.clear();
            savedReceipts.addAll(receipts);
        }
    }

    private File file() {
        return new File(folder, "pendientes.yml");
    }

    @Test
    void aNormalClaimAppliesEverythingOnceAndClearsTheQueue() {
        MutangoPending queue = new MutangoPending(file());
        MutangoPending.Award award = queue.add(player, 1, 1);
        FakePlayer fake = new FakePlayer();

        assertEquals(MutangoAwardClaim.Result.CLAIMED, MutangoAwardClaim.claim(award, fake, fake, queue::confirm));

        assertEquals(1, fake.savedWins);
        assertEquals(1, fake.savedRewards);
        assertTrue(fake.savedReceipts.contains(award.id()));
        assertFalse(new MutangoPending(file()).has(player));
    }

    @Test
    void aCutAtAnyStepNeverLosesNorDuplicatesTheAward() {
        for (String step : new String[] {"wins", "items", "receipt", "persist"}) {
            MutangoPending queue = new MutangoPending(file());
            for (MutangoPending.Award old : queue.of(player)) {
                queue.confirm(old.id());
            }
            MutangoPending.Award award = queue.add(player, 1, 1);
            FakePlayer fake = new FakePlayer();
            fake.crashAt = step;

            assertThrows(IllegalStateException.class,
                () -> MutangoAwardClaim.claim(award, fake, fake, queue::confirm), step);

            // el servidor se cae y vuelve: la cola sale del archivo y el jugador de lo que guardo
            fake.restart();
            MutangoPending afterRestart = new MutangoPending(file());
            assertTrue(afterRestart.has(player), "antes de confirmar la adjudicacion sigue en la cola: " + step);
            for (MutangoPending.Award again : afterRestart.of(player)) {
                MutangoAwardClaim.claim(again, fake, fake, afterRestart::confirm);
            }

            assertEquals(1, fake.savedWins, "una victoria, ni mas ni menos (corte en " + step + ")");
            assertEquals(1, fake.savedRewards, "un juego de premios (corte en " + step + ")");
            assertFalse(new MutangoPending(file()).has(player), "la cola queda vacia (corte en " + step + ")");
        }
    }

    @Test
    void aCutAfterSavingThePlayerButBeforeConfirmingIsOnlyReconfirmed() {
        MutangoPending queue = new MutangoPending(file());
        MutangoPending.Award award = queue.add(player, 1, 1);
        FakePlayer fake = new FakePlayer();

        // se guarda al jugador y el servidor se cae justo antes de borrar la adjudicacion de la cola
        assertThrows(IllegalStateException.class, () -> MutangoAwardClaim.claim(award, fake, fake, id -> {
            throw new IllegalStateException("corte antes de confirmar");
        }));
        fake.restart();
        MutangoPending afterRestart = new MutangoPending(file());

        MutangoAwardClaim.Result result = MutangoAwardClaim.claim(afterRestart.of(player).get(0), fake, fake,
            afterRestart::confirm);

        assertEquals(MutangoAwardClaim.Result.ALREADY_APPLIED, result);
        assertEquals(1, fake.savedWins);
        assertEquals(1, fake.savedRewards);
        assertFalse(new MutangoPending(file()).has(player));
    }

    @Test
    void withoutRoomNothingIsTouchedAndItStaysPendingUntilThereIsRoom() {
        MutangoPending queue = new MutangoPending(file());
        MutangoPending.Award award = queue.add(player, 0, 1);
        FakePlayer fake = new FakePlayer();
        fake.freeSlots = 1;

        assertEquals(MutangoAwardClaim.Result.NO_ROOM, MutangoAwardClaim.claim(award, fake, fake, queue::confirm));
        assertEquals(0, fake.rewards);
        assertEquals(0, fake.savedRewards);
        assertTrue(new MutangoPending(file()).has(player), "sigue pendiente, sin depender de un drop en el mundo");

        fake.freeSlots = 2;
        assertEquals(MutangoAwardClaim.Result.CLAIMED, MutangoAwardClaim.claim(award, fake, fake, queue::confirm));
        assertEquals(1, fake.savedRewards);
        assertFalse(new MutangoPending(file()).has(player));
    }

    @Test
    void aWinWithoutRewardsDoesNotNeedRoom() {
        MutangoPending queue = new MutangoPending(file());
        MutangoPending.Award award = queue.add(player, 1, 0);
        FakePlayer fake = new FakePlayer();
        fake.freeSlots = 0;

        assertEquals(MutangoAwardClaim.Result.CLAIMED, MutangoAwardClaim.claim(award, fake, fake, queue::confirm));
        assertEquals(1, fake.savedWins);
    }

    @Test
    void claimingTheSameAwardTwiceInARowOnlyAppliesItOnce() {
        MutangoPending queue = new MutangoPending(file());
        MutangoPending.Award award = queue.add(player, 1, 1);
        FakePlayer fake = new FakePlayer();

        MutangoAwardClaim.claim(award, fake, fake, queue::confirm);
        assertEquals(MutangoAwardClaim.Result.ALREADY_APPLIED,
            MutangoAwardClaim.claim(award, fake, fake, queue::confirm));
        assertEquals(1, fake.savedWins);
        assertEquals(1, fake.savedRewards);
    }
}
