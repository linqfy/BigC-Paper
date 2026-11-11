package dev.linqfy.bigCasares.modules.auth;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** One YAML file per account under {@code data/auth/accounts}, keyed by the lower-cased name. */
public final class AuthAccountStore {

    private final Path directory;
    private final Map<String, Optional<AuthAccount>> cache = new ConcurrentHashMap<>();

    public AuthAccountStore(Path directory) {
        this.directory = directory;
    }

    public Optional<AuthAccount> find(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        return cache.computeIfAbsent(key, this::load);
    }

    public void put(AuthAccount account) {
        cache.put(account.key(), Optional.of(account));
        save(account);
    }

    public boolean delete(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        cache.put(key, Optional.empty());
        try {
            return Files.deleteIfExists(file(key));
        } catch (IOException ex) {
            throw new IllegalStateException("Could not delete auth account " + key, ex);
        }
    }

    public void save(AuthAccount account) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", account.name());
        yaml.set("registered-at", account.registeredAtMillis());
        writeSecret(yaml, "password", account.password());
        yaml.set("totp.secret", account.totpSecret());
        if (account.totpSecret() != null) {
            yaml.set("totp.last-step", account.totpLastStep());
        }
        yaml.set("recovery-codes", account.recoveryDigests());
        byte[] ring = account.pgpKeyRing();
        if (ring != null) {
            yaml.set("pgp.key-ring", Base64.getEncoder().encodeToString(ring));
            yaml.set("pgp.fingerprint", account.pgpFingerprint());
        }
        writeSecret(yaml, "pattern", account.pattern());
        yaml.set("premium.enabled", account.premium());
        yaml.set("premium.uuid", account.premiumId() == null ? null : account.premiumId().toString());
        yaml.set("trusted-friends", account.trustedFriends().stream().sorted().toList());
        yaml.set("require-two-factors", account.requireTwoFactors());
        yaml.set("session-resume", account.sessionResume());
        yaml.set("discord-login", account.discordLogin());
        yaml.set("last-login.address", account.lastAddressHash());
        yaml.set("last-login.at", account.lastLoginAtMillis());
        try {
            Files.createDirectories(directory);
            Path target = file(account.key());
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(temporary, yaml.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Could not save auth account " + account.key(), ex);
        }
    }

    private Optional<AuthAccount> load(String key) {
        Path path = file(key);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException | InvalidConfigurationException ex) {
            throw new IllegalStateException("Could not read auth account " + key, ex);
        }
        AuthAccount account = new AuthAccount(yaml.getString("name", key), yaml.getLong("registered-at"));
        account.password(readSecret(yaml.getConfigurationSection("password")));
        String totp = yaml.getString("totp.secret");
        if (totp != null) {
            account.totpSecret(totp);
            account.totpLastStep(yaml.getLong("totp.last-step", Long.MIN_VALUE));
        }
        account.recoveryDigests(yaml.getStringList("recovery-codes"));
        String ring = yaml.getString("pgp.key-ring");
        if (ring != null) {
            account.pgpKey(Base64.getDecoder().decode(ring), yaml.getString("pgp.fingerprint"));
        }
        account.pattern(readSecret(yaml.getConfigurationSection("pattern")));
        String premiumId = yaml.getString("premium.uuid");
        account.premium(yaml.getBoolean("premium.enabled"), premiumId == null ? null : UUID.fromString(premiumId));
        yaml.getStringList("trusted-friends").forEach(account::trust);
        account.requireTwoFactors(yaml.getBoolean("require-two-factors"));
        account.sessionResume(yaml.getBoolean("session-resume", true));
        account.discordLogin(yaml.getBoolean("discord-login", true));
        account.recordLogin(yaml.getString("last-login.address"), yaml.getLong("last-login.at"));
        return Optional.of(account);
    }

    private Path file(String key) {
        String safe = key.matches("[a-z0-9_.\\-]{1,32}") && !key.startsWith(".")
            ? key
            : "_" + HexFormat.of().formatHex(key.getBytes(StandardCharsets.UTF_8));
        return directory.resolve(safe + ".yml");
    }

    private static void writeSecret(YamlConfiguration yaml, String path, PasswordHasher.HashedSecret secret) {
        if (secret == null) {
            return;
        }
        yaml.set(path + ".algorithm", secret.algorithm());
        yaml.set(path + ".iterations", secret.iterations());
        yaml.set(path + ".salt", secret.salt());
        yaml.set(path + ".hash", secret.hash());
    }

    private static PasswordHasher.HashedSecret readSecret(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        return new PasswordHasher.HashedSecret(
            section.getString("algorithm"), section.getInt("iterations"),
            section.getString("salt"), section.getString("hash"));
    }
}
