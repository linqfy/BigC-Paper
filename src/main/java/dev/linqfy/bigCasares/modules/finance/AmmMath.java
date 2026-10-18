package dev.linqfy.bigCasares.modules.finance;

import java.math.BigInteger;

public final class AmmMath {

    private static final BigInteger BASIS_POINTS = BigInteger.valueOf(10_000);

    private AmmMath() {
    }

    public static TradeQuote buy(long bcReserve, long tokenReserve, long input, int feeBps, long minimumOutput) {
        validateTrade(bcReserve, tokenReserve, input, feeBps, minimumOutput);
        long fee = fee(input, feeBps);
        long effective = input - fee;
        positive(effective);
        BigInteger bcAfter = value(bcReserve).add(value(effective));
        long output = exact(value(tokenReserve).multiply(value(effective)).divide(bcAfter));
        validateOutput(output, minimumOutput);
        return new TradeQuote(input, fee, output, exact(bcAfter), tokenReserve - output);
    }

    public static TradeQuote sell(long bcReserve, long tokenReserve, long input, int feeBps, long minimumOutput) {
        validateTrade(bcReserve, tokenReserve, input, feeBps, minimumOutput);
        BigInteger tokensAfter = value(tokenReserve).add(value(input));
        long gross = exact(value(bcReserve).multiply(value(input)).divide(tokensAfter));
        long fee = fee(gross, feeBps);
        long output = gross - fee;
        validateOutput(output, minimumOutput);
        return new TradeQuote(input, fee, output, bcReserve - gross, exact(tokensAfter));
    }

    public static LiquidityQuote withdraw(long bcReserve, long tokenReserve, long shares, long totalShares) {
        positive(bcReserve);
        positive(tokenReserve);
        positive(shares);
        positive(totalShares);
        if (shares > totalShares) {
            throw new ExchangeException(ExchangeException.Code.INVALID_TRADE, "Insufficient liquidity shares");
        }
        long bcOut = exact(value(bcReserve).multiply(value(shares)).divide(value(totalShares)));
        long tokensOut = exact(value(tokenReserve).multiply(value(shares)).divide(value(totalShares)));
        positive(bcOut);
        positive(tokensOut);
        return new LiquidityQuote(bcOut, tokensOut, totalShares - shares);
    }

    private static void validateTrade(long bc, long tokens, long input, int feeBps, long minimumOutput) {
        positive(bc);
        positive(tokens);
        positive(input);
        positive(minimumOutput);
        if (feeBps < 0 || feeBps >= 10_000) {
            throw new ExchangeException(ExchangeException.Code.INVALID_TRADE, "Invalid fee basis points");
        }
    }

    private static void validateOutput(long output, long minimumOutput) {
        positive(output);
        if (output < minimumOutput) {
            throw new ExchangeException(ExchangeException.Code.SLIPPAGE, "Output is below the requested minimum");
        }
    }

    private static long fee(long gross, int feeBps) {
        return exact(value(gross).multiply(value(feeBps)).add(BASIS_POINTS.subtract(BigInteger.ONE))
            .divide(BASIS_POINTS));
    }

    private static void positive(long value) {
        if (value <= 0) {
            throw new ExchangeException(ExchangeException.Code.INVALID_AMOUNT, "Amount must be positive");
        }
    }

    private static BigInteger value(long value) {
        return BigInteger.valueOf(value);
    }

    private static long exact(BigInteger value) {
        try {
            return value.longValueExact();
        } catch (ArithmeticException failure) {
            throw new ExchangeException(ExchangeException.Code.OVERFLOW, "Reserve exceeds supported units", failure);
        }
    }
}
