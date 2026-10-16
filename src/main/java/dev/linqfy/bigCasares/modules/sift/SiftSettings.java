package dev.linqfy.bigCasares.modules.sift;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code the-sift} configuration. Invalid values fall back to defaults with a
 * warning instead of disabling the dimension; values that change the datapack
 * (seed salt, tides lengths) take effect after a restart.
 */
public record SiftSettings(
    long seedSalt,
    double worldBorderDiameter,
    String entryPermission,
    int travelCooldownSeconds,
    CraftedPortal craftedPortal,
    boolean ancientCityRitual,
    Tides tides,
    Ichor ichor,
    boolean blubs,
    List<String> warnings
) {

    public record CraftedPortal(boolean enabled, String frameBlock, String activationItem) {
    }

    public record Tides(boolean enabled, int flowTicks, int endureTicks, int thriveTicks) {
    }

    public record Ichor(boolean regeneration, boolean slowness) {
    }

    public SiftSettings {
        warnings = List.copyOf(warnings);
    }

    public static SiftSettings defaults() {
        return load(null);
    }

    public static SiftSettings load(ConfigurationSection root) {
        List<String> warnings = new ArrayList<>();
        ConfigurationSection section = root == null ? null : root.getConfigurationSection("the-sift");
        long seedSalt = section == null ? 0L : section.getLong("seed-salt", 0L);
        double border = number(section, "world-border-diameter", 60_000.0, 64.0, 5.9999968E7, warnings);
        String permission = section == null ? "" : section.getString("entry-permission", "").trim();
        int cooldown = (int) number(section, "travel-cooldown-seconds", 5, 0, 3600, warnings);

        ConfigurationSection portal = section == null ? null : section.getConfigurationSection("crafted-portal");
        CraftedPortal craftedPortal = new CraftedPortal(
            portal == null || portal.getBoolean("enabled", true),
            id(portal, "frame-block", "minecraft:crying_obsidian", warnings),
            id(portal, "activation-item", "minecraft:echo_shard", warnings));

        boolean ritual = section == null || section.getBoolean("ancient-city-ritual.enabled", true);

        ConfigurationSection tidesSection = section == null ? null : section.getConfigurationSection("tides");
        Tides tides = new Tides(
            tidesSection == null || tidesSection.getBoolean("enabled", true),
            (int) number(tidesSection, "flow-ticks", SiftDimension.DAY_NIGHT_PERIOD, 1200, 1_000_000, warnings),
            (int) number(tidesSection, "endure-ticks", SiftDimension.DAY_NIGHT_PERIOD, 1200, 1_000_000, warnings),
            (int) number(tidesSection, "thrive-ticks", SiftDimension.DAY_NIGHT_PERIOD, 1200, 1_000_000, warnings));

        ConfigurationSection ichorSection = section == null ? null : section.getConfigurationSection("ichor");
        Ichor ichor = new Ichor(
            ichorSection == null || ichorSection.getBoolean("regeneration", true),
            ichorSection == null || ichorSection.getBoolean("slowness", true));
        boolean blubs = section == null || section.getBoolean("blubs.enabled", true);
        return new SiftSettings(seedSalt, border, permission.isEmpty() ? null : permission, cooldown,
            craftedPortal, ritual, tides, ichor, blubs, warnings);
    }

    private static double number(ConfigurationSection section, String key, double fallback, double min, double max,
                                 List<String> warnings) {
        if (section == null || !section.isSet(key)) {
            return fallback;
        }
        if (!section.isInt(key) && !section.isDouble(key) && !section.isLong(key)) {
            warnings.add("the-sift." + key + " is not a number; using " + fallback);
            return fallback;
        }
        double value = section.getDouble(key);
        if (value < min || value > max) {
            warnings.add("the-sift." + key + " must be within [" + min + ", " + max + "]; using " + fallback);
            return fallback;
        }
        return value;
    }

    private static String id(ConfigurationSection section, String key, String fallback, List<String> warnings) {
        if (section == null || !section.isSet(key)) {
            return fallback;
        }
        String value = section.getString(key, fallback).trim().toLowerCase(java.util.Locale.ROOT);
        if (!value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            warnings.add("the-sift.crafted-portal." + key + " must be a namespaced id; using " + fallback);
            return fallback;
        }
        return value;
    }
}
