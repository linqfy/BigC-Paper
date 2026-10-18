package dev.linqfy.bigCasares.modules.finance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public final class SqliteExchangeLedger implements ExchangeLedger {

    private final Connection connection;
    private boolean closed;
    private boolean inTransaction;

    public SqliteExchangeLedger(Path database) {
        Objects.requireNonNull(database, "database");
        Connection candidate = null;
        try {
            Path absolute = database.toAbsolutePath();
            Files.createDirectories(absolute.getParent());
            candidate = DriverManager.getConnection("jdbc:sqlite:" + absolute);
            ExchangeSchema.initialize(candidate);
            this.connection = candidate;
        } catch (SQLException | IOException failure) {
            if (candidate != null) {
                try {
                    candidate.close();
                } catch (SQLException closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            throw new ExchangeException(ExchangeException.Code.STORAGE, "Cannot open exchange ledger", failure);
        }
    }

    @Override
    public synchronized long balance(LedgerAccount account, String asset) {
        ensureOpen();
        try {
            return readBalance(connection, account, asset);
        } catch (SQLException failure) {
            throw storage(failure);
        }
    }

    @Override
    public synchronized Optional<MarketSnapshot> market(UUID marketId) {
        ensureOpen();
        try {
            return readMarket(connection, marketId);
        } catch (SQLException failure) {
            throw storage(failure);
        }
    }

    @Override
    public synchronized List<MarketSnapshot> markets() {
        ensureOpen();
        try (var sql = connection.createStatement();
             var result = sql.executeQuery("SELECT * FROM markets ORDER BY ticker")) {
            List<MarketSnapshot> markets = new ArrayList<>();
            while (result.next()) {
                markets.add(mapMarket(result));
            }
            return List.copyOf(markets);
        } catch (SQLException failure) {
            throw storage(failure);
        }
    }

    @Override
    public synchronized Optional<LiquidityPosition> position(UUID marketId) {
        ensureOpen();
        try {
            return readPosition(connection, marketId);
        } catch (SQLException failure) {
            throw storage(failure);
        }
    }

    @Override
    public synchronized Optional<OperationReceipt> operation(UUID operationId) {
        ensureOpen();
        try (var sql = statement(connection, "SELECT * FROM operations WHERE id=?", operationId);
             var result = sql.executeQuery()) {
            return result.next() ? Optional.of(mapReceipt(result)) : Optional.empty();
        } catch (SQLException failure) {
            throw storage(failure);
        }
    }

    @Override
    public synchronized List<LedgerEntry> entries(UUID operationId) {
        ensureOpen();
        try (var sql = statement(connection, """
                SELECT e.*, a.kind FROM entries e JOIN accounts a ON a.id=e.account_id
                WHERE e.operation_id=? ORDER BY e.sequence
                """, operationId);
             var result = sql.executeQuery()) {
            List<LedgerEntry> entries = new ArrayList<>();
            while (result.next()) {
                entries.add(new LedgerEntry(operationId,
                    new LedgerAccount(result.getString("account_id"), AccountKind.valueOf(result.getString("kind"))),
                    result.getString("asset"), result.getLong("delta")));
            }
            return List.copyOf(entries);
        } catch (SQLException failure) {
            throw storage(failure);
        }
    }

    @Override
    public synchronized OperationReceipt transact(UUID operationId, String fingerprint, Instant now,
                                                   Function<ExchangeTransaction, OperationReceipt> work) {
        ensureOpen();
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(work, "work");
        if (fingerprint == null || fingerprint.isBlank() || inTransaction) {
            throw new ExchangeException(ExchangeException.Code.CONFLICT, "Invalid or nested ledger transaction");
        }
        // Begin before reading balances, reserves, or the deduplication record, including across connections.
        try (var sql = connection.createStatement()) {
            sql.execute("BEGIN IMMEDIATE");
        } catch (SQLException failure) {
            throw storage(failure);
        }
        inTransaction = true;
        SqliteExchangeTransaction transaction = new SqliteExchangeTransaction(connection, operationId);
        try {
            OperationReceipt result;
            try (var sql = statement(connection, "SELECT * FROM operations WHERE id=?", operationId);
                 var existing = sql.executeQuery()) {
                if (existing.next()) {
                    if (!fingerprint.equals(existing.getString("fingerprint"))) {
                        throw new ExchangeException(ExchangeException.Code.CONFLICT, "Operation UUID has different input");
                    }
                    result = mapReceipt(existing);
                } else {
                    result = null;
                }
            }
            if (result == null) {
                try (var sql = statement(connection,
                        "INSERT INTO operations(id,fingerprint,created_at) VALUES (?,?,?)", operationId, fingerprint, now)) {
                    sql.executeUpdate();
                }
                result = Objects.requireNonNull(work.apply(transaction), "operation receipt");
                if (!result.operationId().equals(operationId)) {
                    throw new ExchangeException(ExchangeException.Code.CONFLICT, "Receipt UUID does not match operation");
                }
                transaction.validate();
                try (var sql = statement(connection, """
                        UPDATE operations SET kind=?,market_id=?,input=?,output=?,fee_bc=?,secondary_output=? WHERE id=?
                        """, result.kind(), result.marketId(), result.input(), result.output(), result.feeBc(),
                        result.secondaryOutput(), operationId)) {
                    sql.executeUpdate();
                }
            }
            try (var sql = connection.createStatement()) {
                sql.execute("COMMIT");
            }
            return result;
        } catch (SQLException failure) {
            rollback(failure);
            throw storage(failure);
        } catch (RuntimeException | Error failure) {
            rollback(failure);
            throw failure;
        } finally {
            transaction.finish();
            inTransaction = false;
        }
    }

    @Override
    public synchronized void close() {
        if (inTransaction) {
            throw new ExchangeException(ExchangeException.Code.CONFLICT, "Cannot close ledger from its callback");
        }
        if (!closed) {
            closed = true;
            try {
                connection.close();
            } catch (SQLException failure) {
                throw storage(failure);
            }
        }
    }

    static long readBalance(Connection connection, LedgerAccount account, String asset) throws SQLException {
        try (var sql = statement(connection, "SELECT amount FROM balances WHERE account_id=? AND asset=?", account.id(), asset);
             var result = sql.executeQuery()) {
            return result.next() ? result.getLong(1) : 0;
        }
    }

    static Optional<MarketSnapshot> readMarket(Connection connection, UUID marketId) throws SQLException {
        try (var sql = statement(connection, "SELECT * FROM markets WHERE id=?", marketId);
             var result = sql.executeQuery()) {
            return result.next() ? Optional.of(mapMarket(result)) : Optional.empty();
        }
    }

    static Optional<LiquidityPosition> readPosition(Connection connection, UUID marketId) throws SQLException {
        try (var sql = statement(connection, "SELECT * FROM liquidity_positions WHERE market_id=?", marketId);
             var result = sql.executeQuery()) {
            return result.next() ? Optional.of(new LiquidityPosition(marketId,
                UUID.fromString(result.getString("owner_id")), result.getLong("shares"),
                Instant.parse(result.getString("unlock_at")))) : Optional.empty();
        }
    }

    static PreparedStatement statement(Connection connection, String query, Object... values) throws SQLException {
        PreparedStatement sql = connection.prepareStatement(query);
        try {
            for (int index = 0; index < values.length; index++) {
                Object value = values[index];
                sql.setObject(index + 1, value instanceof UUID || value instanceof Instant || value instanceof Enum<?>
                    ? value.toString() : value);
            }
            return sql;
        } catch (SQLException | RuntimeException failure) {
            sql.close();
            throw failure;
        }
    }

    static ExchangeException storage(SQLException failure) {
        return new ExchangeException(ExchangeException.Code.STORAGE, "Exchange storage operation failed", failure);
    }

    private static MarketSnapshot mapMarket(ResultSet result) throws SQLException {
        return new MarketSnapshot(UUID.fromString(result.getString("id")), UUID.fromString(result.getString("creator_id")),
            result.getString("ticker"), result.getString("name"), result.getLong("total_supply"),
            result.getLong("bc_reserve"), result.getLong("token_reserve"), result.getLong("lp_shares"),
            Instant.parse(result.getString("unlock_at")), result.getInt("closed") == 1);
    }

    private static OperationReceipt mapReceipt(ResultSet result) throws SQLException {
        String market = result.getString("market_id");
        return new OperationReceipt(UUID.fromString(result.getString("id")),
            OperationKind.valueOf(result.getString("kind")), market == null ? null : UUID.fromString(market),
            result.getLong("input"), result.getLong("output"), result.getLong("fee_bc"), result.getLong("secondary_output"));
    }

    private void ensureOpen() {
        if (closed) {
            throw new ExchangeException(ExchangeException.Code.CLOSED, "Exchange ledger is closed");
        }
    }

    private void rollback(Throwable failure) {
        try (var sql = connection.createStatement()) {
            sql.execute("ROLLBACK");
        } catch (SQLException rollback) {
            failure.addSuppressed(rollback);
        }
    }
}
