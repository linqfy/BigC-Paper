package dev.linqfy.bigCasares.modules.finance;

import java.util.Objects;
import java.util.UUID;

public record OperationReceipt(UUID operationId, OperationKind kind, UUID marketId,
                               long input, long output, long feeBc, long secondaryOutput) {

    public OperationReceipt {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(kind, "kind");
        if ((kind == OperationKind.CONFIRMED_DEPOSIT) != (marketId == null)
            || input <= 0 || output < 0 || feeBc < 0 || secondaryOutput < 0) {
            throw new ExchangeException(ExchangeException.Code.INVALID_REQUEST, "Invalid operation receipt");
        }
    }
}
