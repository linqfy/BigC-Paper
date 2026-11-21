package dev.linqfy.bigCasares.modules.forex;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Virtual emeralds ($vEMR), the server's safe haven. The price grows a little every cycle, faster when the
 * market moves more money, and suffers occasional controlled corrections. The reserve defends a floor at
 * {@code maxDrawdown} below the all-time high, so a long-term holder never loses much.
 */
public final class EmeraldMarket {

    public record Step(double before, double after, boolean correction) {

        public double change() {
            return after / before - 1.0;
        }
    }

    private final ForexSettings settings;
    private final PriceSeries series;
    private double price;
    private double allTimeHigh;

    public EmeraldMarket(ForexSettings settings) {
        this.settings = settings;
        this.price = settings.emerald().startPrice();
        this.allTimeHigh = price;
        this.series = new PriceSeries(price);
    }

    public double price() {
        return price;
    }

    public double allTimeHigh() {
        return allTimeHigh;
    }

    /** The lowest price the reserve allows right now. */
    public double floor() {
        return Math.max(settings.emerald().startPrice(), allTimeHigh * (1.0 - settings.emerald().maxDrawdown()));
    }

    /** Close prices of the recent cycles, the current one last. */
    public List<Double> history() {
        return series.closes();
    }

    public PriceSeries series() {
        return series;
    }

    void restore(double restoredPrice, double restoredHigh, List<Candle> restoredCandles) {
        if (restoredPrice > 0) {
            price = restoredPrice;
        }
        allTimeHigh = Math.max(price, restoredHigh);
        series.restore(restoredCandles, Candle.flat(price));
    }

    /**
     * Advances one market cycle. {@code activity} in [0, 1] is the money moved through the forex market during
     * the cycle relative to the configured reference; it pushes growth toward the top of the daily range.
     */
    public Step step(RandomGenerator random, double activity) {
        ForexSettings.Emerald config = settings.emerald();
        double before = price;
        boolean correction = random.nextDouble() < settings.chancePerCycle(config.correctionsPerDay());
        if (correction) {
            double drop = config.correctionMin() + random.nextDouble() * (config.correctionMax() - config.correctionMin());
            price = Math.max(floor(), price * (1.0 - drop));
        } else {
            double weight = 0.5 * random.nextDouble() + 0.5 * Math.max(0, Math.min(1, activity));
            double daily = config.dailyGrowthMin() + weight * (config.dailyGrowthMax() - config.dailyGrowthMin());
            price = price * (1.0 + settings.perCycle(daily));
        }
        allTimeHigh = Math.max(allTimeHigh, price);
        series.observe(price);
        series.close();
        return new Step(before, price, correction);
    }
}
