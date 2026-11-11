package dev.linqfy.bigCasares.modules.auctionhouse;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Stores listings and claim boxes in {@code data/auction-house.yml}, written atomically. */
final class YamlAuctionStore implements AuctionStore<ItemStack> {

    private final Path file;
    private final Logger logger;

    YamlAuctionStore(Path file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    @Override
    public AuctionSnapshot<ItemStack> load() {
        if (!Files.exists(file)) {
            return AuctionSnapshot.empty();
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file.toFile());
        } catch (IOException | InvalidConfigurationException failure) {
            // Refusing to start keeps the unreadable file (and the items inside it) from being overwritten.
            throw new IllegalStateException("Could not read " + file, failure);
        }
        List<AuctionListing<ItemStack>> listings = new ArrayList<>();
        ConfigurationSection listingSection = yaml.getConfigurationSection("listings");
        if (listingSection != null) {
            for (String key : listingSection.getKeys(false)) {
                ConfigurationSection entry = listingSection.getConfigurationSection(key);
                try {
                    listings.add(readListing(key, entry));
                } catch (RuntimeException invalid) {
                    logger.warning("[AuctionHouse] Skipping invalid listing " + key + ": " + invalid.getMessage());
                }
            }
        }
        Map<UUID, List<ItemStack>> claims = new LinkedHashMap<>();
        ConfigurationSection claimSection = yaml.getConfigurationSection("claims");
        if (claimSection != null) {
            for (String key : claimSection.getKeys(false)) {
                try {
                    List<ItemStack> items = new ArrayList<>();
                    for (Object value : claimSection.getList(key, List.of())) {
                        if (value instanceof ItemStack item) {
                            items.add(item);
                        }
                    }
                    claims.put(UUID.fromString(key), items);
                } catch (IllegalArgumentException invalid) {
                    logger.warning("[AuctionHouse] Skipping invalid claim box " + key);
                }
            }
        }
        return new AuctionSnapshot<>(yaml.getLong("next-id", 1), listings, claims);
    }

    @Override
    public void save(AuctionSnapshot<ItemStack> snapshot) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("next-id", snapshot.nextId());
        ConfigurationSection listingSection = yaml.createSection("listings");
        for (AuctionListing<ItemStack> listing : snapshot.listings()) {
            ConfigurationSection entry = listingSection.createSection(Long.toString(listing.id()));
            entry.set("seller", listing.seller().toString());
            entry.set("seller-name", listing.sellerName());
            entry.set("item", listing.item());
            entry.set("price", listing.price());
            entry.set("created-at", listing.createdAt().toEpochMilli());
            entry.set("expires-at", listing.expiresAt().toEpochMilli());
        }
        ConfigurationSection claimSection = yaml.createSection("claims");
        snapshot.claims().forEach((player, items) -> claimSection.set(player.toString(), items));
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, yaml.saveToString());
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException failure) {
            logger.log(Level.SEVERE, "[AuctionHouse] Could not save " + file, failure);
        }
    }

    private static AuctionListing<ItemStack> readListing(String key, ConfigurationSection entry) {
        if (entry == null) {
            throw new IllegalArgumentException("not a section");
        }
        ItemStack item = entry.getItemStack("item");
        if (item == null || item.getType().isAir()) {
            throw new IllegalArgumentException("missing item");
        }
        return new AuctionListing<>(
            Long.parseLong(key),
            UUID.fromString(entry.getString("seller", "")),
            entry.getString("seller-name"),
            item,
            entry.getLong("price"),
            Instant.ofEpochMilli(entry.getLong("created-at")),
            Instant.ofEpochMilli(entry.getLong("expires-at"))
        );
    }
}
