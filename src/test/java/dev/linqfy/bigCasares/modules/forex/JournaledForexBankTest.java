package dev.linqfy.bigCasares.modules.forex;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class JournaledForexBankTest {
    @TempDir Path directory;
    private final UUID player = UUID.randomUUID();

    @Test
    void writesIntentBeforeCallingVault() {
        Path journal = directory.resolve("transfers.log");
        ForexBank observing = new ForexBank() {
            public long balance(UUID ignored) { return 100; }
            public boolean withdraw(UUID ignored, long amount) {
                try {
                    assertTrue(Files.readString(journal).contains("INTENT"));
                } catch (java.io.IOException failure) {
                    throw new java.io.UncheckedIOException(failure);
                }
                return true;
            }
            public boolean deposit(UUID ignored, long amount) { throw new AssertionError("unexpected deposit"); }
        };
        assertTrue(new JournaledForexBank(observing, journal).withdraw(player, 20));
    }

    @Test
    void successfulTransferIsRetainedUntilSnapshotCommitAndBlocksRestart() throws Exception {
        Path journal = directory.resolve("transfers.log");
        FakeBank delegate = new FakeBank().give(player, 100);
        JournaledForexBank bank = new JournaledForexBank(delegate, journal);
        assertTrue(bank.withdraw(player, 20));
        assertEquals(80, delegate.balance(player));
        assertTrue(Files.readString(journal).contains("SUCCEEDED"));
        assertTrue(new JournaledForexBank(delegate, journal).paused());
        bank.committed();
        assertFalse(Files.exists(journal));
    }

    @Test
    void ordinaryRejectionDoesNotLeaveTheMarketPaused() {
        FakeBank delegate = new FakeBank();
        JournaledForexBank bank = new JournaledForexBank(delegate, directory.resolve("transfers.log"));
        assertFalse(bank.withdraw(player, 20));
        assertFalse(bank.paused());
        assertFalse(Files.exists(directory.resolve("transfers.log")));
    }

    @Test
    void journalWriteFailurePreventsVaultMutation() throws Exception {
        Path notDirectory = Files.createFile(directory.resolve("file"));
        FakeBank delegate = new FakeBank().give(player, 100);
        JournaledForexBank bank = new JournaledForexBank(delegate, notDirectory.resolve("transfers.log"));
        assertThrows(IllegalStateException.class, () -> bank.withdraw(player, 20));
        assertEquals(100, delegate.balance(player));
        assertTrue(bank.paused());
    }

    @Test
    void rejectedTransferAfterSuccessPreservesEvidenceAndPauses() {
        Path journal = directory.resolve("transfers.log");
        FakeBank delegate = new FakeBank().give(player, 100);
        JournaledForexBank bank = new JournaledForexBank(delegate, journal);
        assertTrue(bank.withdraw(player, 20));
        assertThrows(IllegalStateException.class, () -> bank.withdraw(player, 500));
        assertEquals(80, delegate.balance(player));
        assertTrue(bank.paused());
        assertTrue(Files.exists(journal));
        assertTrue(new JournaledForexBank(delegate, journal).paused());
    }

    @Test
    void exceptionFromVaultLeavesIntentAndDoesNotReplayOnRestart() throws Exception {
        Path journal = directory.resolve("transfers.log");
        ForexBank failing = new ForexBank() {
            public long balance(UUID ignored) { return 100; }
            public boolean withdraw(UUID ignored, long amount) { throw new IllegalStateException("unknown outcome"); }
            public boolean deposit(UUID ignored, long amount) { throw new AssertionError("unexpected replay"); }
        };
        JournaledForexBank bank = new JournaledForexBank(failing, journal);
        bank.context("buy ABC player=" + player);
        assertThrows(IllegalStateException.class, () -> bank.withdraw(player, 20));
        assertTrue(bank.paused());
        assertTrue(Files.readString(journal).contains("CONTEXT buy ABC"));
        assertTrue(Files.readString(journal).contains("INTENT"));
        assertTrue(new JournaledForexBank(failing, journal).paused());
    }

    @Test
    void recordsSnapshotFingerprintForManualReconciliation() throws Exception {
        Path snapshot = directory.resolve("market.yml");
        Files.writeString(snapshot, "abc");
        Path journal = directory.resolve("transfers.log");
        JournaledForexBank bank = new JournaledForexBank(new FakeBank().give(player, 100), journal, snapshot);
        assertTrue(bank.withdraw(player, 20));
        assertTrue(Files.readString(journal).contains(
            "BASELINE ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"));
    }
}
