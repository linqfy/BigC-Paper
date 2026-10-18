package dev.linqfy.bigCasares.modules.finance;

import java.util.Objects;
import java.util.UUID;

public record LiquidityRequest(UUID operationId, UUID playerId, UUID marketId, long shares) {

    public LiquidityRequest {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(marketId, "marketId");
    }
}
