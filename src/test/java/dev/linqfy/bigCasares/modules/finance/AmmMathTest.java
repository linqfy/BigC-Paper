package dev.linqfy.bigCasares.modules.finance;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmmMathTest {

    @Test
    void reproducesTheDocumentedPumpWithoutFees() {
        TradeQuote quote = AmmMath.buy(100_000, 1_000_000_000_000L, 50_000, 0, 1);
        assertEquals(333_333_333_333L, quote.output());
        assertEquals(150_000, quote.bcReserveAfter());
        assertEquals(666_666_666_667L, quote.tokenReserveAfter());
        assertEquals(0, quote.feeBc());
    }

    @Test
    void chargesMoneyFeesWithCeilingRoundingOnBothSides() {
        TradeQuote buy = AmmMath.buy(100_000, 1_000_000_000_000L, 50_000, 150, 1);
        assertEquals(750, buy.feeBc());
        assertEquals(149_250, buy.bcReserveAfter());
        TradeQuote sell = AmmMath.sell(100_000, 1_000_000, 100_000, 150, 1);
        assertEquals(137, sell.feeBc());
        assertEquals(8953, sell.output());
        assertEquals(90_910, sell.bcReserveAfter());
        assertEquals(1_100_000, sell.tokenReserveAfter());
        assertEquals(1, AmmMath.buy(100, 10000, 2, 150, 1).feeBc());
    }

    @Test
    void rejectsInvalidTradesDustSlippageAndOverflow() {
        assertThrows(ExchangeException.class, () -> AmmMath.buy(0, 100, 10, 0, 1));
        assertThrows(ExchangeException.class, () -> AmmMath.sell(100, 0, 10, 0, 1));
        assertThrows(ExchangeException.class, () -> AmmMath.buy(100, 100, 0, 0, 1));
        assertThrows(ExchangeException.class, () -> AmmMath.buy(100, 100, -1, 0, 1));
        assertThrows(ExchangeException.class, () -> AmmMath.buy(100, 100, 1, 150, 1));
        assertThrows(ExchangeException.class, () -> AmmMath.sell(1, 10000, 1, 0, 1));
        assertThrows(ExchangeException.class, () -> AmmMath.sell(100, 100, 1, 150, 1));
        assertThrows(ExchangeException.class, () -> AmmMath.buy(100, 100, 10, 0, 0));
        assertThrows(ExchangeException.class, () -> AmmMath.sell(100, 100, 10, 0, -1));
        assertThrows(ExchangeException.class, () -> AmmMath.buy(100, 100, 10, -1, 1));
        assertThrows(ExchangeException.class, () -> AmmMath.sell(100, 100, 10, 10000, 1));
        assertThrows(ExchangeException.class, () -> AmmMath.buy(100, 100, 10, 0, 10));
        assertThrows(ExchangeException.class, () -> AmmMath.sell(100, 100, 10, 0, 10));
        assertThrows(ExchangeException.class, () -> AmmMath.buy(Long.MAX_VALUE, Long.MAX_VALUE, 2, 0, 1));
        assertThrows(ExchangeException.class, () -> AmmMath.sell(Long.MAX_VALUE, Long.MAX_VALUE, 2, 0, 1));
    }

    @Test
    void usesWideIntermediateArithmeticAndNeverDecreasesPoolProduct() {
        long bc = Long.MAX_VALUE / 4;
        long tokens = Long.MAX_VALUE / 4;
        Random random = new Random(4918);
        for (int i = 0; i < 500; i++) {
            BigInteger before = product(bc, tokens);
            long input = 100 + random.nextInt(1_000_000);
            TradeQuote quote = i % 2 == 0
                ? AmmMath.buy(bc, tokens, input, 150, 1)
                : AmmMath.sell(bc, tokens, input, 150, 1);
            bc = quote.bcReserveAfter();
            tokens = quote.tokenReserveAfter();
            assertTrue(product(bc, tokens).compareTo(before) >= 0);
            assertTrue(bc > 0 && tokens > 0);
        }
    }

    @Test
    void removesLiquidityProportionallyAndReturnsEveryLastUnitOnFinalBurn() {
        LiquidityQuote partial = AmmMath.withdraw(100_001, 1_000_001, 900_000, 1_000_000);
        assertEquals(90_000, partial.bcOut());
        assertEquals(900_000, partial.tokensOut());
        assertEquals(100_000, partial.sharesAfter());
        LiquidityQuote finalBurn = AmmMath.withdraw(10_001, 100_001, 100_000, 100_000);
        assertEquals(10_001, finalBurn.bcOut());
        assertEquals(100_001, finalBurn.tokensOut());
        assertEquals(0, finalBurn.sharesAfter());
        assertThrows(ExchangeException.class, () -> AmmMath.withdraw(1, 1, 1, 100));
        assertThrows(ExchangeException.class, () -> AmmMath.withdraw(100, 100, 101, 100));
        assertThrows(ExchangeException.class, () -> AmmMath.withdraw(100, 100, 0, 100));
    }

    private static BigInteger product(long left, long right) {
        return BigInteger.valueOf(left).multiply(BigInteger.valueOf(right));
    }
}
