package dev.linqfy.bigCasares.modules.alexscaves.config;

import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.VoronoiRarity.RarityParameters;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Effective settings of the {@code alexs-caves} module, loaded from {@code alexs-caves.yml}. */
public record AlexsCavesSettings(
    WorldgenSettings worldgen,
    SpawningSettings spawning,
    EffectSettings effects,
    MagnetismSettings magnetism,
    EnchantmentSettings enchantments,
    BlockSettings blocks,
    IntegrationSettings integration,
    ForlornSettings forlorn
) {
    public static AlexsCavesSettings defaults() {
        return new AlexsCavesSettings(WorldgenSettings.defaults(), SpawningSettings.defaults(), EffectSettings.defaults(),
            MagnetismSettings.defaults(), EnchantmentSettings.defaults(), BlockSettings.defaults(),
            IntegrationSettings.defaults(), ForlornSettings.defaults());
    }

    /** {@code possessPlayers}: mod's {@code totem_of_possession_works_on_players}; off by default (griefing risk). */
    public record ForlornSettings(boolean possessPlayers) {
        public static ForlornSettings defaults() {
            return new ForlornSettings(false);
        }
    }

    public record WorldgenSettings(
        boolean enabled,
        boolean installDatapack,
        List<String> worlds,
        RarityParameters rarity,
        Set<CaveBiomeId> enabledBiomes,
        Map<CaveBiomeId, Integer> minSpawnDistance
    ) {
        public WorldgenSettings {
            worlds = List.copyOf(worlds);
            enabledBiomes = enabledBiomes.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(enabledBiomes));
            minSpawnDistance = Map.copyOf(minSpawnDistance);
        }

        public static WorldgenSettings defaults() {
            EnumMap<CaveBiomeId, Integer> distances = new EnumMap<>(CaveBiomeId.class);
            for (CaveBiomeId biome : CaveBiomeId.values()) {
                distances.put(biome, biome.defaultMinSpawnDistance());
            }
            return new WorldgenSettings(true, true, List.of("world"), RarityParameters.MOD_DEFAULTS,
                EnumSet.allOf(CaveBiomeId.class), distances);
        }

        public WorldgenSettings disabled() {
            return new WorldgenSettings(false, installDatapack, worlds, rarity, enabledBiomes, minSpawnDistance);
        }
    }

    /**
     * @param caveCreatureMultiplier mod's {@code cave_creature_spawn_count_modifier}
     * @param capScale               fraction of the vanilla caps used around each player
     */
    public record SpawningSettings(
        boolean enabled,
        int intervalTicks,
        int radius,
        int attemptsPerPlayer,
        double caveCreatureMultiplier,
        double capScale
    ) {
        public static SpawningSettings defaults() {
            return new SpawningSettings(true, 40, 48, 3, 1.75, 0.5);
        }

        public SpawningSettings disabled() {
            return new SpawningSettings(false, intervalTicks, radius, attemptsPerPlayer, caveCreatureMultiplier, capScale);
        }
    }

    public record EffectSettings(boolean irradiatedEnabled, boolean hud) {
        public static EffectSettings defaults() {
            return new EffectSettings(true, true);
        }
    }

    /** Walking on magnet walls is not offered: it needs a modified client. */
    public record MagnetismSettings(boolean enabled, int maxEntitiesPerTick, int scanRadius) {
        public static MagnetismSettings defaults() {
            return new MagnetismSettings(true, 200, 6);
        }

        public MagnetismSettings disabled() {
            return new MagnetismSettings(false, maxEntitiesPerTick, scanRadius);
        }
    }

    public record EnchantmentSettings(boolean enabled) {
        public static EnchantmentSettings defaults() {
            return new EnchantmentSettings(true);
        }
    }

    /**
     * @param packetRemap    Java players (never Bedrock ones) receive module blocks that are not full cubes, and
     *                       waterloggable ones standing in water, as borrowed vanilla states that do not hide the faces
     *                       around them (needs packetevents; see client-remap.txt). Off by default until it is checked
     *                       with real clients: mining speed and sounds follow the borrowed block client-side
     */
    public record BlockSettings(boolean packetRemap) {
        public static BlockSettings defaults() {
            return new BlockSettings(false);
        }
    }

    /**
     * Links with the plugin's other systems for the module's bosses. Danger rewards use the plugin's usual
     * placement table; the economy reward is off by default and needs Vault with an economy provider.
     *
     * @param minDamagePercent minimum damage, as a percentage of the boss's maximum health, a player needs to
     *                         take a podium place (keeps a single tap from winning a prize); 0 = any damage
     * @param economyRewards   boss id to the amounts for places 1, 2 and 3. A boss written in the file replaces
     *                         its default list entirely (places it leaves out pay nothing); bosses not written keep
     *                         the defaults
     */
    public record IntegrationSettings(
        boolean dangerPlacements,
        boolean economyEnabled,
        double minDamagePercent,
        Map<String, List<Double>> economyRewards
    ) {
        public static final double MAX_REWARD = 1_000_000_000.0;
        public static final double DEFAULT_MIN_DAMAGE_PERCENT = 2.0;

        public IntegrationSettings {
            Map<String, List<Double>> copy = new LinkedHashMap<>();
            economyRewards.forEach((boss, amounts) -> copy.put(boss, List.copyOf(amounts)));
            economyRewards = Map.copyOf(copy);
        }

        public static IntegrationSettings defaults() {
            return new IntegrationSettings(true, false, DEFAULT_MIN_DAMAGE_PERCENT, Map.of(
                "luxtructosaurus", List.of(500.0, 300.0, 150.0),
                "forsaken", List.of(300.0, 200.0, 100.0),
                "hullbreaker", List.of(400.0, 250.0, 125.0),
                "tremorzilla", List.of(600.0, 400.0, 200.0)));
        }

        public IntegrationSettings withoutEconomy() {
            return new IntegrationSettings(dangerPlacements, false, minDamagePercent, economyRewards);
        }

        /** Amount paid to {@code placement} (1-based) for {@code bossId}; 0 when nothing is configured. */
        public double reward(String bossId, int placement) {
            List<Double> amounts = economyRewards.get(bossId);
            if (amounts == null || placement < 1 || placement > amounts.size()) {
                return 0.0;
            }
            return amounts.get(placement - 1);
        }
    }
}
