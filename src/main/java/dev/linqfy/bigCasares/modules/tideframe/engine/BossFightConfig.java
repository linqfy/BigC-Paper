package dev.linqfy.bigCasares.modules.tideframe.engine;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.BossFightSettings;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.KeyChallenge;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Reads the engine settings' boss-fight section ({@link EngineConfig}) into {@link BossFightSettings}. */
public final class BossFightConfig {
    private BossFightConfig() { }

    /** A YAML file whose top level holds boss-fight (tests and offline tools). */
    public static BossFightSettings load(Path config) {
        return load(YamlConfiguration.loadConfiguration(config.toFile()));
    }

    /** {@code root}: the engine's settings ({@link EngineConfig#read()}). */
    public static BossFightSettings load(ConfigurationSection root) {
        var section = root.getConfigurationSection("boss-fight");
        var d = BossFightSettings.defaults();
        if (section == null) return d;
        var keys = new ArrayList<KeyChallenge.Key>();
        if (section.isList("keys")) for (Object key : section.getList("keys")) {
            try { keys.add(KeyChallenge.Key.valueOf(String.valueOf(key).trim().toUpperCase(Locale.ROOT))); }
            catch (IllegalArgumentException unknown) { throw new IllegalArgumentException("boss-fight.keys: unknown key " + key + " (use W, A, S, D, SPACE, CLICK)"); }
        } else keys.addAll(d.keys());
        return new BossFightSettings(keys, section.getInt("lives", d.lives()),
                ticks(section, "time-start", d.startTicks()), ticks(section, "time-min", d.minTicks()),
                section.getInt("enrage-at", d.enragePercent()), section.getInt("hit-damage", d.hitDamage()), section.getDouble("miss-damage", d.missDamage()),
                strings(section.getConfigurationSection("colors")), numbers(section.getConfigurationSection("layout")),
                booleans(section.getConfigurationSection("show")), strings(section.getConfigurationSection("texts")),
                strings(section.getConfigurationSection("sounds")));
    }
    private static int ticks(ConfigurationSection section, String key, int fallback) {
        return section.isSet(key) ? (int) Math.round(section.getDouble(key) * 20) : fallback;
    }
    private static Map<String, String> strings(ConfigurationSection section) {
        var out = new LinkedHashMap<String, String>();
        if (section != null) for (String key : section.getKeys(false)) out.put(key, section.isString(key) ? section.getString(key) : String.valueOf(section.get(key)));
        return out;
    }
    private static Map<String, Double> numbers(ConfigurationSection section) {
        var out = new LinkedHashMap<String, Double>();
        if (section != null) for (String key : section.getKeys(false)) {
            if (!section.isDouble(key) && !section.isInt(key)) throw new IllegalArgumentException("boss-fight.layout." + key + " must be a number");
            out.put(key, section.getDouble(key));
        }
        return out;
    }
    private static Map<String, Boolean> booleans(ConfigurationSection section) {
        var out = new LinkedHashMap<String, Boolean>();
        if (section != null) for (String key : section.getKeys(false)) {
            if (!section.isBoolean(key)) throw new IllegalArgumentException("boss-fight.show." + key + " must be true or false");
            out.put(key, section.getBoolean(key));
        }
        return out;
    }
}
