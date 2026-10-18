package dev.linqfy.bigCasares.modules.finance;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record LiquidityPosition(UUID marketId, UUID ownerId, long shares, Instant unlockAt) {

    public LiquidityPosition {
        Objects.requireNonNull(marketId, "marketId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(unlockAt, "unlockAt");
        if (shares < 0) {
            throw new ExchangeException(ExchangeException.Code.INVALID_AMOUNT, "Negative liquidity position");
        }
    }
}
