package dev.linqfy.bigCasares.modules.finance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FixedAmountTest {

    @Test
    void parsesExactMoneyAndTokenUnits() {
        assertEquals(12345, FixedAmount.parsePositive("123.45", FixedAmount.BC_DECIMALS));
        assertEquals(100, FixedAmount.parsePositive("1", FixedAmount.BC_DECIMALS));
        assertEquals(1, FixedAmount.parsePositive("0.000001", FixedAmount.TOKEN_DECIMALS));
        assertEquals(Long.MAX_VALUE, FixedAmount.parsePositive("92233720368547758.07", 2));
    }

    @Test
    void rejectsInvalidOrInexactInputsInsteadOfTruncating() {
        for (String value : new String[]{"", "0", "0.00", "-1", "+1", "1e2", "NaN",
                "Infinity", "1,50", ".5", "1.", " 1", "1 ", "1.230", "92233720368547758.08"}) {
            assertThrows(ExchangeException.class, () -> FixedAmount.parsePositive(value, 2), value);
        }
        assertThrows(ExchangeException.class, () -> FixedAmount.parsePositive(null, 2));
        assertThrows(ExchangeException.class, () -> FixedAmount.parsePositive("1", -1));
        assertThrows(ExchangeException.class, () -> FixedAmount.parsePositive("1", 7));
    }

    @Test
    void formatsWithoutFloatingPointOrLocaleRounding() {
        assertEquals("0.00", FixedAmount.format(0, 2));
        assertEquals("123.45", FixedAmount.format(12345, 2));
        assertEquals("0.000001", FixedAmount.format(1, 6));
        assertEquals("92233720368547758.07", FixedAmount.format(Long.MAX_VALUE, 2));
        assertEquals("7", FixedAmount.format(7, 0));
        assertThrows(ExchangeException.class, () -> FixedAmount.format(-1, 2));
    }
}
