# Finance: exchange core

The exchange currently provides an internal Java API for accounting, token
launches, constant-product swaps and liquidity withdrawal. It is not registered
as a gameplay module: `/bolsa`, Vault transfers, authentication, Java inventories,
Bedrock forms, ORO, staking and market events are subsequent integration work.
Installing this JAR does not enable trading or move player balances.

The implementation is under
[`modules/finance/`](../src/main/java/dev/linqfy/bigCasares/modules/finance/).
The approved [design](superpowers/specs/2026-10-18-shitcoin-exchange-core-design.md)
and [implementation plan](superpowers/plans/2026-10-18-shitcoin-exchange-core.md)
record this first delivery's scope.

## Units and quotes

Every monetary parameter is an integer number of base units:

| Asset | Decimal places | One displayed unit |
| --- | --- | --- |
| BC$ | 2 | `100` |
| Each token | 6 | `1_000_000` |
| LP shares | 0 | `1` |

`FixedAmount.parsePositive(value, decimals)` rejects zero, negative values,
exponents, excess decimal places and overflow. `FixedAmount.format(units, decimals)`
formats without floating-point arithmetic. Both use a decimal point regardless
of server locale. A boundary adapter must translate player-facing locale choices
before parsing and must honor the economy provider's supported precision.

`AmmMath.buy` and `sell` return an immutable `TradeQuote`; `withdraw` returns a
`LiquidityQuote`. Products and intermediate sums use `BigInteger`. Outputs round
down, fees round up, and unrepresentable final reserves are rejected.

For a buy, the BC$ fee is removed from input before calculating token output.
For a sell, token input determines gross BC$ output and the fee is removed from
that gross output. Fees go to the `fees` account, outside pool reserves. This
implementation's segregated fees do not automatically compound inside the pool.

Trades require a strictly positive `minimumOutput`. Execution recalculates the
quote inside the SQLite write transaction; a stale displayed quote cannot bypass
the minimum. The marginal reserve ratio is not a promise of liquidation value.

## Internal API

Create a `SqliteExchangeLedger(Path)`, pass it to
`ExchangeService(ExchangeLedger, ExchangeSettings, Clock)`, and close the ledger
when its owner stops. The future server path is
`plugins/BigCasares/data/finance/exchange.db`; no runtime code opens it yet.

| Service method | Input and result |
| --- | --- |
| `recordConfirmedDeposit` | UUID of a confirmed external debit, player UUID and BC$ base units; returns the credited amount |
| `launch` | `LaunchRequest`: operation/creator IDs, ticker, name, BC$ liquidity, pool/creator token allocation and lock duration |
| `buy` | `TradeRequest`: operation/player/market IDs, gross BC$ input and minimum token output |
| `sell` | `TradeRequest`: operation/player/market IDs, token input and minimum net BC$ output |
| `withdrawLiquidity` | `LiquidityRequest`: operation/player/market IDs and shares to burn |

Every method returns an `OperationReceipt`. `input` uses the input asset's units;
`output` uses the output asset's units. A launch reports BC$ liquidity as input
and the creator's retained tokens as output. `feeBc` always uses BC$ base units.
For LP withdrawal, `output` is BC$ and `secondaryOutput` is returned tokens; other
operations have zero `secondaryOutput`. A launch's operation ID is its market ID.

The ledger exposes immutable market, position, receipt and entry snapshots, and
account balances through `LedgerAccount` factories. The low-level transaction API
is for trusted domain/integration code. Player interfaces must use the service
and verify identity and permissions before submitting requests.

### External confirmation boundary

`recordConfirmedDeposit` records an external debit that the caller has **already
confirmed**. It does not call Vault, check a Vault balance, authenticate a player,
or prove that an external provider moved funds. Tests supply simulated
confirmations. It must not be exposed as an admin credit or player command.

The integration must persist the confirmation UUID and reuse it for all retries.
A different UUID represents a different confirmed debit. SQLite cannot deduplicate
two unrelated UUIDs that a caller incorrectly assigns to the same provider payment.
The future Vault adapter must journal payment attempts and preserve ambiguous
results for reconciliation, rather than automatically retrying an uncertain debit
or payout. There is no atomic transaction spanning SQLite and Vault.

## Launch and liquidity rules

`ExchangeSettings.defaults()` supplies these values; this core has no YAML loader:

| Setting | Default |
| --- | --- |
| Launch fee | 500 BC$ (`50_000`) |
| Minimum initial liquidity | 1,000 BC$ (`100_000`) |
| Active markets per creator | 1 |
| Total active markets | 6 |
| Trading fee | 150 basis points (1.5%) |

Tickers use 2–12 ASCII letters/digits, normalize with `Locale.ROOT`, and are
permanently unique; BC and ORO are reserved. Names use 1–32 visible Unicode code
points and reject control/format characters and legacy color codes. Supply is
the exact sum of pool and creator allocations and cannot be minted again.

Known metadata limitation: a name containing only nonbreaking spaces (U+00A0)
is currently accepted. Rejecting names made entirely of Unicode separators is
deferred from this delivery; tickers remain mandatory ASCII identifiers.

The creator owns 1,000,000 initial LP shares. This version has one nontransferable
LP position, with no additional liquidity contributions. `Duration.ZERO` leaves
it unlocked; other lock durations must be between 1 and 48 hours. All shares
share that initial lock. Withdrawal is allowed exactly at the expiration instant.

Withdrawal burns shares and returns both reserves proportionally. A full burn
returns every remaining unit and closes the pool. Closed markets retain their
history and ticker but no longer count toward active-market limits. Holders keep
their tokens, including after a complete liquidity rug. A creator's dev dump is
an ordinary sale of tokens the creator actually owns.

## Persistence and failure contracts

SQLite uses schema version 1, WAL, `synchronous=FULL`, foreign keys and a 5-second
busy timeout. Unknown schema versions fail to open and are not recreated.
The JDBC dependency is pinned to `org.xerial:sqlite-jdbc:3.50.3.0` in Gradle and
`plugin.yml` libraries; the regular plugin JAR does not shade this dependency.

Each write acquires `BEGIN IMMEDIATE` before inspecting receipts, balances,
limits or reserves. It commits operation metadata, paired entries, balances,
market state and LP state together. Pool balances and immutable supply are checked
against the snapshots before commit. Failure rolls back the whole transaction.
Any JDBC error invalidates the transaction even if its callback catches the
original exception; later access and commit are rejected.
Two ledger instances on the same file share SQLite's writer lock.

An operation UUID and SHA-256 fingerprint identify the request. Replaying the same
normalized request returns the original stored receipt without moving funds,
including after a restart, pool closure or settings change. Reusing the UUID with
other inputs or an operation of another type returns `CONFLICT`.

Spendable accounts never go negative. External BC$ and token issuance have signed,
nonspendable control accounts, so entries sum to zero independently for each asset.
Balance changes use exact overflow checks. `ExchangeException.code()` distinguishes
invalid amounts, insufficient funds, slippage, locks, limits, conflicts, closed
state and storage failures. Rejected operations have no receipt or entries and
can be resubmitted after their conditions are corrected.

One ledger serializes its JDBC connection. `close()` waits for an active callback,
then rejects reads/writes; nested writes and retaining transaction handles for
later use are rejected. The future Paper owner must put this blocking I/O on a
dedicated executor. Provider calls that require Bukkit's main thread must be
scheduled separately on that thread.

## Verification

With Java 25 in `JAVA_HOME` and `PATH`:

```bash
bash gradlew test --tests 'dev.linqfy.bigCasares.modules.finance.*'
bash gradlew --no-daemon clean build
```

Tests cover exact math, fees, limits, slippage, emission, idempotency, rollback,
reopening, concurrent connections, LP expiration and process death before commit.
A subprocess test terminates its own JVM after an uncommitted ledger mutation,
then checks that reopening sees only committed balances and reserves.
These tests do not start Paper, connect to Vault or exercise Java/Bedrock clients.
