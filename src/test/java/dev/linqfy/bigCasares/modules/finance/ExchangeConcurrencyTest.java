package dev.linqfy.bigCasares.modules.finance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(20)
class ExchangeConcurrencyTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-18T12:00:00Z"), ZoneOffset.UTC);
    @TempDir Path directory;

    @Test
    void twoConnectionsCannotSpendTheSameWalletTwice() throws Exception {
        Path file = directory.resolve("exchange.db");
        try (var first = new SqliteExchangeLedger(file); var second = new SqliteExchangeLedger(file)) {
            var a = service(first);
            var b = service(second);
            UUID market = setupMarket(a);
            UUID trader = fund(a, 50_000);
            TradeRequest one = new TradeRequest(UUID.randomUUID(), trader, market, 40_000, 1);
            TradeRequest two = new TradeRequest(UUID.randomUUID(), trader, market, 40_000, 1);
            List<Boolean> results = race(() -> tryBuy(a, one), () -> tryBuy(b, two));
            assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
            assertEquals(10_000, first.balance(LedgerAccount.wallet(trader), "BC"));
            assertEquals(139_400, first.market(market).orElseThrow().bcReserve());
            assertEquals(1, List.of(one, two).stream().filter(request -> first.operation(request.operationId()).isPresent()).count());
            UUID rejected = results.get(0) ? two.operationId() : one.operationId();
            assertTrue(first.entries(rejected).isEmpty());
        }
    }

    @Test
    void fundedBuyersExecuteAgainstSuccessiveReservesAcrossConnections() throws Exception {
        Path file = directory.resolve("exchange.db");
        try (var first = new SqliteExchangeLedger(file); var second = new SqliteExchangeLedger(file)) {
            var a = service(first);
            var b = service(second);
            UUID market = setupMarket(a);
            UUID playerA = fund(a, 50_000);
            UUID playerB = fund(a, 50_000);
            TradeQuote one = AmmMath.buy(100_000, 1_000_000_000_000L, 50_000, 150, 1);
            TradeQuote two = AmmMath.buy(one.bcReserveAfter(), one.tokenReserveAfter(), 50_000, 150, 1);
            List<OperationReceipt> results = race(
                () -> a.buy(new TradeRequest(UUID.randomUUID(), playerA, market, 50_000, 1)),
                () -> b.buy(new TradeRequest(UUID.randomUUID(), playerB, market, 50_000, 1)));
            assertEquals(List.of(one.output(), two.output()).stream().sorted().toList(),
                results.stream().map(OperationReceipt::output).sorted().toList());
            assertEquals(two.bcReserveAfter(), first.market(market).orElseThrow().bcReserve());
            assertEquals(two.tokenReserveAfter(), first.market(market).orElseThrow().tokenReserve());
            assertEquals(0, second.balance(LedgerAccount.wallet(playerA), "BC"));
            assertEquals(0, second.balance(LedgerAccount.wallet(playerB), "BC"));
        }
    }

    @Test
    void identicalConcurrentOperationReturnsOneReceiptAndOneSetOfEntries() throws Exception {
        Path file = directory.resolve("exchange.db");
        try (var first = new SqliteExchangeLedger(file); var second = new SqliteExchangeLedger(file)) {
            var a = service(first);
            var b = service(second);
            UUID market = setupMarket(a);
            UUID trader = fund(a, 50_000);
            TradeRequest request = new TradeRequest(UUID.randomUUID(), trader, market, 50_000, 1);
            List<OperationReceipt> results = race(() -> a.buy(request), () -> b.buy(request));
            assertEquals(results.get(0), results.get(1));
            assertEquals(6, first.entries(request.operationId()).size());
            assertEquals(149_250, first.market(market).orElseThrow().bcReserve());
        }
    }

    @Test
    void launchLimitIsCheckedAfterAcquiringTheDatabaseWriteLock() throws Exception {
        Path file = directory.resolve("exchange.db");
        try (var first = new SqliteExchangeLedger(file); var second = new SqliteExchangeLedger(file)) {
            ExchangeSettings settings = new ExchangeSettings(50_000, 100_000, 1, 1, 150);
            var a = new ExchangeService(first, settings, CLOCK);
            var b = new ExchangeService(second, settings, CLOCK);
            UUID creatorA = fund(a, 150_000);
            UUID creatorB = fund(a, 150_000);
            List<Boolean> results = race(() -> tryLaunch(a, creatorA, "FIRST"), () -> tryLaunch(b, creatorB, "SECOND"));
            assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
            assertEquals(1, first.markets().size());
            assertEquals(150_000, first.balance(LedgerAccount.wallet(results.get(0) ? creatorB : creatorA), "BC"));
        }
    }

    @Test
    void refusesAStaleQuoteThatNoLongerMeetsItsMinimumOutput() {
        Path file = directory.resolve("exchange.db");
        try (var first = new SqliteExchangeLedger(file); var second = new SqliteExchangeLedger(file)) {
            var a = service(first);
            var b = service(second);
            UUID market = setupMarket(a);
            UUID trader = fund(a, 50_000);
            UUID other = fund(a, 50_000);
            long minimum = AmmMath.buy(100_000, 1_000_000_000_000L, 50_000, 150, 1).output();
            b.buy(new TradeRequest(UUID.randomUUID(), other, market, 50_000, 1));
            assertFalse(tryBuy(a, new TradeRequest(UUID.randomUUID(), trader, market, 50_000, minimum)));
            assertEquals(50_000, first.balance(LedgerAccount.wallet(trader), "BC"));
        }
    }

    private static boolean tryBuy(ExchangeService service, TradeRequest request) {
        try {
            service.buy(request);
            return true;
        } catch (ExchangeException failure) {
            assertTrue(failure.code() == ExchangeException.Code.INSUFFICIENT_BALANCE
                || failure.code() == ExchangeException.Code.SLIPPAGE);
            return false;
        }
    }

    private static boolean tryLaunch(ExchangeService service, UUID creator, String ticker) {
        try {
            service.launch(launch(creator, ticker));
            return true;
        } catch (ExchangeException failure) {
            assertEquals(ExchangeException.Code.LIMIT, failure.code());
            return false;
        }
    }

    private static <T> List<T> race(Callable<T> first, Callable<T> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> { ready.countDown(); assertTrue(start.await(5, TimeUnit.SECONDS)); return first.call(); });
            var b = workers.submit(() -> { ready.countDown(); assertTrue(start.await(5, TimeUnit.SECONDS)); return second.call(); });
            try {
                assertTrue(ready.await(5, TimeUnit.SECONDS));
            } finally {
                start.countDown();
            }
            return List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
        }
    }

    private static ExchangeService service(ExchangeLedger ledger) {
        return new ExchangeService(ledger, ExchangeSettings.defaults(), CLOCK);
    }

    private static UUID setupMarket(ExchangeService service) {
        return service.launch(launch(fund(service, 150_000), "CHORI")).marketId();
    }

    private static LaunchRequest launch(UUID creator, String ticker) {
        return new LaunchRequest(UUID.randomUUID(), creator, ticker, "Choripan", 100_000,
            1_000_000_000_000L, 200_000_000_000L, Duration.ZERO);
    }

    private static UUID fund(ExchangeService service, long amount) {
        UUID player = UUID.randomUUID();
        service.recordConfirmedDeposit(UUID.randomUUID(), player, amount);
        return player;
    }
}
