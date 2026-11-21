package dev.linqfy.bigCasares.modules.mythicdungeons;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * La configuracion de {@code mythic-dungeons.yml} ya validada (sin servidor). Lo que falta o es invalido queda con el
 * valor por defecto y deja un aviso en {@link #warnings()}.
 */
public record MythicDungeonSettings(
    Material altarMaterial,
    double bossOffsetY,
    int spawnerCap,
    double capRadius,
    double naturalReplaceChance,
    double lootingBonusPerLevel,
    int rewardTopPlayers,
    Map<String, BossReward> rewards,
    MythicMobCatalog mobs,
    List<String> warnings
) {

    /** Un item vanilla de premio. */
    public record RewardItem(Material material, int amount) {
    }

    /** El premio de un jefe para cada uno de los mejores en dano; la experiencia baja con el puesto. */
    public record BossReward(int xp, List<RewardItem> items) {

        public BossReward {
            items = List.copyOf(items);
        }

        /** Experiencia para el puesto (1 = toda, 2 = 75 %, 3 o mas = 50 %). */
        public int xpFor(int placement) {
            double factor = placement <= 1 ? 1.0 : placement == 2 ? 0.75 : 0.5;
            return (int) Math.round(xp * factor);
        }
    }

    public static final Material DEFAULT_ALTAR = Material.LODESTONE;

    public MythicDungeonSettings {
        rewards = Map.copyOf(rewards);
        warnings = List.copyOf(warnings);
    }

    public BossReward rewardFor(MythicCulture culture) {
        return culture.boss().map(boss -> rewards.get(boss.id())).orElseGet(() -> defaultReward(culture));
    }

    public static MythicDungeonSettings defaults() {
        return load(null);
    }

    public static MythicDungeonSettings load(ConfigurationSection root) {
        List<String> warnings = new ArrayList<>();
        ConfigurationSection config = root == null ? new org.bukkit.configuration.MemoryConfiguration() : root;

        Material altar = material(config.getString("altar.material", DEFAULT_ALTAR.name()))
            .filter(MythicDungeonSettings::isBlock)
            .orElseGet(() -> {
                warnings.add("altar.material no es un bloque valido; se usa " + DEFAULT_ALTAR.name());
                return DEFAULT_ALTAR;
            });
        double offset = finite(config.getDouble("altar.boss-offset-y", 0.0), 0.0);
        int cap = Math.max(0, config.getInt("spawns.spawner-cap", 6));
        double radius = positive(config.getDouble("spawns.cap-radius", 24.0), 24.0);
        double replace = probability(config.getDouble("spawns.natural-replace-chance", 0.6), 0.6);
        double looting = Math.max(0.0, finite(config.getDouble("fragments.looting-bonus-per-level", 0.02), 0.02));
        int top = Math.max(1, config.getInt("rewards.top-players", 3));

        Map<String, BossReward> rewards = new LinkedHashMap<>();
        for (MythicCulture culture : MythicCulture.bossCultures()) {
            ConfigurationSection section = config.getConfigurationSection("rewards." + culture.requireBoss().id());
            BossReward fallback = defaultReward(culture);
            if (section == null) {
                rewards.put(culture.requireBoss().id(), fallback);
                continue;
            }
            int xp = Math.max(0, section.getInt("xp", fallback.xp()));
            List<RewardItem> items = section.isList("items")
                ? parseItems(section.getStringList("items"), culture.requireBoss().id(), warnings) : fallback.items();
            rewards.put(culture.requireBoss().id(), new BossReward(xp, items));
        }

        MythicMobCatalog mobs = MythicMobCatalog.defaults().withOverrides(config.getConfigurationSection("mobs"),
            unknown -> warnings.add("mobs." + unknown + " no es un mob mitico; se ignora"));
        return new MythicDungeonSettings(altar, offset, cap, radius, replace, looting, top, rewards, mobs, warnings);
    }

    /** Los premios por defecto: experiencia y botin vanilla con algo de la tematica del jefe. */
    public static BossReward defaultReward(MythicCulture culture) {
        List<RewardItem> items = new ArrayList<>(List.of(
            new RewardItem(Material.DIAMOND, 4),
            new RewardItem(Material.GOLDEN_APPLE, 2),
            new RewardItem(Material.NETHERITE_SCRAP, 1)));
        items.add(switch (culture) {
            case GREEK -> new RewardItem(Material.TRIDENT, 1);
            case NORSE -> new RewardItem(Material.TOTEM_OF_UNDYING, 1);
            case MEDIEVAL -> new RewardItem(Material.GOLD_BLOCK, 3);
            case EGYPTIAN -> new RewardItem(Material.ENCHANTED_GOLDEN_APPLE, 1);
            case MESOPOTAMIAN -> new RewardItem(Material.LAPIS_BLOCK, 4);
            case JAPANESE -> new RewardItem(Material.IRON_BLOCK, 4);
            case MESOAMERICAN -> new RewardItem(Material.EMERALD_BLOCK, 2);
            case ARABIAN -> new RewardItem(Material.BLAZE_ROD, 8);
            case ANDEAN -> new RewardItem(Material.AMETHYST_BLOCK, 4);
        });
        return new BossReward(1500, items);
    }

    private static List<RewardItem> parseItems(List<String> raw, String bossId, List<String> warnings) {
        List<RewardItem> items = new ArrayList<>();
        for (String entry : raw) {
            String[] parts = entry.trim().split(":");
            Optional<Material> material = material(parts[0]).filter(MythicDungeonSettings::isItem);
            int amount = 1;
            if (parts.length > 1) {
                try {
                    amount = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException invalid) {
                    amount = -1;
                }
            }
            if (material.isEmpty() || amount < 1 || parts.length > 2) {
                warnings.add("rewards." + bossId + ".items: '" + entry + "' no es MATERIAL:cantidad; se ignora");
                continue;
            }
            items.add(new RewardItem(material.get(), amount));
        }
        return items;
    }

    private static Optional<Material> material(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT)));
    }

    // The unit-test API resolves block/item types through a registry that only a live server has; when that lookup is
    // unavailable the name already matched a Material, which is the most that can be checked offline.
    private static boolean isBlock(Material material) {
        try {
            return material.isBlock();
        } catch (RuntimeException | LinkageError offline) {
            return true;
        }
    }

    private static boolean isItem(Material material) {
        try {
            return material.isItem();
        } catch (RuntimeException | LinkageError offline) {
            return true;
        }
    }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static double positive(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0 ? value : fallback;
    }

    private static double probability(double value, double fallback) {
        return Double.isFinite(value) && value >= 0.0 && value <= 1.0 ? value : fallback;
    }
}
