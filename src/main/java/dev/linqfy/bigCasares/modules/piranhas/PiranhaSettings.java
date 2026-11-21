package dev.linqfy.bigCasares.modules.piranhas;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @param biomes             biome keys (without namespace) where schools appear near players
 * @param spawnIntervalTicks how often each player rolls for a nearby school
 * @param spawnChance        probability of a school per roll
 * @param schoolMin          smallest school
 * @param schoolMax          largest school
 * @param maxNearby          piranhas allowed within {@link #NEARBY_RADIUS} blocks of a player before spawning stops
 * @param huntRange          distance at which piranhas notice a swimmer
 * @param speed              swim speed while hunting (blocks per tick)
 * @param damage             damage per bite
 * @param biteCooldownTicks  ticks between two bites of the same piranha
 */
record PiranhaSettings(
    Set<String> biomes,
    long spawnIntervalTicks,
    double spawnChance,
    int schoolMin,
    int schoolMax,
    int maxNearby,
    double huntRange,
    double speed,
    double damage,
    long biteCooldownTicks
) {

    static final double NEARBY_RADIUS = 32.0;

    static PiranhaSettings defaults() {
        return new PiranhaSettings(
            Set.of("jungle", "sparse_jungle", "bamboo_jungle", "swamp", "mangrove_swamp"),
            400L, 0.35, 3, 5, 8, 10.0, 0.35, 2.0, 20L);
    }

    static PiranhaSettings load(FileConfiguration config) {
        PiranhaSettings d = defaults();
        List<String> biomes = config.getStringList("piranhas.biomes");
        int min = Math.max(1, config.getInt("piranhas.school-min", d.schoolMin()));
        int max = Math.max(min, config.getInt("piranhas.school-max", d.schoolMax()));
        return new PiranhaSettings(
            biomes.isEmpty() ? d.biomes()
                : biomes.stream().map(PiranhaSettings::normalizeBiome).collect(Collectors.toUnmodifiableSet()),
            Math.max(20L, config.getLong("piranhas.spawn-interval-ticks", d.spawnIntervalTicks())),
            clamp01(config.getDouble("piranhas.spawn-chance", d.spawnChance())),
            min,
            max,
            Math.max(0, config.getInt("piranhas.max-nearby", d.maxNearby())),
            Math.max(1.0, config.getDouble("piranhas.hunt-range", d.huntRange())),
            Math.max(0.05, config.getDouble("piranhas.speed", d.speed())),
            Math.max(0.0, config.getDouble("piranhas.damage", d.damage())),
            Math.max(1L, config.getLong("piranhas.bite-cooldown-ticks", d.biteCooldownTicks()))
        );
    }

    static String normalizeBiome(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        int colon = lower.indexOf(':');
        return colon >= 0 ? lower.substring(colon + 1) : lower;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
