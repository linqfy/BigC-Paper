package dev.linqfy.bigCasares.modules.discord;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Links Minecraft accounts to Discord users through short-lived DM codes.
 *
 * <p>Codes are generated on the server thread and verified on JDA's thread, so all
 * state is concurrent and file writes are serialized. Codes expire and each Discord
 * user gets a bounded number of wrong guesses, so a six-digit code cannot be
 * brute-forced to hijack another player's link.
 */
public class DiscordLinkService {

    static final Duration CODE_LIFETIME = Duration.ofMinutes(10);
    static final int MAX_FAILED_ATTEMPTS = 5;

    private final File dataFile;
    private final Logger logger;
    private final Clock clock;
    private final Map<UUID, Long> linkedAccounts = new ConcurrentHashMap<>();
    private final Map<String, PendingCode> pendingCodes = new ConcurrentHashMap<>();
    private final Map<UUID, String> reversePending = new ConcurrentHashMap<>();
    private final Map<Long, FailedAttempts> failures = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public DiscordLinkService(Plugin plugin) {
        this(new File(plugin.getDataFolder(), "data/discord-integration/discord-links.yml"),
            plugin.getLogger(), Clock.systemUTC());
    }

    DiscordLinkService(File dataFile, Logger logger, Clock clock) {
        this.dataFile = dataFile;
        this.logger = logger;
        this.clock = clock;
        load();
    }

    private void load() {
        if (!dataFile.isFile()) {
            return;
        }
        ConfigurationSection links = YamlConfiguration.loadConfiguration(dataFile).getConfigurationSection("links");
        if (links == null) {
            return;
        }
        for (String key : links.getKeys(false)) {
            try {
                linkedAccounts.put(UUID.fromString(key), links.getLong(key));
            } catch (IllegalArgumentException ignored) {
                logger.warning("Ignoring invalid Discord link entry: " + key);
            }
        }
    }

    private synchronized void save() {
        YamlConfiguration config = new YamlConfiguration();
        linkedAccounts.forEach((player, discordId) -> config.set("links." + player, discordId));
        try {
            File parent = dataFile.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("Cannot create " + parent);
            }
            config.save(dataFile);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not save Discord links to " + dataFile, e);
        }
    }

    public synchronized String generateCode(UUID playerUuid) {
        purgeExpired();
        String oldCode = reversePending.remove(playerUuid);
        if (oldCode != null) {
            pendingCodes.remove(oldCode);
        }
        String code;
        do {
            code = String.format(java.util.Locale.ROOT, "%06d", random.nextInt(1_000_000));
        } while (pendingCodes.containsKey(code));
        pendingCodes.put(code, new PendingCode(playerUuid, clock.instant().plus(CODE_LIFETIME)));
        reversePending.put(playerUuid, code);
        return code;
    }

    public synchronized boolean verifyCode(String code, long discordUserId) {
        Instant now = clock.instant();
        FailedAttempts attempts = failures.get(discordUserId);
        if (attempts != null && attempts.expiresAt().isAfter(now) && attempts.count() >= MAX_FAILED_ATTEMPTS) {
            return false;
        }
        PendingCode pending = pendingCodes.get(code);
        if (pending == null || !pending.expiresAt().isAfter(now)) {
            if (pending != null) {
                pendingCodes.remove(code);
                reversePending.remove(pending.playerId(), code);
            }
            int count = attempts == null || !attempts.expiresAt().isAfter(now) ? 1 : attempts.count() + 1;
            failures.put(discordUserId, new FailedAttempts(count, now.plus(CODE_LIFETIME)));
            return false;
        }
        pendingCodes.remove(code);
        reversePending.remove(pending.playerId());
        failures.remove(discordUserId);
        linkedAccounts.put(pending.playerId(), discordUserId);
        save();
        return true;
    }

    public boolean isLinked(UUID playerUuid) {
        return linkedAccounts.containsKey(playerUuid);
    }

    public Optional<Long> getDiscordId(UUID playerUuid) {
        return Optional.ofNullable(linkedAccounts.get(playerUuid));
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        pendingCodes.entrySet().removeIf(entry -> {
            if (entry.getValue().expiresAt().isAfter(now)) {
                return false;
            }
            reversePending.remove(entry.getValue().playerId(), entry.getKey());
            return true;
        });
        failures.values().removeIf(attempt -> !attempt.expiresAt().isAfter(now));
    }

    private record PendingCode(UUID playerId, Instant expiresAt) {
    }

    private record FailedAttempts(int count, Instant expiresAt) {
    }
}
