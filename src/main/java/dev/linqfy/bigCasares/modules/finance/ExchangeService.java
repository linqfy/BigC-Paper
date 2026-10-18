package dev.linqfy.bigCasares.modules.finance;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Financial domain API. All quantities are integer base units, never displayed decimal amounts. */
public final class ExchangeService {

    public static final long INITIAL_LP_SHARES = 1_000_000;

    private final ExchangeLedger ledger;
    private final ExchangeSettings settings;
    private final Clock clock;

    public ExchangeService(ExchangeLedger ledger, ExchangeSettings settings, Clock clock) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Trusted integration boundary: the caller must already have durable evidence of an external debit.
     * The UUID identifies that confirmation and must be reused on every retry. This method never calls Vault.
     */
    public OperationReceipt recordConfirmedDeposit(UUID operationId, UUID playerId, long amount) {
        Objects.requireNonNull(playerId, "playerId");
        positive(amount);
        return ledger.transact(operationId, fingerprint(OperationKind.CONFIRMED_DEPOSIT, playerId, amount), clock.instant(), tx -> {
            tx.creditConfirmed(playerId, amount);
            return new OperationReceipt(operationId, OperationKind.CONFIRMED_DEPOSIT, null, amount, amount, 0, 0);
        });
    }

    public OperationReceipt launch(LaunchRequest request) {
        Objects.requireNonNull(request, "request");
        String ticker = normalizeTicker(request.ticker());
        String name = normalizeName(request.name());
        positive(request.bcLiquidity());
        positive(request.poolTokens());
        if (request.creatorTokens() < 0 || (!request.lockDuration().isZero()
            && (request.lockDuration().compareTo(Duration.ofHours(1)) < 0
                || request.lockDuration().compareTo(Duration.ofHours(48)) > 0))) {
            throw new ExchangeException(ExchangeException.Code.INVALID_REQUEST, "Invalid token allocation or lock duration");
        }
        long supply = add(request.poolTokens(), request.creatorTokens());
        Instant now = clock.instant();
        String fingerprint = fingerprint(OperationKind.LAUNCH, request.creatorId(), ticker, name,
            request.bcLiquidity(), request.poolTokens(), request.creatorTokens(), request.lockDuration());
        return ledger.transact(request.operationId(), fingerprint, now, tx -> {
            if (request.bcLiquidity() < settings.minimumLiquidityBc()) {
                throw new ExchangeException(ExchangeException.Code.LIMIT, "Initial liquidity is below minimum");
            }
            if (tx.activeMarketCount(null) >= settings.maximumActiveMarkets()
                || tx.activeMarketCount(request.creatorId()) >= settings.marketsPerCreator()) {
                throw new ExchangeException(ExchangeException.Code.LIMIT, "Active market limit reached");
            }
            if (tx.tickerExists(ticker)) {
                throw new ExchangeException(ExchangeException.Code.CONFLICT, "Ticker already exists");
            }
            LedgerAccount creator = LedgerAccount.wallet(request.creatorId());
            long cost = add(request.bcLiquidity(), settings.launchFeeBc());
            if (tx.balance(creator, "BC") < cost) {
                throw new ExchangeException(ExchangeException.Code.INSUFFICIENT_BALANCE, "Launch requires liquidity plus fee");
            }
            UUID marketId = request.operationId();
            Instant unlockAt = request.lockDuration().isZero() ? Instant.EPOCH : now.plus(request.lockDuration());
            tx.saveMarket(new MarketSnapshot(marketId, request.creatorId(), ticker, name, supply,
                request.bcLiquidity(), request.poolTokens(), INITIAL_LP_SHARES, unlockAt, false));
            tx.issue(marketId, request.creatorId(), request.poolTokens(), request.creatorTokens());
            tx.transfer(creator, LedgerAccount.pool(marketId), "BC", request.bcLiquidity());
            if (settings.launchFeeBc() > 0) {
                tx.transfer(creator, LedgerAccount.fees(), "BC", settings.launchFeeBc());
            }
            tx.savePosition(new LiquidityPosition(marketId, request.creatorId(), INITIAL_LP_SHARES, unlockAt));
            return new OperationReceipt(request.operationId(), OperationKind.LAUNCH, marketId,
                request.bcLiquidity(), request.creatorTokens(), settings.launchFeeBc(), 0);
        });
    }

    public OperationReceipt buy(TradeRequest request) {
        return trade(request, OperationKind.BUY);
    }

    public OperationReceipt sell(TradeRequest request) {
        return trade(request, OperationKind.SELL);
    }

    public OperationReceipt withdrawLiquidity(LiquidityRequest request) {
        Objects.requireNonNull(request, "request");
        positive(request.shares());
        Instant now = clock.instant();
        return ledger.transact(request.operationId(), fingerprint(OperationKind.WITHDRAW_LIQUIDITY,
                request.playerId(), request.marketId(), request.shares()), now, tx -> {
            MarketSnapshot market = openMarket(tx, request.marketId());
            LiquidityPosition position = tx.position(request.marketId()).orElseThrow(() ->
                new ExchangeException(ExchangeException.Code.NOT_FOUND, "Missing LP position"));
            if (!request.playerId().equals(position.ownerId())) {
                throw new ExchangeException(ExchangeException.Code.INVALID_REQUEST, "Player does not own liquidity");
            }
            if (now.isBefore(position.unlockAt())) {
                throw new ExchangeException(ExchangeException.Code.LOCKED, "Liquidity remains locked");
            }
            if (request.shares() > position.shares()) {
                throw new ExchangeException(ExchangeException.Code.INSUFFICIENT_BALANCE, "Insufficient LP shares");
            }
            LiquidityQuote quote = AmmMath.withdraw(market.bcReserve(), market.tokenReserve(), request.shares(), market.lpShares());
            LedgerAccount pool = LedgerAccount.pool(market.marketId());
            LedgerAccount owner = LedgerAccount.wallet(request.playerId());
            tx.transfer(pool, owner, "BC", quote.bcOut());
            tx.transfer(pool, owner, market.marketId().toString(), quote.tokensOut());
            tx.saveMarket(market.withReserves(market.bcReserve() - quote.bcOut(),
                market.tokenReserve() - quote.tokensOut(), quote.sharesAfter(), quote.sharesAfter() == 0));
            tx.savePosition(new LiquidityPosition(position.marketId(), position.ownerId(),
                position.shares() - request.shares(), position.unlockAt()));
            return new OperationReceipt(request.operationId(), OperationKind.WITHDRAW_LIQUIDITY,
                market.marketId(), request.shares(), quote.bcOut(), 0, quote.tokensOut());
        });
    }

    private OperationReceipt trade(TradeRequest request, OperationKind kind) {
        Objects.requireNonNull(request, "request");
        positive(request.input());
        positive(request.minimumOutput());
        String fingerprint = fingerprint(kind, request.playerId(), request.marketId(), request.input(), request.minimumOutput());
        return ledger.transact(request.operationId(), fingerprint, clock.instant(), tx -> {
            MarketSnapshot market = openMarket(tx, request.marketId());
            TradeQuote quote = kind == OperationKind.BUY
                ? AmmMath.buy(market.bcReserve(), market.tokenReserve(), request.input(), settings.tradeFeeBps(), request.minimumOutput())
                : AmmMath.sell(market.bcReserve(), market.tokenReserve(), request.input(), settings.tradeFeeBps(), request.minimumOutput());
            LedgerAccount wallet = LedgerAccount.wallet(request.playerId());
            LedgerAccount pool = LedgerAccount.pool(market.marketId());
            String token = market.marketId().toString();
            if (kind == OperationKind.BUY) {
                tx.transfer(wallet, pool, "BC", request.input() - quote.feeBc());
                tx.transfer(pool, wallet, token, quote.output());
                if (quote.feeBc() > 0) {
                    tx.transfer(wallet, LedgerAccount.fees(), "BC", quote.feeBc());
                }
            } else {
                tx.transfer(wallet, pool, token, request.input());
                tx.transfer(pool, wallet, "BC", quote.output());
                if (quote.feeBc() > 0) {
                    tx.transfer(pool, LedgerAccount.fees(), "BC", quote.feeBc());
                }
            }
            tx.saveMarket(market.withReserves(quote.bcReserveAfter(), quote.tokenReserveAfter(), market.lpShares(), false));
            return new OperationReceipt(request.operationId(), kind, market.marketId(), request.input(), quote.output(), quote.feeBc(), 0);
        });
    }

    private static MarketSnapshot openMarket(ExchangeTransaction tx, UUID id) {
        MarketSnapshot market = tx.market(id).orElseThrow(() -> new ExchangeException(ExchangeException.Code.NOT_FOUND, "Unknown market"));
        if (market.closed()) {
            throw new ExchangeException(ExchangeException.Code.CLOSED, "Market has no remaining liquidity");
        }
        return market;
    }

    private static String normalizeTicker(String ticker) {
        if (ticker == null || !ticker.matches("[A-Za-z0-9]{2,12}")) {
            throw new ExchangeException(ExchangeException.Code.INVALID_REQUEST, "Ticker must contain 2-12 ASCII letters or digits");
        }
        String normalized = ticker.toUpperCase(Locale.ROOT);
        if (normalized.equals("BC") || normalized.equals("ORO")) {
            throw new ExchangeException(ExchangeException.Code.INVALID_REQUEST, "Reserved ticker");
        }
        return normalized;
    }

    private static String normalizeName(String name) {
        if (name == null || name.codePoints().anyMatch(code -> Character.isISOControl(code)
            || Character.getType(code) == Character.FORMAT || Character.getType(code) == Character.SURROGATE)) {
            throw new ExchangeException(ExchangeException.Code.INVALID_REQUEST, "Name contains nonvisible characters");
        }
        String normalized = name.strip();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 1 || length > 32 || normalized.indexOf('§') >= 0 || normalized.matches("(?s).*&[0-9a-fk-orA-FK-OR].*")) {
            throw new ExchangeException(ExchangeException.Code.INVALID_REQUEST, "Name must contain 1-32 visible characters without formatting");
        }
        return normalized;
    }

    private static String fingerprint(Object... fields) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object field : fields) {
                byte[] bytes = field.toString().getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("JVM lacks required SHA-256 support", failure);
        }
    }

    private static void positive(long amount) {
        if (amount <= 0) {
            throw new ExchangeException(ExchangeException.Code.INVALID_AMOUNT, "Amount must be positive");
        }
    }

    private static long add(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException failure) {
            throw new ExchangeException(ExchangeException.Code.OVERFLOW, "Token supply or launch cost overflow", failure);
        }
    }
}
