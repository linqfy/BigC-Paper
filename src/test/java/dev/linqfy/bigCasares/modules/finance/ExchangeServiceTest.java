package dev.linqfy.bigCasares.modules.finance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExchangeServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");
    private static final long POOL_TOKENS = 1_000_000_000_000L;
    private static final long CREATOR_TOKENS = 200_000_000_000L;
    @TempDir Path directory;

    @Test
    void launchesByPayingFeeAndFundingReservesWithoutChangingTotalSupplyLater() {
        try (var ledger = open()) {
            ExchangeService service = service(ledger);
            UUID creator = fund(service, 150_000);
            LaunchRequest request = launch(creator, "CHORI", Duration.ZERO);
            OperationReceipt receipt = service.launch(request);
            MarketSnapshot market = ledger.market(request.operationId()).orElseThrow();
            assertEquals(0, bc(ledger, creator));
            assertEquals(50_000, ledger.balance(LedgerAccount.fees(), "BC"));
            assertEquals(100_000, market.bcReserve());
            assertEquals(POOL_TOKENS, market.tokenReserve());
            assertEquals(POOL_TOKENS + CREATOR_TOKENS, market.totalSupply());
            assertEquals(CREATOR_TOKENS, tokens(ledger, creator, market.marketId()));
            assertEquals(1_000_000, ledger.position(market.marketId()).orElseThrow().shares());
            assertEquals(receipt, service.launch(request));
            assertEquals(50_000, ledger.balance(LedgerAccount.fees(), "BC"));
        }
    }

    @Test
    void creditsOnlyOnceAndRejectsConflictingDepositReplays() {
        try (var ledger = open()) {
            ExchangeService service = service(ledger);
            UUID id = UUID.randomUUID();
            UUID player = UUID.randomUUID();
            OperationReceipt first = service.recordConfirmedDeposit(id, player, 50);
            assertEquals(first, service.recordConfirmedDeposit(id, player, 50));
            assertEquals(50, bc(ledger, player));
            assertThrows(ExchangeException.class, () -> service.recordConfirmedDeposit(id, player, 51));
            assertThrows(ExchangeException.class, () -> service.recordConfirmedDeposit(id, UUID.randomUUID(), 50));
            assertThrows(ExchangeException.class, () -> service.recordConfirmedDeposit(UUID.randomUUID(), player, 0));
        }
    }

    @Test
    void rejectsInvalidMetadataAndLocksWithoutMovingFunds() {
        try (var ledger = open()) {
            ExchangeService service = service(ledger);
            UUID creator = fund(service, 150_000);
            for (String ticker : new String[]{"", "A", "BC", "oro", "TOO_LONG_TICKER", "A$", "ß", "ＡＢ"}) {
                assertThrows(ExchangeException.class, () -> service.launch(launch(creator, ticker, Duration.ZERO)), ticker);
            }
            for (String name : new String[]{"", "   ", "a".repeat(33), "§aCoin", "&aCoin", "Bad\nName", "\u200B"}) {
                assertThrows(ExchangeException.class, () -> service.launch(new LaunchRequest(UUID.randomUUID(), creator,
                    "COIN", name, 100_000, POOL_TOKENS, CREATOR_TOKENS, Duration.ZERO)), name);
            }
            for (Duration lock : new Duration[]{Duration.ofSeconds(-1), Duration.ofMinutes(59), Duration.ofHours(49)}) {
                assertThrows(ExchangeException.class, () -> service.launch(launch(creator, "COIN", lock)));
            }
            assertEquals(150_000, bc(ledger, creator));
            assertEquals(0, ledger.balance(LedgerAccount.fees(), "BC"));
            assertTrue(ledger.markets().isEmpty());
        }
    }

    @Test
    void normalizesAsciiTickerIndependentlyOfServerLocale() {
        Locale previous = Locale.getDefault();
        try (var ledger = open()) {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            ExchangeService service = service(ledger);
            UUID creator = fund(service, 150_000);
            LaunchRequest request = launch(creator, "mini", Duration.ZERO);
            service.launch(request);
            assertEquals("MINI", ledger.market(request.operationId()).orElseThrow().ticker());
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void enforcesLaunchCostLiquiditySupplyAndMarketLimits() {
        try (var ledger = open()) {
            ExchangeService service = service(ledger);
            UUID creator = fund(service, 149_999);
            assertThrows(ExchangeException.class, () -> service.launch(launch(creator, "CHORI", Duration.ZERO)));
            assertEquals(149_999, bc(ledger, creator));
            service.recordConfirmedDeposit(UUID.randomUUID(), creator, 1);
            assertThrows(ExchangeException.class, () -> service.launch(new LaunchRequest(UUID.randomUUID(), creator,
                "DUST", "Dust", 99_999, POOL_TOKENS, 0, Duration.ZERO)));
            assertThrows(ExchangeException.class, () -> service.launch(new LaunchRequest(UUID.randomUUID(), creator,
                "OVER", "Overflow", 100_000, Long.MAX_VALUE, 1, Duration.ZERO)));
            service.launch(launch(creator, "CHORI", Duration.ZERO));
            service.recordConfirmedDeposit(UUID.randomUUID(), creator, 150_000);
            assertThrows(ExchangeException.class, () -> service.launch(launch(creator, "SECOND", Duration.ZERO)));
            UUID other = fund(service, 150_000);
            assertThrows(ExchangeException.class, () -> service.launch(launch(other, "chori", Duration.ZERO)));
            assertEquals(150_000, bc(ledger, other));
            ExchangeService oneMarket = new ExchangeService(ledger, new ExchangeSettings(50_000, 100_000, 1, 1, 150), clock(NOW));
            assertThrows(ExchangeException.class, () -> oneMarket.launch(launch(other, "OTHER", Duration.ZERO)));
        }
    }

    @Test
    void tradesAtCurrentReservesAndSeparatesFeesFromPoolMoney() {
        try (var ledger = open()) {
            ExchangeService service = service(ledger);
            UUID creator = fund(service, 150_000);
            UUID market = service.launch(launch(creator, "CHORI", Duration.ZERO)).marketId();
            UUID trader = fund(service, 50_000);
            TradeRequest buy = new TradeRequest(UUID.randomUUID(), trader, market, 50_000, 1);
            OperationReceipt bought = service.buy(buy);
            assertEquals(750, bought.feeBc());
            assertEquals(0, bc(ledger, trader));
            assertEquals(bought.output(), tokens(ledger, trader, market));
            assertEquals(149_250, ledger.market(market).orElseThrow().bcReserve());
            assertEquals(50_750, ledger.balance(LedgerAccount.fees(), "BC"));
            assertEquals(bought, service.buy(buy));
            OperationReceipt sold = service.sell(new TradeRequest(UUID.randomUUID(), trader, market, bought.output(), 1));
            assertTrue(sold.output() < 50_000);
            assertEquals(sold.output(), bc(ledger, trader));
            assertEquals(0, tokens(ledger, trader, market));
            assertEquals(50_750 + sold.feeBc(), ledger.balance(LedgerAccount.fees(), "BC"));
        }
    }

    @Test
    void rejectsOverspendingOverSellingAndSlippageWithoutChangingState() {
        try (var ledger = open()) {
            ExchangeService service = service(ledger);
            UUID creator = fund(service, 150_000);
            UUID market = service.launch(launch(creator, "CHORI", Duration.ZERO)).marketId();
            UUID player = fund(service, 100);
            MarketSnapshot before = ledger.market(market).orElseThrow();
            assertThrows(ExchangeException.class,
                () -> service.buy(new TradeRequest(UUID.randomUUID(), player, market, 101, 1)));
            assertThrows(ExchangeException.class,
                () -> service.buy(new TradeRequest(UUID.randomUUID(), player, market, 100, Long.MAX_VALUE)));
            assertThrows(ExchangeException.class,
                () -> service.sell(new TradeRequest(UUID.randomUUID(), player, market, 1_000_000, 1)));
            assertThrows(ExchangeException.class,
                () -> service.buy(new TradeRequest(UUID.randomUUID(), player, market, 100, 0)));
            assertEquals(before, ledger.market(market).orElseThrow());
            assertEquals(100, bc(ledger, player));
        }
    }

    @Test
    void creatorCanDumpOnlyTheTokensTheyActuallyRetained() {
        try (var ledger = open()) {
            ExchangeService service = service(ledger);
            UUID creator = fund(service, 150_000);
            UUID market = service.launch(launch(creator, "CHORI", Duration.ZERO)).marketId();
            assertThrows(ExchangeException.class,
                () -> service.sell(new TradeRequest(UUID.randomUUID(), creator, market, CREATOR_TOKENS + 1, 1)));
            service.sell(new TradeRequest(UUID.randomUUID(), creator, market, CREATOR_TOKENS, 1));
            assertEquals(0, tokens(ledger, creator, market));
            assertTrue(bc(ledger, creator) > 0);
            assertTrue(ledger.market(market).orElseThrow().bcReserve() < 100_000);
        }
    }

    @Test
    void partialAndTotalRugsLeaveThirdPartyTokensIntactAndCloseThePool() {
        try (var ledger = open()) {
            ExchangeService service = service(ledger);
            UUID creator = fund(service, 150_000);
            UUID market = service.launch(launch(creator, "CHORI", Duration.ZERO)).marketId();
            UUID trader = fund(service, 50_000);
            long held = service.buy(new TradeRequest(UUID.randomUUID(), trader, market, 50_000, 1)).output();
            assertThrows(ExchangeException.class, () -> service.withdrawLiquidity(
                new LiquidityRequest(UUID.randomUUID(), trader, market, 1)));
            assertThrows(ExchangeException.class, () -> service.withdrawLiquidity(
                new LiquidityRequest(UUID.randomUUID(), creator, market, 1_000_001)));
            OperationReceipt partial = service.withdrawLiquidity(new LiquidityRequest(UUID.randomUUID(), creator, market, 900_000));
            assertEquals(134_325, partial.output());
            assertEquals(held, tokens(ledger, trader, market));
            assertEquals(100_000, ledger.position(market).orElseThrow().shares());
            LiquidityRequest last = new LiquidityRequest(UUID.randomUUID(), creator, market, 100_000);
            OperationReceipt finalBurn = service.withdrawLiquidity(last);
            assertEquals(149_250, partial.output() + finalBurn.output());
            assertEquals(held, tokens(ledger, trader, market));
            assertTrue(ledger.market(market).orElseThrow().closed());
            assertEquals(0, ledger.market(market).orElseThrow().tokenReserve());
            assertEquals(finalBurn, service.withdrawLiquidity(last));
            assertThrows(ExchangeException.class,
                () -> service.sell(new TradeRequest(UUID.randomUUID(), trader, market, held, 1)));
            assertThrows(ExchangeException.class,
                () -> service.buy(new TradeRequest(UUID.randomUUID(), trader, market, 1, 1)));
            service.recordConfirmedDeposit(UUID.randomUUID(), creator, 150_000);
            assertFalse(ledger.market(service.launch(launch(creator, "NEXT", Duration.ZERO)).marketId()).orElseThrow().closed());
        }
    }

    @Test
    void permitsLiquidityWithdrawalExactlyAtUnlockTime() {
        try (var ledger = open()) {
            ExchangeService service = service(ledger);
            UUID creator = fund(service, 150_000);
            UUID market = service.launch(launch(creator, "LOCK", Duration.ofHours(1))).marketId();
            LiquidityRequest withdrawal = new LiquidityRequest(UUID.randomUUID(), creator, market, 1_000_000);
            ExchangeService early = new ExchangeService(ledger, ExchangeSettings.defaults(), clock(NOW.plusSeconds(3600).minusNanos(1)));
            assertThrows(ExchangeException.class, () -> early.withdrawLiquidity(withdrawal));
            ExchangeService atUnlock = new ExchangeService(ledger, ExchangeSettings.defaults(), clock(NOW.plusSeconds(3600)));
            assertEquals(100_000, atUnlock.withdrawLiquidity(withdrawal).output());
        }
    }

    @Test
    void replaysAcrossConfigurationChangesAndReopeningUsingOriginalEconomics() {
        Path file = directory.resolve("exchange.db");
        LaunchRequest request;
        TradeRequest buy;
        OperationReceipt bought;
        try (var ledger = new SqliteExchangeLedger(file)) {
            ExchangeService service = service(ledger);
            UUID creator = fund(service, 150_000);
            request = launch(creator, "CHORI", Duration.ZERO);
            service.launch(request);
            UUID buyer = fund(service, 50_000);
            buy = new TradeRequest(UUID.randomUUID(), buyer, request.operationId(), 50_000, 1);
            bought = service.buy(buy);
        }
        try (var ledger = new SqliteExchangeLedger(file)) {
            ExchangeService changed = new ExchangeService(ledger, new ExchangeSettings(90_000, 200_000, 1, 1, 900),
                clock(NOW.plusSeconds(100_000)));
            assertEquals(request.operationId(), changed.launch(request).marketId());
            assertEquals(bought, changed.buy(buy));
            assertEquals(50_750, ledger.balance(LedgerAccount.fees(), "BC"));
            assertThrows(ExchangeException.class, () -> changed.sell(buy));
        }
    }

    @Test
    void supportsZeroCreatorAllocationAndRejectsDustLiquidityRemoval() {
        try (var ledger = open()) {
            ExchangeService tiny = new ExchangeService(ledger, new ExchangeSettings(0, 1, 1, 6, 0), clock(NOW));
            UUID creator = fund(tiny, 1);
            UUID market = tiny.launch(new LaunchRequest(UUID.randomUUID(), creator, "TINY", "Tiny", 1, 1, 0, Duration.ZERO)).marketId();
            assertEquals(0, tokens(ledger, creator, market));
            assertThrows(ExchangeException.class, () -> tiny.withdrawLiquidity(
                new LiquidityRequest(UUID.randomUUID(), creator, market, 1)));
            assertEquals(1, tiny.withdrawLiquidity(new LiquidityRequest(UUID.randomUUID(), creator, market, 1_000_000)).output());
        }
    }

    @Test
    void rejectsInvalidSettings() {
        assertThrows(ExchangeException.class, () -> new ExchangeSettings(-1, 1, 1, 6, 150));
        assertThrows(ExchangeException.class, () -> new ExchangeSettings(0, 0, 1, 6, 150));
        assertThrows(ExchangeException.class, () -> new ExchangeSettings(0, 1, 0, 6, 150));
        assertThrows(ExchangeException.class, () -> new ExchangeSettings(0, 1, 1, 0, 150));
        assertThrows(ExchangeException.class, () -> new ExchangeSettings(0, 1, 1, 6, 10000));
    }

    private SqliteExchangeLedger open() {
        return new SqliteExchangeLedger(directory.resolve("exchange.db"));
    }

    private static ExchangeService service(ExchangeLedger ledger) {
        return new ExchangeService(ledger, ExchangeSettings.defaults(), clock(NOW));
    }

    private static Clock clock(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    private static UUID fund(ExchangeService service, long amount) {
        UUID player = UUID.randomUUID();
        service.recordConfirmedDeposit(UUID.randomUUID(), player, amount);
        return player;
    }

    private static LaunchRequest launch(UUID creator, String ticker, Duration lock) {
        return new LaunchRequest(UUID.randomUUID(), creator, ticker, "Choripan", 100_000,
            POOL_TOKENS, CREATOR_TOKENS, lock);
    }

    private static long bc(ExchangeLedger ledger, UUID player) {
        return ledger.balance(LedgerAccount.wallet(player), "BC");
    }

    private static long tokens(ExchangeLedger ledger, UUID player, UUID market) {
        return ledger.balance(LedgerAccount.wallet(player), market.toString());
    }
}
