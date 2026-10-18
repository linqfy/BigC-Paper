package dev.linqfy.bigCasares.modules.finance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigInteger;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteExchangeLedgerTest {

    private static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");
    @TempDir Path directory;

    @Test
    void persistsBalancedCreditsAndDeduplicatesAfterReopening() {
        Path file = directory.resolve("nested/exchange.db");
        UUID player = UUID.randomUUID();
        UUID operation = UUID.randomUUID();
        OperationReceipt receipt;
        List<LedgerEntry> entries;
        try (var ledger = new SqliteExchangeLedger(file)) {
            receipt = credit(ledger, operation, player, 150_000);
            entries = ledger.entries(operation);
            assertEquals(150_000, ledger.balance(LedgerAccount.wallet(player), "BC"));
            assertEquals(-150_000, ledger.balance(LedgerAccount.external(), "BC"));
            assertBalanced(entries);
        }
        try (var ledger = new SqliteExchangeLedger(file)) {
            assertEquals(receipt, ledger.transact(operation, "credit:" + player + ":150000", NOW,
                tx -> { throw new AssertionError("Replay must not invoke work"); }));
            assertEquals(entries, ledger.entries(operation));
            assertEquals(receipt, ledger.operation(operation).orElseThrow());
            assertEquals(150_000, ledger.balance(LedgerAccount.wallet(player), "BC"));
            assertThrows(ExchangeException.class,
                () -> ledger.transact(operation, "changed", NOW, tx -> receipt));
            assertEquals(2, ledger.entries(operation).size());
        }
    }

    @Test
    void transfersOnlyAvailableFundsAndRollsBackFailedOperations() {
        try (var ledger = open()) {
            UUID player = UUID.randomUUID();
            UUID recipient = UUID.randomUUID();
            credit(ledger, UUID.randomUUID(), player, 100);
            UUID failed = UUID.randomUUID();
            assertThrows(ExchangeException.class, () -> ledger.transact(failed, "overspend", NOW, tx -> {
                tx.transfer(LedgerAccount.wallet(player), LedgerAccount.wallet(recipient), "BC", 101);
                return receipt(failed, 101);
            }));
            assertEquals(100, ledger.balance(LedgerAccount.wallet(player), "BC"));
            assertEquals(0, ledger.balance(LedgerAccount.wallet(recipient), "BC"));
            assertTrue(ledger.operation(failed).isEmpty());
            assertTrue(ledger.entries(failed).isEmpty());
            UUID transfer = UUID.randomUUID();
            ledger.transact(transfer, "transfer", NOW, tx -> {
                tx.transfer(LedgerAccount.wallet(player), LedgerAccount.wallet(recipient), "BC", 75);
                return receipt(transfer, 75);
            });
            assertEquals(25, ledger.balance(LedgerAccount.wallet(player), "BC"));
            assertEquals(75, ledger.balance(LedgerAccount.wallet(recipient), "BC"));
            assertBalanced(ledger.entries(transfer));
        }
    }

    @Test
    void rollsBackAccountsEntriesAndBalancesIfWorkThrowsAfterWriting() throws Exception {
        Path file = directory.resolve("exchange.db");
        UUID operation = UUID.randomUUID();
        try (var ledger = new SqliteExchangeLedger(file)) {
            assertThrows(IllegalStateException.class, () -> ledger.transact(operation, "interrupted", NOW, tx -> {
                tx.creditConfirmed(UUID.randomUUID(), 100);
                throw new IllegalStateException("failure after ledger write");
            }));
            assertTrue(ledger.entries(operation).isEmpty());
            assertTrue(ledger.operation(operation).isEmpty());
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file);
                 var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT COUNT(*) FROM accounts")) {
                assertEquals(0, result.getInt(1));
            }
            assertEquals(10, credit(ledger, UUID.randomUUID(), UUID.randomUUID(), 10).output());
        }
    }

    @Test
    void cannotCommitAfterTheCallbackCatchesAnEntryWriteFailure() throws Exception {
        Path file = directory.resolve("exchange.db");
        try (var ledger = new SqliteExchangeLedger(file)) {
            UUID source = UUID.randomUUID();
            UUID destination = UUID.randomUUID();
            UUID other = UUID.randomUUID();
            credit(ledger, UUID.randomUUID(), source, 100);
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file);
                 var sql = connection.createStatement()) {
                sql.execute("""
                    CREATE TRIGGER fail_debit_entry BEFORE INSERT ON entries
                    WHEN NEW.delta=-100 AND NEW.account_id LIKE 'wallet:%'
                    BEGIN SELECT RAISE(FAIL, 'injected entry-write failure'); END
                    """);
            }
            UUID operation = UUID.randomUUID();
            assertThrows(ExchangeException.class, () -> ledger.transact(operation, "caught-write-failure", NOW, tx -> {
                ExchangeException failure = assertThrows(ExchangeException.class,
                    () -> tx.transfer(LedgerAccount.wallet(source), LedgerAccount.wallet(destination), "BC", 100));
                assertEquals(ExchangeException.Code.STORAGE, failure.code());
                tx.creditConfirmed(other, 50);
                return receipt(operation, 50);
            }));
            assertEquals(100, ledger.balance(LedgerAccount.wallet(source), "BC"));
            assertEquals(0, ledger.balance(LedgerAccount.wallet(destination), "BC"));
            assertEquals(0, ledger.balance(LedgerAccount.wallet(other), "BC"));
            assertEquals(-100, ledger.balance(LedgerAccount.external(), "BC"));
            assertTrue(ledger.operation(operation).isEmpty());
            assertTrue(ledger.entries(operation).isEmpty());
        }
    }

    @Test
    void rejectsCommitEvenWhenNoFurtherAccessFollowsACaughtWriteFailure() throws Exception {
        Path file = directory.resolve("exchange.db");
        try (var ledger = new SqliteExchangeLedger(file)) {
            UUID source = UUID.randomUUID();
            UUID destination = UUID.randomUUID();
            credit(ledger, UUID.randomUUID(), source, 200);
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file);
                 var sql = connection.createStatement()) {
                sql.execute("""
                    CREATE TRIGGER fail_debit_entry BEFORE INSERT ON entries
                    WHEN NEW.delta=-100 AND NEW.account_id LIKE 'wallet:%'
                    BEGIN SELECT RAISE(ABORT, 'injected entry-write failure'); END
                    """);
            }
            UUID operation = UUID.randomUUID();
            assertThrows(ExchangeException.class, () -> ledger.transact(operation, "caught-then-returned", NOW, tx -> {
                tx.transfer(LedgerAccount.wallet(source), LedgerAccount.wallet(destination), "BC", 20);
                assertThrows(ExchangeException.class,
                    () -> tx.transfer(LedgerAccount.wallet(source), LedgerAccount.wallet(destination), "BC", 100));
                return receipt(operation, 20);
            }));
            assertEquals(200, ledger.balance(LedgerAccount.wallet(source), "BC"));
            assertEquals(0, ledger.balance(LedgerAccount.wallet(destination), "BC"));
            assertTrue(ledger.operation(operation).isEmpty());
            assertTrue(ledger.entries(operation).isEmpty());
        }
    }

    @Test
    void rejectsSpendingControlAccountsAndForgingTheirIdentities() {
        try (var ledger = open()) {
            UUID player = UUID.randomUUID();
            UUID operation = UUID.randomUUID();
            assertThrows(ExchangeException.class, () -> ledger.transact(operation, "control", NOW, tx -> {
                tx.transfer(LedgerAccount.external(), LedgerAccount.wallet(player), "BC", 1);
                return receipt(operation, 1);
            }));
            assertThrows(ExchangeException.class,
                () -> new LedgerAccount("wallet:" + player, AccountKind.EXTERNAL));
            assertTrue(ledger.operation(operation).isEmpty());
        }
    }

    @Test
    void rejectsOverflowInDestinationAndControlBalancesAtomically() {
        try (var ledger = open()) {
            UUID player = UUID.randomUUID();
            credit(ledger, UUID.randomUUID(), player, Long.MAX_VALUE);
            UUID overflow = UUID.randomUUID();
            assertThrows(ExchangeException.class, () -> credit(ledger, overflow, player, 1));
            assertEquals(Long.MAX_VALUE, ledger.balance(LedgerAccount.wallet(player), "BC"));
            assertEquals(-Long.MAX_VALUE, ledger.balance(LedgerAccount.external(), "BC"));
            assertTrue(ledger.operation(overflow).isEmpty());
            credit(ledger, UUID.randomUUID(), UUID.randomUUID(), 1);
            UUID controlOverflow = UUID.randomUUID();
            assertThrows(ExchangeException.class,
                () -> credit(ledger, controlOverflow, UUID.randomUUID(), 1));
            assertEquals(Long.MIN_VALUE, ledger.balance(LedgerAccount.external(), "BC"));
            assertTrue(ledger.entries(controlOverflow).isEmpty());
        }
    }

    @Test
    void issuesExactlyTheDeclaredSupplyOnceAndPersistsLiquidityPosition() {
        try (var ledger = open()) {
            UUID creator = UUID.randomUUID();
            UUID marketId = UUID.randomUUID();
            UUID operation = UUID.randomUUID();
            credit(ledger, UUID.randomUUID(), creator, 100_000);
            MarketSnapshot market = market(marketId, creator);
            ledger.transact(operation, "launch", NOW, tx -> {
                tx.saveMarket(market);
                tx.issue(marketId, creator, 1_000_000, 200_000);
                tx.transfer(LedgerAccount.wallet(creator), LedgerAccount.pool(marketId), "BC", 100_000);
                tx.savePosition(new LiquidityPosition(marketId, creator, 1_000_000, NOW));
                return new OperationReceipt(operation, OperationKind.LAUNCH, marketId, 100_000, 200_000, 0, 0);
            });
            assertEquals(market, ledger.market(marketId).orElseThrow());
            assertEquals(List.of(market), ledger.markets());
            assertEquals(1_000_000, ledger.position(marketId).orElseThrow().shares());
            assertEquals(-1_200_000, ledger.balance(LedgerAccount.issuance(marketId), marketId.toString()));
            assertEquals(200_000, ledger.balance(LedgerAccount.wallet(creator), marketId.toString()));
            assertBalanced(ledger.entries(operation));
            UUID second = UUID.randomUUID();
            assertThrows(ExchangeException.class, () -> ledger.transact(second, "mint-again", NOW, tx -> {
                tx.issue(marketId, creator, 1_000_000, 200_000);
                return receipt(second, 0);
            }));
            assertEquals(-1_200_000, ledger.balance(LedgerAccount.issuance(marketId), marketId.toString()));
        }
    }

    @Test
    void refusesACommitWhosePoolSnapshotDoesNotMatchItsLedgerBalances() {
        try (var ledger = open()) {
            UUID operation = UUID.randomUUID();
            UUID creator = UUID.randomUUID();
            assertThrows(ExchangeException.class, () -> ledger.transact(operation, "unfunded-pool", NOW, tx -> {
                tx.saveMarket(market(operation, creator));
                tx.savePosition(new LiquidityPosition(operation, creator, 1_000_000, NOW));
                return new OperationReceipt(operation, OperationKind.LAUNCH, operation, 100_000, 200_000, 0, 0);
            }));
            assertTrue(ledger.market(operation).isEmpty());
            assertTrue(ledger.operation(operation).isEmpty());
        }
    }

    @Test
    void transactionHandlesExpireAndNestedWritesCannotRollBackTheOuterTransaction() {
        try (var ledger = open()) {
            AtomicReference<ExchangeTransaction> captured = new AtomicReference<>();
            UUID operation = UUID.randomUUID();
            UUID player = UUID.randomUUID();
            ledger.transact(operation, "outer", NOW, tx -> {
                captured.set(tx);
                assertThrows(ExchangeException.class, () -> ledger.transact(UUID.randomUUID(), "nested", NOW,
                    nested -> receipt(UUID.randomUUID(), 0)));
                assertThrows(ExchangeException.class, ledger::close);
                tx.creditConfirmed(player, 100);
                return receipt(operation, 100);
            });
            assertThrows(ExchangeException.class, () -> captured.get().creditConfirmed(player, 1));
            assertEquals(100, ledger.balance(LedgerAccount.wallet(player), "BC"));
        }
    }

    @Test
    void refusesUnknownSchemaVersionsWithoutErasingExistingData() throws Exception {
        Path file = directory.resolve("future.db");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             var statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version=99");
            statement.execute("CREATE TABLE valuable_data (value TEXT)");
            statement.execute("INSERT INTO valuable_data VALUES ('keep')");
        }
        assertThrows(ExchangeException.class, () -> new SqliteExchangeLedger(file));
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT value FROM valuable_data")) {
            assertEquals("keep", result.getString(1));
        }
    }

    @Test
    void closesIdempotentlyAndRejectsSubsequentReadsAndWrites() {
        var ledger = open();
        ledger.close();
        ledger.close();
        assertThrows(ExchangeException.class, () -> ledger.markets());
        assertThrows(ExchangeException.class,
            () -> credit(ledger, UUID.randomUUID(), UUID.randomUUID(), 1));
    }

    @Test
    void enablesWalAndRejectsSqlConstraintViolations() throws Exception {
        Path file = directory.resolve("exchange.db");
        try (var ledger = new SqliteExchangeLedger(file);
             var connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("PRAGMA journal_mode")) {
                assertEquals("wal", result.getString(1));
            }
            UUID player = UUID.randomUUID();
            credit(ledger, UUID.randomUUID(), player, 100);
            assertThrows(java.sql.SQLException.class,
                () -> statement.executeUpdate("UPDATE balances SET amount=-1 WHERE account_id LIKE 'wallet:%'"));
            assertEquals(100, ledger.balance(LedgerAccount.wallet(player), "BC"));
        }
    }

    private SqliteExchangeLedger open() {
        return new SqliteExchangeLedger(directory.resolve("exchange.db"));
    }

    private static MarketSnapshot market(UUID market, UUID creator) {
        return new MarketSnapshot(market, creator, "TEST", "Test", 1_200_000, 100_000,
            1_000_000, 1_000_000, NOW, false);
    }

    private static OperationReceipt credit(SqliteExchangeLedger ledger, UUID operation, UUID player, long amount) {
        return ledger.transact(operation, "credit:" + player + ":" + amount, NOW, tx -> {
            tx.creditConfirmed(player, amount);
            return receipt(operation, amount);
        });
    }

    private static OperationReceipt receipt(UUID operation, long amount) {
        return new OperationReceipt(operation, OperationKind.CONFIRMED_DEPOSIT, null, amount, amount, 0, 0);
    }

    private static void assertBalanced(List<LedgerEntry> entries) {
        assertFalse(entries.isEmpty());
        for (String asset : entries.stream().map(LedgerEntry::asset).distinct().toList()) {
            assertEquals(BigInteger.ZERO, entries.stream().filter(entry -> entry.asset().equals(asset))
                .map(entry -> BigInteger.valueOf(entry.delta())).reduce(BigInteger.ZERO, BigInteger::add));
        }
    }
}
