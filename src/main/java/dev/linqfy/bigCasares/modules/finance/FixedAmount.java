package dev.linqfy.bigCasares.modules.finance;

import java.math.BigDecimal;

public final class FixedAmount {

    public static final int BC_DECIMALS = 2;
    public static final int TOKEN_DECIMALS = 6;

    private FixedAmount() {
    }

    public static long parsePositive(String value, int decimals) {
        validateDecimals(decimals);
        String pattern = decimals == 0 ? "[0-9]+" : "[0-9]+(?:\\.[0-9]{1," + decimals + "})?";
        if (value == null || !value.matches(pattern)) {
            throw new ExchangeException(ExchangeException.Code.INVALID_AMOUNT, "Invalid decimal amount");
        }
        try {
            long units = new BigDecimal(value).movePointRight(decimals).longValueExact();
            if (units <= 0) {
                throw new ExchangeException(ExchangeException.Code.INVALID_AMOUNT, "Amount must be positive");
            }
            return units;
        } catch (ArithmeticException failure) {
            throw new ExchangeException(ExchangeException.Code.OVERFLOW, "Amount exceeds supported units", failure);
        }
    }

    public static String format(long units, int decimals) {
        validateDecimals(decimals);
        if (units < 0) {
            throw new ExchangeException(ExchangeException.Code.INVALID_AMOUNT, "Cannot format negative units");
        }
        return BigDecimal.valueOf(units, decimals).toPlainString();
    }

    private static void validateDecimals(int decimals) {
        if (decimals < 0 || decimals > TOKEN_DECIMALS) {
            throw new ExchangeException(ExchangeException.Code.INVALID_AMOUNT, "Unsupported decimal precision");
        }
    }
}
