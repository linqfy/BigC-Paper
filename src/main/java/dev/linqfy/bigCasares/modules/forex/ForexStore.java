package dev.linqfy.bigCasares.modules.forex;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Saves the whole market to {@code forex/market.yml}. Writes go to a temporary file first. */
public final class ForexStore {

    private final File file;
    private final Logger logger;

    public ForexStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public void save(ForexMarket market) {
        try {
            write(serialize(market));
        } catch (IOException failure) {
            logger.log(Level.SEVERE, "[Forex] Could not save the market", failure);
        }
    }

    /** Refuses to start on an unreadable file, so a later save never overwrites the players' money with nothing. */
    public void load(ForexMarket market) {
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException failure) {
            throw new IllegalStateException("[Forex] " + file + " is unreadable; fix or move it before enabling", failure);
        }
        deserialize(yaml, market, logger);
    }

    static YamlConfiguration serialize(ForexMarket market) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("next-cycle-at", market.nextCycleAt());
        EmeraldMarket emerald = market.emerald();
        yaml.set("emerald.price", emerald.price());
        yaml.set("emerald.all-time-high", emerald.allTimeHigh());
        yaml.set("emerald.history", emerald.history());
        for (Map.Entry<UUID, ForexMarket.Account> entry : market.accounts().entrySet()) {
            ForexMarket.Account account = entry.getValue();
            if (account.isEmpty()) {
                continue;
            }
            String path = "accounts." + entry.getKey();
            yaml.set(path + ".emeralds", account.emeralds);
            yaml.set(path + ".rug-pulls", account.rugPulls);
            yaml.set(path + ".last-rug-at", account.lastRugAt);
            yaml.set(path + ".aura-hidden", account.auraHidden);
        }
        for (Shitcoin coin : market.coins()) {
            String path = "coins." + coin.ticker();
            yaml.set(path + ".name", coin.name());
            yaml.set(path + ".creator", coin.creator().toString());
            yaml.set(path + ".created-at", coin.createdAt());
            yaml.set(path + ".creator-fee", coin.creatorFee());
            yaml.set(path + ".staking-share", coin.stakingShare());
            yaml.set(path + ".hype", coin.hype());
            yaml.set(path + ".supply", coin.supply());
            yaml.set(path + ".pool", coin.pool());
            yaml.set(path + ".dividend-pot", coin.dividendPot());
            yaml.set(path + ".team", coin.teamId().orElse(null));
            yaml.set(path + ".next-flip-at", coin.nextFlipAt());
            yaml.set(path + ".warnings-sent", coin.warningsSent());
            yaml.set(path + ".flips-survived", coin.flipsSurvived());
            yaml.set(path + ".last-dividend-at", coin.lastDividendAt());
            yaml.set(path + ".peak-cap", coin.peakCap());
            yaml.set(path + ".lifetime-volume", coin.lifetimeVolume());
            yaml.set(path + ".history", coin.history());
            Map<UUID, Long> locks = coin.locks();
            for (Map.Entry<UUID, Double> holding : coin.holdings().entrySet()) {
                String holder = path + ".holders." + holding.getKey();
                yaml.set(holder + ".balance", holding.getValue());
                double staked = coin.staked(holding.getKey());
                if (staked > Shitcoin.DUST) {
                    yaml.set(holder + ".staked", staked);
                    yaml.set(holder + ".locked-until", locks.getOrDefault(holding.getKey(), 0L));
                }
            }
        }
        List<DeadCoin> graves = market.graveyard();
        for (int index = 0; index < graves.size(); index++) {
            DeadCoin dead = graves.get(index);
            String path = "graveyard." + index;
            yaml.set(path + ".ticker", dead.ticker());
            yaml.set(path + ".name", dead.name());
            yaml.set(path + ".creator", dead.creator().toString());
            yaml.set(path + ".fate", dead.fate().name());
            yaml.set(path + ".peak-cap", dead.peakCap());
            yaml.set(path + ".lost-pool", dead.lostPool());
            yaml.set(path + ".died-at", dead.diedAt());
        }
        return yaml;
    }

    static void deserialize(ConfigurationSection yaml, ForexMarket market, Logger logger) {
        market.restoreClock(yaml.getLong("next-cycle-at", 0));
        market.emerald().restore(yaml.getDouble("emerald.price", 0), yaml.getDouble("emerald.all-time-high", 0),
            yaml.getDoubleList("emerald.history"));
        ConfigurationSection accounts = yaml.getConfigurationSection("accounts");
        if (accounts != null) {
            for (String id : accounts.getKeys(false)) {
                try {
                    ConfigurationSection entry = accounts.getConfigurationSection(id);
                    ForexMarket.Account account = new ForexMarket.Account();
                    account.emeralds = Math.max(0, entry.getDouble("emeralds"));
                    account.rugPulls = Math.max(0, entry.getInt("rug-pulls"));
                    account.lastRugAt = entry.getLong("last-rug-at");
                    account.auraHidden = entry.getBoolean("aura-hidden");
                    market.accounts().put(UUID.fromString(id), account);
                } catch (RuntimeException invalid) {
                    logger.warning("[Forex] Ignoring invalid account " + id);
                }
            }
        }
        ConfigurationSection coins = yaml.getConfigurationSection("coins");
        if (coins != null) {
            for (String ticker : coins.getKeys(false)) {
                try {
                    market.restoreCoin(readCoin(ticker, coins.getConfigurationSection(ticker)));
                } catch (RuntimeException invalid) {
                    logger.warning("[Forex] Ignoring invalid coin " + ticker);
                }
            }
        }
        ConfigurationSection graves = yaml.getConfigurationSection("graveyard");
        if (graves != null) {
            for (String key : graves.getKeys(false)) {
                ConfigurationSection entry = graves.getConfigurationSection(key);
                try {
                    market.restoreGrave(new DeadCoin(entry.getString("ticker"), entry.getString("name"),
                        UUID.fromString(entry.getString("creator")), DeadCoin.Fate.valueOf(entry.getString("fate")),
                        entry.getDouble("peak-cap"), entry.getLong("lost-pool"), entry.getLong("died-at")));
                } catch (RuntimeException invalid) {
                    logger.warning("[Forex] Ignoring invalid graveyard entry " + key);
                }
            }
        }
    }

    private static Shitcoin readCoin(String ticker, ConfigurationSection entry) {
        Shitcoin coin = new Shitcoin(ticker, entry.getString("name", ticker),
            UUID.fromString(entry.getString("creator")), entry.getLong("created-at"),
            entry.getDouble("creator-fee"), entry.getDouble("staking-share"));
        coin.hype(entry.getDouble("hype", 1.0));
        coin.supply(entry.getDouble("supply"));
        coin.pool(entry.getLong("pool"));
        coin.dividendPot(entry.getLong("dividend-pot"));
        coin.teamId(entry.getString("team"));
        coin.nextFlipAt(entry.getLong("next-flip-at"));
        coin.warningsSent(entry.getInt("warnings-sent"));
        coin.flipsSurvived(entry.getInt("flips-survived"));
        coin.lastDividendAt(entry.getLong("last-dividend-at", coin.createdAt()));
        coin.peakCap(entry.getDouble("peak-cap"));
        coin.lifetimeVolume(entry.getLong("lifetime-volume"));
        entry.getDoubleList("history").forEach(coin::record);
        ConfigurationSection holders = entry.getConfigurationSection("holders");
        if (holders != null) {
            for (String id : holders.getKeys(false)) {
                ConfigurationSection holder = holders.getConfigurationSection(id);
                coin.restoreHolding(UUID.fromString(id), holder.getDouble("balance"), holder.getDouble("staked"),
                    holder.getLong("locked-until"));
            }
        }
        return coin;
    }

    private void write(YamlConfiguration yaml) throws IOException {
        Path target = file.toPath();
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temporary, yaml.saveToString(), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicUnsupported) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
