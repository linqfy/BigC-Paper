package dev.linqfy.bigCasares.modules.forex;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BondingCurveTest {

    private final BondingCurve curve = new BondingCurve(1.0, 10_000);

    @Test
    void tokensForInvertsTheCostIntegral() {
        double tokens = curve.tokensFor(2_500, 1_234, 1.7);
        assertEquals(1_234, curve.cost(2_500, tokens, 1.7), 1e-6);
    }

    @Test
    void sellingBackWhatWasBoughtReturnsTheSameMoneyWhileHypeStaysPut() {
        double tokens = curve.tokensFor(4_000, 5_000, 1.0);
        assertEquals(5_000, curve.proceeds(4_000 + tokens, tokens, 1.0), 1e-6);
    }

    @Test
    void priceRisesWithSupplyAndScalesWithHype() {
        assertEquals(1.0, curve.price(0, 1.0), 1e-12);
        assertEquals(2.0, curve.price(10_000, 1.0), 1e-12);
        assertEquals(4.0, curve.price(10_000, 2.0), 1e-12);
        assertTrue(curve.tokensFor(0, 1_000, 1.0) > curve.tokensFor(50_000, 1_000, 1.0));
    }

    @Test
    void cannotSellMoreThanTheSupply() {
        assertEquals(curve.proceeds(100, 100, 1.0), curve.proceeds(100, 500, 1.0), 1e-12);
        assertEquals(0, curve.tokensFor(0, -5, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new BondingCurve(0, 10));
    }
}
