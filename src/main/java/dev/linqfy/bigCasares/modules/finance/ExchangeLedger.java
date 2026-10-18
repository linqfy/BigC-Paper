package dev.linqfy.bigCasares.modules.finance;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** Trusted internal storage API. Player-facing code must use ExchangeService. */
public interface ExchangeLedger extends AutoCloseable {

    long balance(LedgerAccount account, String asset);

    Optional<MarketSnapshot> market(UUID marketId);

    List<MarketSnapshot> markets();

    Optional<LiquidityPosition> position(UUID marketId);

    Optional<OperationReceipt> operation(UUID operationId);

    List<LedgerEntry> entries(UUID operationId);

    OperationReceipt transact(UUID operationId, String fingerprint, Instant now,
                              Function<ExchangeTransaction, OperationReceipt> work);

    @Override
    void close();
}
