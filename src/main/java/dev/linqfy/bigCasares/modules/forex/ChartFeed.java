package dev.linqfy.bigCasares.modules.forex;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Turns market state into chart frames, and tells charts when the frame changed. */
final class ChartFeed {

    private static final long MINUTE = 60_000L;

    private final ForexMarket market;

    ChartFeed(ForexMarket market) {
        this.market = market;
    }

    /** {@code vEMR} for the emeralds, otherwise the normalized ticker. */
    static String asset(String raw) {
        return ForexMarket.isEmeraldAlias(raw) ? ForexMarket.EMERALD_TICKER : ForexMarket.normalizeTicker(raw);
    }

    /** Changes whenever the drawing would: a new trade or cycle, or another minute of a countdown. */
    long key(String asset) {
        long minute = market.now() / MINUTE;
        if (ForexMarket.EMERALD_TICKER.equals(asset)) {
            return market.emerald().series().version() * 1_000_003L + minute;
        }
        Optional<Shitcoin> coin = market.coin(asset);
        if (coin.isPresent()) {
            return coin.get().series().version() * 1_000_003L + minute;
        }
        return grave(asset).map(dead -> -dead.diedAt()).orElse(Long.MIN_VALUE);
    }

    ChartPainter.Chart chart(String asset) {
        String timeframe = market.settings().cycleMinutes() + "m";
        if (ForexMarket.EMERALD_TICKER.equals(asset)) {
            EmeraldMarket emerald = market.emerald();
            return new ChartPainter.Chart("$vEMR", ForexFormat.price(emerald.price()), emerald.series().candles(),
                timeframe, "PISO " + ChartPainter.compact(emerald.floor()) + "  0% RUG PULL", ChartPainter.UP, null);
        }
        Optional<Shitcoin> live = market.coin(asset);
        if (live.isPresent()) {
            Shitcoin coin = live.get();
            String status;
            int color;
            if (coin.isBubble()) {
                status = "BURBUJA 50/50 EN " + countdown(coin.nextFlipAt() - market.now());
                color = ChartPainter.BUBBLE;
            } else {
                double cap = market.marketCap(coin);
                long backing = cap < 1 ? 100 : Math.round(Math.min(9.99, coin.pool() / cap) * 100);
                status = "POZO " + ChartPainter.compact(coin.pool()) + " " + backing + "%  HOLD " + coin.holderCount();
                color = backing >= 90 ? ChartPainter.MUTED : backing >= 50 ? ChartPainter.TITLE : ChartPainter.DOWN;
            }
            return new ChartPainter.Chart("$" + coin.ticker(), ForexFormat.price(market.price(coin)),
                coin.series().candles(), timeframe, status, color, null);
        }
        return grave(asset).map(dead -> new ChartPainter.Chart("$" + dead.ticker(), "DELISTED", dead.candles(),
                timeframe, "PICO " + ChartPainter.compact(dead.peakCap()) + "  PERDIDO " + ChartPainter.compact(dead.lostPool()),
                ChartPainter.DOWN, dead.fate() == DeadCoin.Fate.RUG_PULL ? "RUG PULL" : "COLAPSO -99.999%"))
            .orElseGet(() -> new ChartPainter.Chart("$" + asset, "-", List.of(), timeframe, "", ChartPainter.MUTED,
                "NO EXISTE"));
    }

    /** Minutes only, so the status line fits: {@code 2h05m}, {@code 9m}. */
    static String countdown(long millis) {
        long minutes = Math.max(0, (millis + 59_999) / 60_000);
        return minutes >= 60 ? minutes / 60 + "h" + String.format(Locale.ROOT, "%02d", minutes % 60) + "m" : minutes + "m";
    }

    private Optional<DeadCoin> grave(String ticker) {
        return market.graveyard().stream().filter(dead -> dead.ticker().equals(ticker)).findFirst();
    }
}
