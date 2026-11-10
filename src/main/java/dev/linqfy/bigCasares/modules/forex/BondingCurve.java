package dev.linqfy.bigCasares.modules.forex;

/**
 * Linear bonding curve scaled by a hype multiplier: {@code price(s) = base * hype * (1 + s / depth)}.
 *
 * <p>Buys and sells integrate the curve, so while the hype stays put the pool holds exactly what every holder
 * could sell for. Pumps raise the hype without adding money, which is what makes a bubble: the price promises
 * more than the pool holds and the first sellers drain it.
 */
public final class BondingCurve {

    private final double basePrice;
    private final double depth;

    public BondingCurve(double basePrice, double depth) {
        if (!(basePrice > 0) || !(depth > 0)) {
            throw new IllegalArgumentException("Invalid bonding curve");
        }
        this.basePrice = basePrice;
        this.depth = depth;
    }

    public double price(double supply, double hype) {
        return basePrice * hype * (1.0 + supply / depth);
    }

    /** Money needed to mint {@code amount} tokens on top of {@code supply}. */
    public double cost(double supply, double amount, double hype) {
        return basePrice * hype * (amount + (2.0 * supply * amount + amount * amount) / (2.0 * depth));
    }

    /** Money the curve pays for burning {@code amount} of {@code supply} tokens. */
    public double proceeds(double supply, double amount, double hype) {
        double burned = Math.min(amount, supply);
        return basePrice * hype * (burned + (2.0 * supply * burned - burned * burned) / (2.0 * depth));
    }

    /** Tokens minted for {@code money}: the positive root of {@code cost(supply, x) = money}. */
    public double tokensFor(double supply, double money, double hype) {
        if (money <= 0 || hype <= 0) {
            return 0;
        }
        double slope = 1.0 + supply / depth;
        return depth * (Math.sqrt(slope * slope + 2.0 * money / (basePrice * hype * depth)) - slope);
    }
}
