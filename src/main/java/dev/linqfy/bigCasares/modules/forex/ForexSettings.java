package dev.linqfy.bigCasares.modules.forex;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Settings of the FOREX simulation, read from the {@code forex} section of config.yml. Missing keys use the
 * defaults. Rates are fractions (0.04 is 4%); times are real minutes.
 */
public record ForexSettings(
    long cycleMinutes,
    long dayMinutes,
    double platformFee,
    long minTrade,
    Emerald emerald,
    Coins coins,
    Bubble bubble,
    long clanPassThreshold,
    Whales whales,
    Credit credit
) {

    /** The safe-haven asset. Daily growth and the chance of a correction are converted to the market cycle. */
    public record Emerald(double startPrice, double dailyGrowthMin, double dailyGrowthMax, double correctionsPerDay,
                          double correctionMin, double correctionMax, double maxDrawdown, double fee,
                          long activityReference) {
    }

    public record Coins(long creationCost, int maxPerCreator, int maxListed, double basePrice, double curveDepth,
                        double creatorFeeMin, double creatorFeeMax, double creatorFeeDefault,
                        double stakingShareDefault, long stakeLockMinutes, long dividendMinutes, double volatility,
                        double drift, double pumpChance, double pumpMin, double pumpMax, double rugPullMin,
                        double rugPullMax, long rugCooldownHours) {
    }

    /** The 50/50: only coins with enough holders and a deep enough pool are "bubbles" that get flipped. */
    public record Bubble(int minHolders, long minPool, long flipIntervalMinutes, List<Long> warningMinutes,
                         double survivePumpMin, double survivePumpMax, double collapseFactor) {
    }

    /** Holders of a large share of a top-capitalisation coin. */
    public record Whales(int topCoins, double minShare, double feeDiscount) {
    }

    /** Interest multiplier Don Casares applies: market-cap tiers lower it, every rug pull raises it. */
    public record Credit(Map<Long, Double> prestigeTiers, double rugPullPenalty, double maxMultiplier) {
    }

    public static ForexSettings defaults() {
        return load(new MemoryConfiguration());
    }

    public static ForexSettings load(ConfigurationSection root) {
        ConfigurationSection forex = section(root, "forex");

        ConfigurationSection em = section(forex, "emerald");
        double growthMin = clamp(em.getDouble("daily-growth-min", 0.005), 0, 1);
        double growthMax = clamp(em.getDouble("daily-growth-max", 0.02), growthMin, 1);
        double correctionMin = clamp(em.getDouble("correction-min", 0.02), 0, 0.9);
        double correctionMax = clamp(em.getDouble("correction-max", 0.06), correctionMin, 0.9);
        Emerald emerald = new Emerald(
            positive(em.getDouble("start-price", 100.0), "emerald.start-price"),
            growthMin,
            growthMax,
            clamp(em.getDouble("corrections-per-day", 0.25), 0, 1),
            correctionMin,
            correctionMax,
            clamp(em.getDouble("max-drawdown", 0.12), correctionMax, 0.95),
            clamp(em.getDouble("fee", 0.005), 0, 0.5),
            Math.max(1, em.getLong("activity-reference", 100_000)));

        ConfigurationSection cn = section(forex, "coins");
        double feeMin = clamp(cn.getDouble("creator-fee-min", 0.03), 0, 0.5);
        double feeMax = clamp(cn.getDouble("creator-fee-max", 0.05), feeMin, 0.5);
        double rugMin = clamp(cn.getDouble("rugpull-min", 0.80), 0, 1);
        double pumpMin = Math.max(0, cn.getDouble("pump-min", 0.25));
        Coins coins = new Coins(
            Math.max(0, cn.getLong("creation-cost", 5000)),
            Math.max(1, cn.getInt("max-per-creator", 1)),
            Math.max(1, cn.getInt("max-listed", 30)),
            positive(cn.getDouble("base-price", 1.0), "coins.base-price"),
            positive(cn.getDouble("curve-depth", 10_000), "coins.curve-depth"),
            feeMin,
            feeMax,
            clamp(cn.getDouble("creator-fee-default", 0.04), feeMin, feeMax),
            clamp(cn.getDouble("staking-share-default", 0.25), 0, 1),
            Math.max(0, cn.getLong("stake-lock-minutes", 60)),
            Math.max(1, cn.getLong("dividend-minutes", 30)),
            clamp(cn.getDouble("volatility", 0.04), 0, 1),
            clamp(cn.getDouble("drift", -0.002), -0.5, 0.5),
            clamp(cn.getDouble("pump-chance", 0.10), 0, 1),
            pumpMin,
            Math.max(pumpMin, cn.getDouble("pump-max", 0.60)),
            rugMin,
            clamp(cn.getDouble("rugpull-max", 0.90), rugMin, 1),
            Math.max(0, cn.getLong("rug-creation-cooldown-hours", 12)));

        ConfigurationSection bb = section(forex, "bubble");
        double surviveMin = Math.max(0, bb.getDouble("survive-pump-min", 0.25));
        List<Long> warnings = new ArrayList<>();
        if (bb.isList("warnings-minutes")) {
            bb.getLongList("warnings-minutes").stream().filter(value -> value > 0).distinct()
                .sorted((left, right) -> Long.compare(right, left)).forEach(warnings::add);
        } else {
            warnings.addAll(List.of(30L, 10L, 1L));
        }
        Bubble bubble = new Bubble(
            Math.max(2, bb.getInt("min-holders", 6)),
            Math.max(0, bb.getLong("min-pool", 25_000)),
            Math.max(1, bb.getLong("flip-interval-minutes", 180)),
            List.copyOf(warnings),
            surviveMin,
            Math.max(surviveMin, bb.getDouble("survive-pump-max", 0.50)),
            clamp(bb.getDouble("collapse-factor", 0.00001), 0, 1));

        ConfigurationSection wh = section(forex, "whales");
        Whales whales = new Whales(
            Math.max(0, wh.getInt("top-coins", 3)),
            clamp(wh.getDouble("min-share", 0.05), 0, 1),
            clamp(wh.getDouble("fee-discount", 0.5), 0, 1));

        ConfigurationSection cr = section(forex, "credit");
        Map<Long, Double> tiers = new TreeMap<>();
        ConfigurationSection tierSection = cr.getConfigurationSection("prestige-tiers");
        if (tierSection == null) {
            tiers.put(50_000L, 0.85);
            tiers.put(250_000L, 0.70);
            tiers.put(1_000_000L, 0.50);
        } else {
            for (String key : tierSection.getKeys(false)) {
                try {
                    tiers.put(Long.parseLong(key.trim()), clamp(tierSection.getDouble(key), 0, 1));
                } catch (NumberFormatException invalid) {
                    throw new IllegalArgumentException("forex.credit.prestige-tiers keys must be market caps: " + key);
                }
            }
        }
        double maxMultiplier = Math.max(1, cr.getDouble("max-multiplier", 3.0));
        Credit credit = new Credit(Map.copyOf(tiers), Math.max(0, cr.getDouble("rugpull-penalty", 0.5)),
            maxMultiplier);

        return new ForexSettings(
            Math.max(1, forex.getLong("cycle-minutes", 10)),
            Math.max(1, forex.getLong("day-minutes", 1440)),
            clamp(forex.getDouble("platform-fee", 0.01), 0, 0.5),
            Math.max(1, forex.getLong("min-trade", 10)),
            emerald,
            coins,
            bubble,
            Math.max(1, forex.getLong("clan-pass-threshold", 500)),
            whales,
            credit);
    }

    /** Converts a per-day fraction to the equivalent compounded per-cycle fraction. */
    public double perCycle(double dailyRate) {
        return Math.pow(1.0 + dailyRate, (double) cycleMinutes / dayMinutes) - 1.0;
    }

    /** Chance that an event happening {@code perDay} times a day happens in one cycle. */
    public double chancePerCycle(double perDay) {
        return 1.0 - Math.pow(1.0 - Math.min(perDay, 0.999999), (double) cycleMinutes / dayMinutes);
    }

    private static ConfigurationSection section(ConfigurationSection parent, String path) {
        ConfigurationSection section = parent.getConfigurationSection(path);
        return section == null ? new MemoryConfiguration() : section;
    }

    private static double positive(double value, String key) {
        if (!(value > 0)) {
            throw new IllegalArgumentException("forex." + key + " must be positive");
        }
        return value;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
