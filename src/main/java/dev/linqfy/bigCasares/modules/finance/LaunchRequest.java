package dev.linqfy.bigCasares.modules.finance;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

public record LaunchRequest(UUID operationId, UUID creatorId, String ticker, String name,
                            long bcLiquidity, long poolTokens, long creatorTokens, Duration lockDuration) {

    public LaunchRequest {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(creatorId, "creatorId");
        Objects.requireNonNull(lockDuration, "lockDuration");
    }
}
