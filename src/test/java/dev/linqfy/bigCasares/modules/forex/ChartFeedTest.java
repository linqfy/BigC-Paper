package dev.linqfy.bigCasares.modules.forex;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChartFeedTest {

    @Test
    void framesFollowTheMarketAndDeadCoinsShowTheirFate() {
        UUID creator = UUID.randomUUID();
        UUID alice = UUID.randomUUID();
        long[] now = {0};
        FakeBank bank = new FakeBank().give(creator, 10_000).give(alice, 10_000);
        ForexMarket market = new ForexMarket(ForexSettings.defaults(), bank, new ScriptedRandom(), () -> now[0]);
        ChartFeed feed = new ChartFeed(market);
        market.create(creator, "MOON", "Moon Coin");

        assertEquals("vEMR", ChartFeed.asset("esmeraldas"));
        assertEquals("MOON", ChartFeed.asset("$moon"));
        long before = feed.key("MOON");
        market.buy(alice, "MOON", 1_000);
        assertNotEquals(before, feed.key("MOON"), "a trade redraws the chart");
        long afterTrade = feed.key("MOON");
        assertEquals(afterTrade, feed.key("MOON"), "nothing changed, no redraw");
        now[0] += 60_000;
        assertNotEquals(afterTrade, feed.key("MOON"), "countdowns redraw every minute");

        ChartPainter.Chart live = feed.chart("MOON");
        assertEquals("$MOON", live.title());
        assertNull(live.banner());
        assertTrue(live.status().startsWith("POZO"));
        assertTrue(feed.chart("vEMR").status().startsWith("PISO"));

        market.rugPull(creator, "MOON");
        assertEquals("RUG PULL", feed.chart("MOON").banner());
        assertEquals("NO EXISTE", feed.chart("NADA").banner());
    }

    @Test
    void countdownsFitTheStatusLine() {
        assertEquals("2h05m", ChartFeed.countdown(125 * 60_000L));
        assertEquals("50m", ChartFeed.countdown(49 * 60_000L + 1));
        assertEquals("0m", ChartFeed.countdown(-5));
    }
}
