package dev.linqfy.bigCasares.modules.finance;

public record ExchangeSettings(long launchFeeBc, long minimumLiquidityBc, int marketsPerCreator,
                               int maximumActiveMarkets, int tradeFeeBps) {

    public ExchangeSettings {
        if (launchFeeBc < 0 || minimumLiquidityBc <= 0 || marketsPerCreator <= 0
            || maximumActiveMarkets <= 0 || tradeFeeBps < 0 || tradeFeeBps >= 10_000) {
            throw new ExchangeException(ExchangeException.Code.INVALID_REQUEST, "Invalid exchange settings");
        }
    }

    public static ExchangeSettings defaults() {
        return new ExchangeSettings(50_000, 100_000, 1, 6, 150);
    }
}
