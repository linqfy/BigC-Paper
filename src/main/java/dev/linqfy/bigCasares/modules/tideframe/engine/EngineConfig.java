package dev.linqfy.bigCasares.modules.tideframe.engine;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The engine's settings: BigCasares' own config.yml, section {@value #PATH}, read from disk each time (so saving the
 * file and the engine's auto-reload apply changes). Options the server's file lacks come from the config.yml in the
 * JAR, as for every other module, so an older config.yml without the section runs on the defaults.
 */
public final class EngineConfig {
    public static final String PATH = "tideframe-ui.motor";
    private final Path file;
    private final Supplier<InputStream> bundled;

    /** {@code file}: plugins/BigCasares/config.yml; {@code bundled}: the config.yml inside the JAR (may give null). */
    public EngineConfig(Path file, Supplier<InputStream> bundled) {
        this.file = Objects.requireNonNull(file, "file");
        this.bundled = Objects.requireNonNull(bundled, "bundled");
    }

    /**
     * The section as it is now: the bundled section with every value of the server's file written over it. Built as
     * one plain section (not Bukkit defaults) because the engine reads with explicit fallbacks, such as
     * {@code getBoolean(key, true)}, which ignore defaults: a missing key must still read the JAR's value.
     */
    public ConfigurationSection read() {
        YamlConfiguration merged = defaults();
        ConfigurationSection target = merged.isConfigurationSection(PATH) ? merged.getConfigurationSection(PATH) : merged.createSection(PATH);
        ConfigurationSection own = YamlConfiguration.loadConfiguration(file.toFile()).getConfigurationSection(PATH);
        if (own != null) overlay(own, target);
        return target;
    }

    /** Writes {@code from} over {@code to}: leaves replace, sections merge, an empty section empties the bundled one. */
    private static void overlay(ConfigurationSection from, ConfigurationSection to) {
        for (String key : from.getKeys(false)) {
            if (from.isConfigurationSection(key)) {
                ConfigurationSection child = from.getConfigurationSection(key);
                if (child.getKeys(false).isEmpty() || !to.isConfigurationSection(key)) to.createSection(key);
                overlay(child, to.getConfigurationSection(key));
            } else {
                to.set(key, from.get(key));
            }
        }
    }

    /** Digest of the section's values: changes only when an engine option changes, not on other edits to config.yml. */
    public String fingerprint() {
        try {
            byte[] values = String.valueOf(read().getValues(true)).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(values));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private YamlConfiguration defaults() {
        try (InputStream in = bundled.get()) {
            if (in == null) return new YamlConfiguration();
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader);
            }
        } catch (IOException unreadable) {
            return new YamlConfiguration();
        }
    }
}
