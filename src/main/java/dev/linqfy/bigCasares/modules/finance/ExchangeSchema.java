package dev.linqfy.bigCasares.modules.finance;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

final class ExchangeSchema {

    private ExchangeSchema() {
    }

    static void initialize(Connection connection) throws SQLException {
        try (Statement sql = connection.createStatement()) {
            sql.execute("PRAGMA busy_timeout=5000");
            sql.execute("PRAGMA foreign_keys=ON");
            sql.execute("PRAGMA journal_mode=WAL");
            sql.execute("PRAGMA synchronous=FULL");
            sql.execute("BEGIN IMMEDIATE");
            try {
                int version;
                try (var result = sql.executeQuery("PRAGMA user_version")) {
                    version = result.getInt(1);
                }
                if (version != 0 && version != 1) {
                    throw new SQLException("Unsupported exchange schema version: " + version);
                }
                if (version == 0) {
                    createTables(sql);
                    sql.execute("PRAGMA user_version=1");
                }
                sql.execute("COMMIT");
            } catch (SQLException | RuntimeException failure) {
                try {
                    sql.execute("ROLLBACK");
                } catch (SQLException rollback) {
                    failure.addSuppressed(rollback);
                }
                throw failure;
            }
        }
    }

    private static void createTables(Statement sql) throws SQLException {
        sql.execute("""
            CREATE TABLE accounts (
                id TEXT PRIMARY KEY, kind TEXT NOT NULL,
                allows_negative INTEGER NOT NULL CHECK(allows_negative IN (0,1)),
                CHECK(kind IN ('WALLET','POOL','FEES','EXTERNAL','ISSUANCE')),
                CHECK(allows_negative = (kind IN ('EXTERNAL','ISSUANCE'))),
                UNIQUE(id, allows_negative)
            )
            """);
        sql.execute("""
            CREATE TABLE balances (
                account_id TEXT NOT NULL, asset TEXT NOT NULL,
                allows_negative INTEGER NOT NULL, amount INTEGER NOT NULL,
                PRIMARY KEY(account_id, asset),
                FOREIGN KEY(account_id, allows_negative) REFERENCES accounts(id, allows_negative),
                CHECK(typeof(amount)='integer'),
                CHECK((allows_negative=0 AND amount>=0) OR (allows_negative=1 AND amount<=0))
            )
            """);
        sql.execute("""
            CREATE TABLE markets (
                id TEXT PRIMARY KEY, creator_id TEXT NOT NULL, ticker TEXT NOT NULL UNIQUE,
                name TEXT NOT NULL, total_supply INTEGER NOT NULL CHECK(total_supply>0),
                bc_reserve INTEGER NOT NULL CHECK(bc_reserve>=0),
                token_reserve INTEGER NOT NULL CHECK(token_reserve>=0 AND token_reserve<=total_supply),
                lp_shares INTEGER NOT NULL CHECK(lp_shares>=0), unlock_at TEXT NOT NULL,
                closed INTEGER NOT NULL CHECK(closed IN (0,1)),
                CHECK((closed=1 AND bc_reserve=0 AND token_reserve=0 AND lp_shares=0)
                    OR (closed=0 AND bc_reserve>0 AND token_reserve>0 AND lp_shares>0))
            )
            """);
        sql.execute("""
            CREATE TABLE liquidity_positions (
                market_id TEXT PRIMARY KEY REFERENCES markets(id), owner_id TEXT NOT NULL,
                shares INTEGER NOT NULL CHECK(shares>=0), unlock_at TEXT NOT NULL
            )
            """);
        sql.execute("""
            CREATE TABLE operations (
                id TEXT PRIMARY KEY, fingerprint TEXT NOT NULL, created_at TEXT NOT NULL,
                kind TEXT, market_id TEXT REFERENCES markets(id),
                input INTEGER, output INTEGER, fee_bc INTEGER, secondary_output INTEGER
            )
            """);
        sql.execute("""
            CREATE TABLE entries (
                sequence INTEGER PRIMARY KEY, operation_id TEXT NOT NULL REFERENCES operations(id),
                account_id TEXT NOT NULL, asset TEXT NOT NULL,
                delta INTEGER NOT NULL CHECK(typeof(delta)='integer' AND delta<>0),
                FOREIGN KEY(account_id, asset) REFERENCES balances(account_id, asset)
            )
            """);
        sql.execute("CREATE INDEX entries_operation ON entries(operation_id, sequence)");
        sql.execute("CREATE INDEX markets_creator ON markets(creator_id, closed)");
    }
}
