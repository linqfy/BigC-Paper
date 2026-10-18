package dev.linqfy.bigCasares.modules.finance;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MarketSnapshot(UUID marketId, UUID creatorId, String ticker, String name,
                             long totalSupply, long bcReserve, long tokenReserve, long lpShares,
                             Instant unlockAt, boolean closed) {

    public MarketSnapshot {
        Objects.requireNonNull(marketId, "marketId");
        Objects.requireNonNull(creatorId, "creatorId");
        Objects.requireNonNull(ticker, "ticker");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(unlockAt, "unlockAt");
        if (totalSupply <= 0 || bcReserve < 0 || tokenReserve < 0 || tokenReserve > totalSupply || lpShares < 0
            || (closed ? bcReserve != 0 || tokenReserve != 0 || lpShares != 0
                : bcReserve == 0 || tokenReserve == 0 || lpShares == 0)) {
            throw new ExchangeException(ExchangeException.Code.INVALID_REQUEST, "Invalid market reserves or supply");
        }
    }

    public MarketSnapshot withReserves(long bc, long tokens, long shares, boolean isClosed) {
        return new MarketSnapshot(marketId, creatorId, ticker, name, totalSupply, bc, tokens, shares, unlockAt, isClosed);
    }
}
