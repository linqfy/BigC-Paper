package dev.linqfy.bigCasares.modules.forex;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ForexAmountsTest {

    @Test
    void moneyAcceptsThousandsSeparatorsAndSuffixes() {
        assertEquals(1_000, ForexAmounts.money("1000"));
        assertEquals(1_000, ForexAmounts.money("1.000"));
        assertEquals(10_000, ForexAmounts.money("10k"));
        assertEquals(2_500_000, ForexAmounts.money("2,5m"));
        assertEquals(1_500, ForexAmounts.money("$1.5k"));
        assertThrows(ForexException.class, () -> ForexAmounts.money("0"));
        assertThrows(ForexException.class, () -> ForexAmounts.money("-5"));
        assertThrows(ForexException.class, () -> ForexAmounts.money("mucho"));
    }

    @Test
    void quantitiesAreAllAPercentageOrAPlainNumber() {
        assertEquals(80, ForexAmounts.quantity("todo").applyAsDouble(80));
        assertEquals(20, ForexAmounts.quantity("25%").applyAsDouble(80), 1e-9);
        assertEquals(12.5, ForexAmounts.quantity("12,5").applyAsDouble(80), 1e-9);
        assertThrows(ForexException.class, () -> ForexAmounts.quantity("150%"));
        assertThrows(ForexException.class, () -> ForexAmounts.quantity("NaN"));
        assertThrows(ForexException.class, () -> ForexAmounts.quantity("algo"));
    }

    @Test
    void percentagesBecomeFractions() {
        assertEquals(0.04, ForexAmounts.percent("4"), 1e-12);
        assertEquals(0.045, ForexAmounts.percent("4,5%"), 1e-12);
        assertThrows(ForexException.class, () -> ForexAmounts.percent("x"));
    }
}
