package dev.linqfy.bigCasares.modules.finance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(30)
class ExchangeRecoveryTest {

    @TempDir Path directory;

    @Test
    void processDeathMidTransactionPreservesTheLastCommittedFundsAndMarket() throws Exception {
        Path file = directory.resolve("exchange.db");
        UUID creator = UUID.randomUUID();
        UUID trader = UUID.randomUUID();
        UUID market = UUID.randomUUID();
        UUID interrupted = UUID.randomUUID();
        MarketSnapshot before;
        try (var ledger = new SqliteExchangeLedger(file)) {
            var service = new ExchangeService(ledger, ExchangeSettings.defaults(), Clock.systemUTC());
            service.recordConfirmedDeposit(UUID.randomUUID(), creator, 150_000);
            service.recordConfirmedDeposit(UUID.randomUUID(), trader, 50_000);
            service.launch(new LaunchRequest(market, creator, "CHORI", "Choripan", 100_000,
                1_000_000_000_000L, 200_000_000_000L, Duration.ZERO));
            before = ledger.market(market).orElseThrow();
        }
        String classpath = Stream.of(ExchangeRecoveryTest.class, SqliteExchangeLedger.class,
                org.sqlite.JDBC.class, org.slf4j.LoggerFactory.class)
            .map(type -> {
                try {
                    return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
                } catch (java.net.URISyntaxException failure) {
                    throw new IllegalStateException(failure);
                }
            }).distinct().collect(Collectors.joining(File.pathSeparator));
        Path output = directory.resolve("crash-writer.log");
        Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "--enable-native-access=ALL-UNNAMED", "-cp", classpath, CrashWriter.class.getName(),
            file.toString(), trader.toString(), market.toString(), interrupted.toString())
            .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(child.waitFor(15, TimeUnit.SECONDS), "Crash writer did not exit");
            assertEquals(23, child.exitValue(), Files.readString(output));
        } finally {
            if (child.isAlive()) {
                child.destroyForcibly();
                child.waitFor(5, TimeUnit.SECONDS);
            }
        }
        try (var ledger = new SqliteExchangeLedger(file)) {
            assertEquals(before, ledger.market(market).orElseThrow());
            assertEquals(50_000, ledger.balance(LedgerAccount.wallet(trader), "BC"));
            assertEquals(100_000, ledger.balance(LedgerAccount.pool(market), "BC"));
            assertTrue(ledger.operation(interrupted).isEmpty());
            assertTrue(ledger.entries(interrupted).isEmpty());
        }
    }

    @Test
    void concurrentCloseWaitsForAnActiveCommitAndRejectsLaterWork() throws Exception {
        Path file = directory.resolve("exchange.db");
        var ledger = new SqliteExchangeLedger(file);
        UUID operation = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        CountDownLatch written = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch closing = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var writing = workers.submit(() -> ledger.transact(operation, "close-test", Instant.now(), tx -> {
                tx.creditConfirmed(player, 100);
                written.countDown();
                try {
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(failure);
                }
                return new OperationReceipt(operation, OperationKind.CONFIRMED_DEPOSIT, null, 100, 100, 0, 0);
            }));
            assertTrue(written.await(5, TimeUnit.SECONDS));
            var close = workers.submit(() -> { closing.countDown(); ledger.close(); closed.countDown(); });
            try {
                assertTrue(closing.await(5, TimeUnit.SECONDS));
                assertFalse(closed.await(100, TimeUnit.MILLISECONDS));
            } finally {
                release.countDown();
            }
            assertEquals(operation, writing.get(10, TimeUnit.SECONDS).operationId());
            close.get(10, TimeUnit.SECONDS);
            assertThrows(ExchangeException.class, ledger::markets);
        } finally {
            release.countDown();
            ledger.close();
        }
        try (var reopened = new SqliteExchangeLedger(file)) {
            assertEquals(100, reopened.balance(LedgerAccount.wallet(player), "BC"));
            assertTrue(reopened.operation(operation).isPresent());
        }
    }

    public static final class CrashWriter {

        public static void main(String[] args) {
            try (var ledger = new SqliteExchangeLedger(Path.of(args[0]))) {
                UUID player = UUID.fromString(args[1]);
                UUID market = UUID.fromString(args[2]);
                UUID operation = UUID.fromString(args[3]);
                ledger.transact(operation, "deliberately-interrupted", Instant.now(), tx -> {
                    tx.transfer(LedgerAccount.wallet(player), LedgerAccount.pool(market), "BC", 100);
                    Runtime.getRuntime().halt(23);
                    throw new AssertionError("halt returned");
                });
            }
        }
    }
}
