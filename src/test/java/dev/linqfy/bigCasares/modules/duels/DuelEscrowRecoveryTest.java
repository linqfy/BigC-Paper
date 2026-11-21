package dev.linqfy.bigCasares.modules.duels;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DuelEscrowRecoveryTest {
    @TempDir Path dir;
    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();

    @Test void confirmedStakesAreRecoveredAfterRestart() throws Exception {
        Bank bank = new Bank();
        DuelEscrow escrow = new DuelEscrow(bank, dir.resolve("escrow.properties"));
        assertTrue(escrow.collect(first, second, 10));
        DuelEscrow restarted = new DuelEscrow(bank, dir.resolve("escrow.properties"));
        restarted.recover();
        assertEquals(100, bank.balance(first));
        assertEquals(100, bank.balance(second));
        restarted.recover();
        assertEquals(100, bank.balance(first));
    }

    @Test void uncertainDepositIsNotRepeatedAfterRestart() throws Exception {
        Bank bank = new Bank();
        Path journal = dir.resolve("escrow.properties");
        DuelEscrow escrow = new DuelEscrow(bank, journal);
        assertTrue(escrow.collect(first, second, 10));
        bank.failAfterDeposit = true;
        escrow.payWinner(first, 10);
        assertEquals(110, bank.balance(first));
        DuelEscrow restarted = new DuelEscrow(bank, journal);
        restarted.recover();
        assertEquals(110, bank.balance(first));
        assertEquals(90, bank.balance(second));
        assertTrue(restarted.needsReconciliation());
    }

    @Test void uncertainWithdrawalIsNeverAutomaticallyRefunded() throws Exception {
        Bank bank = new Bank();
        bank.failAfterWithdraw = true;
        Path journal = dir.resolve("escrow.properties");
        assertFalse(new DuelEscrow(bank, journal).collect(first, second, 10));
        DuelEscrow restarted = new DuelEscrow(bank, journal);
        restarted.recover();
        assertEquals(90, bank.balance(first));
        assertEquals(100, bank.balance(second));
        assertTrue(restarted.needsReconciliation());
    }

    private static class Bank implements DuelBank {
        final Map<UUID, Long> money = new HashMap<>();
        boolean failAfterDeposit;
        boolean failAfterWithdraw;
        public long balance(UUID id) { return money.getOrDefault(id, 100L); }
        public boolean withdraw(UUID id, long amount) { money.put(id, balance(id) - amount); return !failAfterWithdraw; }
        public boolean deposit(UUID id, long amount) {
            money.put(id, balance(id) + amount);
            return !failAfterDeposit;
        }
    }
}
