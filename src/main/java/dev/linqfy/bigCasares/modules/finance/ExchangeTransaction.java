package dev.linqfy.bigCasares.modules.finance;

import java.util.Optional;
import java.util.UUID;

/** Valid only inside its ledger callback, on the callback thread. */
public interface ExchangeTransaction {

    long balance(LedgerAccount account, String asset);

    Optional<MarketSnapshot> market(UUID marketId);

    Optional<LiquidityPosition> position(UUID marketId);

    boolean tickerExists(String ticker);

    int activeMarketCount(UUID creatorId);

    void transfer(LedgerAccount from, LedgerAccount to, String asset, long amount);

    void creditConfirmed(UUID playerId, long amount);

    void issue(UUID marketId, UUID creatorId, long poolTokens, long creatorTokens);

    void saveMarket(MarketSnapshot market);

    void savePosition(LiquidityPosition position);
}
