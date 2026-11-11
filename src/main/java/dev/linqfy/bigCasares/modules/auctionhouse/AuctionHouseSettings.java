package dev.linqfy.bigCasares.modules.auctionhouse;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.Duration;

/** Values read from the {@code auction-house} section of config.yml. */
public record AuctionHouseSettings(
    int maxListingsPerPlayer,
    long minPrice,
    long maxPrice,
    int taxPercent,
    Duration listingDuration
) {
    public static final String SECTION = "auction-house";

    public AuctionHouseSettings {
        // "Mis subastas" shows every listing of a player on one page of 45 slots.
        maxListingsPerPlayer = Math.clamp(maxListingsPerPlayer, 1, 45);
        minPrice = Math.max(1, minPrice);
        maxPrice = Math.max(minPrice, maxPrice);
        taxPercent = Math.clamp(taxPercent, 0, 100);
        if (listingDuration == null || listingDuration.isNegative() || listingDuration.isZero()) {
            listingDuration = Duration.ofHours(48);
        }
    }

    public static AuctionHouseSettings defaults() {
        return new AuctionHouseSettings(10, 1, 1_000_000_000L, 5, Duration.ofHours(48));
    }

    public static AuctionHouseSettings load(FileConfiguration config) {
        AuctionHouseSettings defaults = defaults();
        ConfigurationSection section = config == null ? null : config.getConfigurationSection(SECTION);
        if (section == null) {
            return defaults;
        }
        return new AuctionHouseSettings(
            section.getInt("max-listings-per-player", defaults.maxListingsPerPlayer()),
            section.getLong("min-price", defaults.minPrice()),
            section.getLong("max-price", defaults.maxPrice()),
            section.getInt("tax-percent", defaults.taxPercent()),
            Duration.ofHours(Math.max(1, section.getLong("duration-hours", defaults.listingDuration().toHours())))
        );
    }

    /** Part of the price kept by the server when an item sells; rounded down. */
    public long taxOf(long price) {
        return Math.multiplyExact(price, (long) taxPercent) / 100;
    }
}
