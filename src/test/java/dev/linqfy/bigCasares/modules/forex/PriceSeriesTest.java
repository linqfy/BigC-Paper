package dev.linqfy.bigCasares.modules.forex;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceSeriesTest {

    @Test
    void candlesTrackOpenHighLowCloseAndVolume() {
        PriceSeries series = new PriceSeries(10);
        series.observe(14);
        series.observe(8);
        series.observe(12);
        series.addVolume(500);
        long version = series.version();
        series.close();

        Candle closed = series.candles().getFirst();
        assertEquals(new Candle(10, 14, 8, 12, 500), closed);
        assertEquals(Candle.flat(12), series.current(), "the next candle opens at the last close");
        assertTrue(series.version() > version);
        assertEquals(List.of(12.0, 12.0), series.closes());
    }

    @Test
    void keepsALimitedWindowAndRoundTripsEncoding() {
        PriceSeries series = new PriceSeries(1);
        for (int i = 0; i < PriceSeries.CAPACITY + 10; i++) {
            series.observe(i + 2);
            series.close();
        }
        assertEquals(PriceSeries.CAPACITY + 1, series.candles().size());
        Candle candle = new Candle(1.5, 2.25, 0.125, 2.0, 77);
        assertEquals(candle, Candle.decode(candle.encode()));
    }

    @Test
    void theMarketFeedsCandlesFromTradesAndCycles() {
        UUID creator = UUID.randomUUID();
        UUID alice = UUID.randomUUID();
        FakeBank bank = new FakeBank().give(creator, 10_000).give(alice, 50_000);
        ForexMarket market = new ForexMarket(ForexSettings.defaults(), bank, new ScriptedRandom(), () -> 0L);
        market.create(creator, "MOON", "Moon Coin");
        Shitcoin coin = market.coin("MOON").orElseThrow();
        double opening = market.price(coin);

        market.buy(alice, "MOON", 20_000);
        double peak = market.price(coin);
        market.sell(alice, "MOON", coin.balance(alice) / 2);
        Candle live = coin.series().current();
        assertEquals(opening, live.open(), 1e-12);
        assertEquals(peak, live.high(), 1e-12);
        assertEquals(market.price(coin), live.close(), 1e-12);
        assertTrue(live.volume() > 20_000);

        market.forceCycle();
        assertEquals(2, coin.series().candles().size(), "the cycle closed the candle");
        assertEquals(2, market.emerald().series().candles().size());

        market.rugPull(creator, "MOON");
        assertEquals(coin.series().candles(), market.graveyard().getFirst().candles(), "dead coins keep their chart");
    }
}
