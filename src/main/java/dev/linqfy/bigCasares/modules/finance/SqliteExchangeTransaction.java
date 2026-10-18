package dev.linqfy.bigCasares.modules.finance;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static dev.linqfy.bigCasares.modules.finance.SqliteExchangeLedger.statement;

final class SqliteExchangeTransaction implements ExchangeTransaction {

    private final Connection connection;
    private final UUID operationId;
    private final Thread owner = Thread.currentThread();
    private final Map<String, BigInteger> deltas = new HashMap<>();
    private final Set<UUID> touchedMarkets = new LinkedHashSet<>();
    private volatile boolean active = true;
    private SQLException storageFailure;

    SqliteExchangeTransaction(Connection connection, UUID operationId) {
        this.connection = connection;
        this.operationId = operationId;
    }

    @Override
    public long balance(LedgerAccount account, String asset) {
        return access(() -> SqliteExchangeLedger.readBalance(connection, account, asset));
    }

    @Override
    public Optional<MarketSnapshot> market(UUID marketId) {
        return access(() -> SqliteExchangeLedger.readMarket(connection, marketId));
    }

    @Override
    public Optional<LiquidityPosition> position(UUID marketId) {
        return access(() -> SqliteExchangeLedger.readPosition(connection, marketId));
    }

    @Override
    public boolean tickerExists(String ticker) {
        return access(() -> {
            try (var sql = statement(connection, "SELECT 1 FROM markets WHERE ticker=?", ticker);
                 var result = sql.executeQuery()) {
                return result.next();
            }
        });
    }

    @Override
    public int activeMarketCount(UUID creatorId) {
        return access(() -> {
            String filter = creatorId == null ? "" : " AND creator_id=?";
            Object[] values = creatorId == null ? new Object[0] : new Object[]{creatorId};
            try (var sql = statement(connection, "SELECT COUNT(*) FROM markets WHERE closed=0" + filter, values);
                 var result = sql.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        });
    }

    @Override
    public void transfer(LedgerAccount from, LedgerAccount to, String asset, long amount) {
        guard();
        positive(amount);
        if (from.kind().control() || to.kind().control() || from.equals(to)) {
            throw new ExchangeException(ExchangeException.Code.INVALID_REQUEST, "Cannot transfer control accounts or to self");
        }
        post(from, asset, -amount);
        post(to, asset, amount);
    }

    @Override
    public void creditConfirmed(UUID playerId, long amount) {
        guard();
        positive(amount);
        post(LedgerAccount.external(), "BC", -amount);
        post(LedgerAccount.wallet(playerId), "BC", amount);
    }

    @Override
    public void issue(UUID marketId, UUID creatorId, long poolTokens, long creatorTokens) {
        guard();
        positive(poolTokens);
        if (creatorTokens < 0) {
            throw new ExchangeException(ExchangeException.Code.INVALID_AMOUNT, "Negative creator allocation");
        }
        MarketSnapshot market = requiredMarket(marketId);
        long supply = add(poolTokens, creatorTokens);
        LedgerAccount issuance = LedgerAccount.issuance(marketId);
        boolean exists = access(() -> {
            try (var sql = statement(connection, "SELECT 1 FROM accounts WHERE id=?", issuance.id());
                 var result = sql.executeQuery()) {
                return result.next();
            }
        });
        if (exists || supply != market.totalSupply() || !creatorId.equals(market.creatorId())) {
            throw new ExchangeException(ExchangeException.Code.CONFLICT, "Token supply already issued or mismatched");
        }
        post(issuance, marketId.toString(), -supply);
        post(LedgerAccount.pool(marketId), marketId.toString(), poolTokens);
        if (creatorTokens > 0) {
            post(LedgerAccount.wallet(creatorId), marketId.toString(), creatorTokens);
        }
    }

    @Override
    public void saveMarket(MarketSnapshot market) {
        guard();
        Objects.requireNonNull(market, "market");
        Optional<MarketSnapshot> previous = market(market.marketId());
        if (previous.isPresent()) {
            MarketSnapshot old = previous.get();
            if (!old.creatorId().equals(market.creatorId()) || !old.ticker().equals(market.ticker())
                || !old.name().equals(market.name()) || old.totalSupply() != market.totalSupply()
                || !old.unlockAt().equals(market.unlockAt()) || market.lpShares() > old.lpShares()
                || (old.closed() && !market.closed())) {
                throw new ExchangeException(ExchangeException.Code.CONFLICT, "Immutable market terms changed");
            }
        }
        access(() -> {
            try (var sql = statement(connection, """
                    INSERT INTO markets(id,creator_id,ticker,name,total_supply,bc_reserve,token_reserve,lp_shares,unlock_at,closed)
                    VALUES (?,?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET
                    bc_reserve=excluded.bc_reserve,token_reserve=excluded.token_reserve,
                    lp_shares=excluded.lp_shares,closed=excluded.closed
                    """, market.marketId(), market.creatorId(), market.ticker(), market.name(), market.totalSupply(),
                    market.bcReserve(), market.tokenReserve(), market.lpShares(), market.unlockAt(), market.closed())) {
                sql.executeUpdate();
            }
            return null;
        });
        touchedMarkets.add(market.marketId());
    }

    @Override
    public void savePosition(LiquidityPosition position) {
        guard();
        Objects.requireNonNull(position, "position");
        Optional<LiquidityPosition> previous = position(position.marketId());
        if (previous.isPresent() && (!previous.get().ownerId().equals(position.ownerId())
            || !previous.get().unlockAt().equals(position.unlockAt()) || position.shares() > previous.get().shares())) {
            throw new ExchangeException(ExchangeException.Code.CONFLICT, "Immutable liquidity terms changed");
        }
        access(() -> {
            try (var sql = statement(connection, """
                    INSERT INTO liquidity_positions(market_id,owner_id,shares,unlock_at) VALUES (?,?,?,?)
                    ON CONFLICT(market_id) DO UPDATE SET shares=excluded.shares
                    """, position.marketId(), position.ownerId(), position.shares(), position.unlockAt())) {
                sql.executeUpdate();
            }
            return null;
        });
        touchedMarkets.add(position.marketId());
    }

    void validate() {
        guard();
        if (deltas.isEmpty() || deltas.values().stream().anyMatch(sum -> sum.signum() != 0)) {
            throw new ExchangeException(ExchangeException.Code.UNBALANCED, "Operation has no balanced ledger movements");
        }
        for (UUID id : touchedMarkets) {
            MarketSnapshot market = requiredMarket(id);
            LiquidityPosition position = position(id).orElseThrow(() ->
                new ExchangeException(ExchangeException.Code.UNBALANCED, "Missing liquidity position"));
            if (balance(LedgerAccount.pool(id), "BC") != market.bcReserve()
                || balance(LedgerAccount.pool(id), id.toString()) != market.tokenReserve()
                || balance(LedgerAccount.issuance(id), id.toString()) != -market.totalSupply()
                || position.shares() != market.lpShares() || !position.ownerId().equals(market.creatorId())
                || !position.unlockAt().equals(market.unlockAt())) {
                throw new ExchangeException(ExchangeException.Code.UNBALANCED, "Pool, supply, and LP ledger differ");
            }
        }
    }

    void finish() {
        active = false;
    }

    private void post(LedgerAccount account, String asset, long delta) {
        validateAsset(account, asset);
        long amount = add(balance(account, asset), delta);
        if ((!account.kind().control() && amount < 0) || (account.kind().control() && amount > 0)) {
            throw new ExchangeException(ExchangeException.Code.INSUFFICIENT_BALANCE, "Insufficient balance");
        }
        access(() -> {
            try (var sql = statement(connection,
                    "INSERT INTO accounts(id,kind,allows_negative) VALUES (?,?,?) ON CONFLICT(id) DO NOTHING",
                    account.id(), account.kind(), account.kind().control())) {
                sql.executeUpdate();
            }
            try (var sql = statement(connection, """
                    INSERT INTO balances(account_id,asset,allows_negative,amount) VALUES (?,?,?,?)
                    ON CONFLICT(account_id,asset) DO UPDATE SET amount=excluded.amount
                    """, account.id(), asset, account.kind().control(), amount)) {
                sql.executeUpdate();
            }
            try (var sql = statement(connection,
                    "INSERT INTO entries(operation_id,account_id,asset,delta) VALUES (?,?,?,?)",
                    operationId, account.id(), asset, delta)) {
                sql.executeUpdate();
            }
            return null;
        });
        deltas.merge(asset, BigInteger.valueOf(delta), BigInteger::add);
    }

    private void validateAsset(LedgerAccount account, String asset) {
        guard();
        if (!"BC".equals(asset)) {
            try {
                if (!UUID.fromString(asset).toString().equals(asset)) {
                    throw new IllegalArgumentException("Noncanonical asset UUID");
                }
                requiredMarket(UUID.fromString(asset));
            } catch (IllegalArgumentException | NullPointerException failure) {
                throw new ExchangeException(ExchangeException.Code.INVALID_REQUEST, "Unknown asset", failure);
            }
        }
        if (account.kind() == AccountKind.POOL) {
            UUID market = UUID.fromString(account.id().substring("pool:".length()));
            requiredMarket(market);
            if (!"BC".equals(asset) && !market.toString().equals(asset)) {
                throw new ExchangeException(ExchangeException.Code.INVALID_REQUEST, "Asset belongs to another pool");
            }
            touchedMarkets.add(market);
        } else if ((account.kind() == AccountKind.FEES || account.kind() == AccountKind.EXTERNAL) && !"BC".equals(asset)) {
            throw new ExchangeException(ExchangeException.Code.INVALID_REQUEST, "Account only supports BC$");
        } else if (account.kind() == AccountKind.ISSUANCE
            && !account.id().substring("issuance:".length()).equals(asset)) {
            throw new ExchangeException(ExchangeException.Code.INVALID_REQUEST, "Wrong issuance account");
        }
    }

    private MarketSnapshot requiredMarket(UUID marketId) {
        return market(marketId).orElseThrow(() -> new ExchangeException(ExchangeException.Code.NOT_FOUND, "Unknown market"));
    }

    private void guard() {
        if (!active || Thread.currentThread() != owner) {
            throw new ExchangeException(ExchangeException.Code.CLOSED, "Transaction handle is expired or on another thread");
        }
        if (storageFailure != null) {
            throw SqliteExchangeLedger.storage(storageFailure);
        }
    }

    private <T> T access(SqlWork<T> work) {
        guard();
        try {
            return work.run();
        } catch (SQLException failure) {
            // A statement may fail after earlier statements changed balances. Even a callback
            // that catches the exception must not use or commit the incomplete transaction.
            storageFailure = failure;
            throw SqliteExchangeLedger.storage(failure);
        }
    }

    private static long add(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException failure) {
            throw new ExchangeException(ExchangeException.Code.OVERFLOW, "Ledger balance overflow", failure);
        }
    }

    private static void positive(long amount) {
        if (amount <= 0) {
            throw new ExchangeException(ExchangeException.Code.INVALID_AMOUNT, "Amount must be positive");
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run() throws SQLException;
    }
}
