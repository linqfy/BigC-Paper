package dev.linqfy.bigCasares.modules.finance;

import java.util.Objects;
import java.util.UUID;

public record TradeRequest(UUID operationId, UUID playerId, UUID marketId, long input, long minimumOutput) {

    public TradeRequest {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(marketId, "marketId");
    }
}
