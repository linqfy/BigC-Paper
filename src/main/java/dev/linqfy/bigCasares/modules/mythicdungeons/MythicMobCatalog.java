package dev.linqfy.bigCasares.modules.mythicdungeons;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Los 27 mobs de las nueve mazmorras con sus numeros por defecto. Los comunes andan por 20-40 de vida (los murcielagos,
 * que vienen en bandada, menos); los elites (casi mini-jefes) por 150-200, con una conducta distintiva y poco peso en el
 * sorteo. Vida, dano, velocidad, chance de fragmento y peso se pueden pisar desde {@code mobs.<id>.*} de
 * {@code mythic-dungeons.yml}. Los mobs de las culturas sin jefe ya traen su chance de fragmento, pero no sueltan nada
 * hasta que exista ese item (ver {@link FragmentDropRoll}).
 */
public final class MythicMobCatalog {

    private final Map<String, MythicMobType> types;
    private final List<String> order;

    private MythicMobCatalog(Map<String, MythicMobType> types) {
        this.types = Map.copyOf(types);
        this.order = List.copyOf(types.keySet());
    }

    public Optional<MythicMobType> find(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(types.get(id.trim().toLowerCase(Locale.ROOT)));
    }

    public MythicMobType require(String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("unknown mythic mob: " + id));
    }

    /** Todos, en el orden de la tabla de culturas. */
    public List<MythicMobType> all() {
        return order.stream().map(types::get).toList();
    }

    public List<String> ids() {
        return order;
    }

    public List<MythicMobType> ofCulture(MythicCulture culture) {
        return culture.mobIds().stream().map(types::get).toList();
    }

    /**
     * Una copia con los numeros de {@code mobs.<id>.health|damage|speed|fragment-chance|weight}. Los valores que faltan
     * o son invalidos dejan el valor por defecto; un id desconocido se ignora (lo devuelve en {@code unknown}).
     */
    public MythicMobCatalog withOverrides(ConfigurationSection mobs, Consumer<String> unknown) {
        if (mobs == null) {
            return this;
        }
        Map<String, MythicMobType> tuned = new LinkedHashMap<>();
        for (String id : order) {
            tuned.put(id, types.get(id));
        }
        for (String key : mobs.getKeys(false)) {
            String id = key.trim().toLowerCase(Locale.ROOT);
            MythicMobType base = tuned.get(id);
            ConfigurationSection section = mobs.getConfigurationSection(key);
            if (base == null || section == null) {
                unknown.accept(key);
                continue;
            }
            tuned.put(id, base.withTuning(
                positive(section, "health", base.health()),
                nonNegative(section, "damage", base.damage()),
                positive(section, "speed", base.speed()),
                probability(section, "fragment-chance", base.fragmentChance()),
                positive(section, "weight", base.weight())));
        }
        return new MythicMobCatalog(tuned);
    }

    private static double positive(ConfigurationSection section, String key, double fallback) {
        double value = section.getDouble(key, fallback);
        return Double.isFinite(value) && value > 0.0 ? value : fallback;
    }

    private static double nonNegative(ConfigurationSection section, String key, double fallback) {
        double value = section.getDouble(key, fallback);
        return Double.isFinite(value) && value >= 0.0 ? value : fallback;
    }

    private static double probability(ConfigurationSection section, String key, double fallback) {
        double value = section.getDouble(key, fallback);
        return Double.isFinite(value) && value >= 0.0 && value <= 1.0 ? value : fallback;
    }

    public static MythicMobCatalog defaults() {
        List<MythicMobType> all = new ArrayList<>();
        // Griega
        all.add(mob("minotaur", MythicCulture.GREEK, "Minotauro", EntityType.HUSK)
            .stats(40, 7, 0.27).scale(1.3).armor(4, 0.4).behaviour(MythicBehaviour.LEAPER)
            .loot(0.08, 10, 12).drop(Material.LEATHER, 0, 2, 0.6).drop(Material.BEEF, 1, 2, 0.5).build());
        all.add(mob("cerberus", MythicCulture.GREEK, "Cerbero", EntityType.WOLF)
            .stats(150, 9, 0.32).scale(2.0).armor(6, 0.7).behaviour(MythicBehaviour.RANGED)
            .projectile(MythicProjectile.FIREBALL).hit("wither", 3, 0)
            .loot(0.35, 2, 60).drop(Material.BONE, 2, 5, 1.0).drop(Material.BLAZE_POWDER, 1, 2, 0.5).elite().build());
        all.add(mob("erinys", MythicCulture.GREEK, "Erinia", EntityType.PHANTOM)
            .stats(26, 5, 0.7).scale(1.6).armor(0, 0.0).behaviour(MythicBehaviour.FLYER_DIVE).hit("weakness", 5, 0)
            .loot(0.10, 7, 10).drop(Material.FEATHER, 1, 2, 0.5).drop(Material.PHANTOM_MEMBRANE, 0, 1, 0.4)
            .floating().build());
        // Nordica
        all.add(mob("draugr", MythicCulture.NORSE, "Draugr", EntityType.ZOMBIE)
            .stats(30, 6, 0.23).scale(1.05).armor(6, 0.2).behaviour(MythicBehaviour.MELEE).hit("slowness", 3, 0)
            .loot(0.07, 12, 8).drop(Material.BONE, 0, 2, 0.6).drop(Material.IRON_NUGGET, 1, 3, 0.4).build());
        all.add(mob("dwarf", MythicCulture.NORSE, "Enano", EntityType.VINDICATOR)
            .stats(36, 7, 0.3).scale(0.75).armor(10, 0.6).behaviour(MythicBehaviour.TANK)
            .loot(0.09, 8, 12).drop(Material.COAL, 1, 3, 0.6).drop(Material.IRON_INGOT, 1, 2, 0.35).build());
        all.add(mob("nidhogg", MythicCulture.NORSE, "Nidhogg", EntityType.RAVAGER)
            .stats(180, 10, 0.28).scale(1.1).armor(8, 0.9).behaviour(MythicBehaviour.CASTER)
            .hit("poison", 5, 1).hit("wither", 3, 0)
            .loot(0.40, 1, 80).drop(Material.LEATHER, 2, 4, 1.0).drop(Material.EMERALD, 1, 3, 0.5).elite().build());
        all.add(mob("hel", MythicCulture.NORSE, "Hel", EntityType.WITHER_SKELETON)
            .stats(160, 9, 0.27).scale(1.15).armor(6, 0.5).behaviour(MythicBehaviour.SUMMONER).summons("draugr")
            .hit("wither", 4, 1)
            .loot(0.40, 1, 80).drop(Material.BONE, 2, 4, 1.0).drop(Material.AMETHYST_SHARD, 1, 2, 0.5).elite()
            .build());
        // Europa medieval
        all.add(mob("treasure_dragon", MythicCulture.MEDIEVAL, "Dragón del tesoro", EntityType.BLAZE)
            .stats(200, 10, 0.25).scale(1.8).armor(8, 0.8).behaviour(MythicBehaviour.RANGED)
            .projectile(MythicProjectile.FIREBALL)
            .loot(0.45, 1, 100).drop(Material.GOLD_INGOT, 2, 5, 1.0).drop(Material.DIAMOND, 0, 1, 0.25).elite()
            .build());
        all.add(mob("kobold", MythicCulture.MEDIEVAL, "Kobold", EntityType.SKELETON)
            .stats(20, 4, 0.27).scale(0.7).armor(2, 0.0).behaviour(MythicBehaviour.RANGED)
            .projectile(MythicProjectile.ARROW)
            .loot(0.06, 12, 6).drop(Material.ARROW, 1, 3, 0.7).drop(Material.FLINT, 0, 2, 0.5).build());
        all.add(mob("goblin", MythicCulture.MEDIEVAL, "Goblin", EntityType.ZOMBIE)
            .stats(22, 5, 0.3).scale(0.65).armor(2, 0.0).behaviour(MythicBehaviour.LEAPER).hit("hunger", 6, 1)
            .loot(0.06, 12, 6).drop(Material.GOLD_NUGGET, 1, 4, 0.7).build());
        all.add(mob("ghost", MythicCulture.MEDIEVAL, "Fantasma", EntityType.VEX)
            .stats(24, 5, 0.7).scale(1.4).armor(0, 0.0).behaviour(MythicBehaviour.PHASER).hit("blindness", 2, 0)
            .loot(0.08, 8, 8).drop(Material.GLOWSTONE_DUST, 1, 2, 0.4).drop(Material.PHANTOM_MEMBRANE, 0, 1, 0.3)
            .floating().phasesThroughWalls().build());
        // Egipcia
        all.add(mob("ammit", MythicCulture.EGYPTIAN, "Ammit", EntityType.RAVAGER)
            .stats(160, 11, 0.3).scale(0.8).armor(10, 0.8).behaviour(MythicBehaviour.TANK).hit("wither", 3, 1)
            .loot(0.40, 1, 80).drop(Material.LEATHER, 2, 4, 1.0).drop(Material.GOLD_INGOT, 1, 2, 0.5).elite().build());
        all.add(mob("apophis", MythicCulture.EGYPTIAN, "Apofis", EntityType.SPIDER)
            .stats(200, 9, 0.3).scale(1.6).armor(6, 0.8).behaviour(MythicBehaviour.CONSTRICTOR)
            .hit("poison", 4, 1).hit("slowness", 3, 2)
            .loot(0.45, 1, 100).drop(Material.STRING, 3, 6, 1.0).drop(Material.EMERALD, 1, 2, 0.4).elite().build());
        all.add(mob("mummy", MythicCulture.EGYPTIAN, "Momia", EntityType.HUSK)
            .stats(34, 6, 0.22).scale(1.0).armor(4, 0.2).behaviour(MythicBehaviour.MELEE)
            .hit("weakness", 6, 0).hit("hunger", 6, 0)
            .loot(0.08, 14, 8).drop(Material.PAPER, 1, 2, 0.6).drop(Material.STRING, 1, 3, 0.4).build());
        // Mesopotamica
        all.add(mob("ereshkigal", MythicCulture.MESOPOTAMIAN, "Ereshkigal", EntityType.EVOKER)
            .stats(180, 8, 0.45).scale(1.2).armor(6, 0.5).behaviour(MythicBehaviour.CASTER)
            .hit("darkness", 4, 0).hit("wither", 3, 1)
            .loot(0.45, 1, 100).drop(Material.LAPIS_LAZULI, 3, 6, 1.0).drop(Material.GOLD_INGOT, 1, 2, 0.5).elite()
            .build());
        all.add(mob("galla", MythicCulture.MESOPOTAMIAN, "Galla", EntityType.ZOMBIE_VILLAGER)
            .stats(32, 6, 0.26).scale(1.1).armor(4, 0.3).behaviour(MythicBehaviour.CONSTRICTOR).hit("slowness", 3, 1)
            .loot(0.10, 10, 9).drop(Material.BONE, 1, 2, 0.5).drop(Material.CLAY_BALL, 1, 3, 0.5).build());
        // Japonesa
        all.add(mob("oni", MythicCulture.JAPANESE, "Oni", EntityType.VINDICATOR)
            .stats(190, 12, 0.27).scale(1.5).armor(8, 0.8).behaviour(MythicBehaviour.LEAPER).hit("slowness", 2, 1)
            .loot(0.40, 1, 90).drop(Material.IRON_INGOT, 1, 3, 1.0).drop(Material.BONE, 1, 3, 0.6).elite().build());
        all.add(mob("cave_yokai", MythicCulture.JAPANESE, "Tsuchigumo", EntityType.CAVE_SPIDER)
            .stats(28, 5, 0.3).scale(1.8).armor(2, 0.2).behaviour(MythicBehaviour.CONSTRICTOR)
            .hit("slowness", 4, 1).hit("poison", 3, 0)
            .loot(0.08, 9, 9).drop(Material.STRING, 1, 3, 0.8).drop(Material.SPIDER_EYE, 0, 1, 0.4).build());
        all.add(mob("shikome", MythicCulture.JAPANESE, "Shikome", EntityType.HUSK)
            .stats(24, 5, 0.33).scale(0.95).armor(2, 0.0).behaviour(MythicBehaviour.LEAPER)
            .hit("weakness", 5, 0).hit("hunger", 6, 1)
            .loot(0.07, 11, 8).drop(Material.ROTTEN_FLESH, 1, 2, 0.7).drop(Material.BONE, 0, 1, 0.4).build());
        // Mesoamericana
        all.add(mob("xibalba_lord", MythicCulture.MESOAMERICAN, "Señor de Xibalbá", EntityType.WITCH)
            .stats(170, 8, 0.27).scale(1.2).armor(6, 0.5).behaviour(MythicBehaviour.SUMMONER).summons("camazotz_bat")
            .hit("wither", 3, 1).hit("darkness", 4, 0)
            .loot(0.40, 1, 85).drop(Material.BONE, 2, 4, 1.0).drop(Material.EMERALD, 1, 3, 0.5).elite().build());
        all.add(mob("xolotl", MythicCulture.MESOAMERICAN, "Xólotl", EntityType.WOLF)
            .stats(26, 5, 0.36).scale(0.9).armor(2, 0.1).behaviour(MythicBehaviour.LEAPER).hit("slowness", 2, 1)
            .loot(0.07, 10, 8).drop(Material.BONE, 1, 2, 0.7).drop(Material.LEATHER, 0, 1, 0.3).build());
        all.add(mob("camazotz_bat", MythicCulture.MESOAMERICAN, "Murciélago de Camazotz", EntityType.BAT)
            .stats(14, 4, 0.7).scale(1.3).armor(0, 0.0).behaviour(MythicBehaviour.FLYER_DIVE).hit("blindness", 2, 0)
            .loot(0.05, 14, 5).drop(Material.LEATHER, 0, 1, 0.3).drop(Material.PHANTOM_MEMBRANE, 0, 1, 0.15)
            .floating().build());
        // Arabe
        all.add(mob("ghul", MythicCulture.ARABIAN, "Gul", EntityType.ZOMBIE)
            .stats(34, 6, 0.25).scale(1.05).armor(4, 0.2).behaviour(MythicBehaviour.MELEE)
            .hit("hunger", 8, 1).hit("weakness", 4, 0)
            .loot(0.08, 12, 9).drop(Material.ROTTEN_FLESH, 1, 3, 0.8).drop(Material.BONE, 0, 2, 0.5).build());
        all.add(mob("djinn", MythicCulture.ARABIAN, "Yinn", EntityType.BLAZE)
            .stats(180, 9, 0.3).scale(1.4).armor(6, 0.6).behaviour(MythicBehaviour.BLINK_CASTER)
            .projectile(MythicProjectile.FIREBALL).hit("weakness", 4, 1)
            .loot(0.40, 2, 90).drop(Material.BLAZE_POWDER, 1, 3, 1.0).drop(Material.GOLD_INGOT, 1, 2, 0.5).elite()
            .floating().build());
        // Sudamericana
        all.add(mob("supay", MythicCulture.ANDEAN, "Supay", EntityType.PIGLIN_BRUTE)
            .stats(200, 11, 0.3).scale(1.4).armor(10, 0.9).behaviour(MythicBehaviour.TANK)
            .hit("mining_fatigue", 8, 1).hit("slowness", 3, 1)
            .loot(0.45, 1, 100).drop(Material.GOLD_INGOT, 2, 4, 1.0).drop(Material.COAL, 2, 5, 0.6).elite().build());
        all.add(mob("salamanca_viper", MythicCulture.ANDEAN, "Víbora de la Salamanca", EntityType.SPIDER)
            .stats(22, 4, 0.32).scale(0.55).armor(0, 0.0).behaviour(MythicBehaviour.CONSTRICTOR).hit("poison", 5, 1)
            .loot(0.07, 10, 7).drop(Material.STRING, 0, 2, 0.5).drop(Material.SPIDER_EYE, 0, 1, 0.3).build());
        all.add(mob("salamanca_goat", MythicCulture.ANDEAN, "Chivo negro", EntityType.HOGLIN)
            .stats(32, 6, 0.3).scale(0.75).armor(4, 0.5).behaviour(MythicBehaviour.LEAPER).hit("nausea", 4, 0)
            .loot(0.08, 9, 9).drop(Material.LEATHER, 1, 2, 0.6).drop(Material.MUTTON, 1, 2, 0.5).build());

        Map<String, MythicMobType> byId = new LinkedHashMap<>();
        for (MythicCulture culture : MythicCulture.values()) {
            for (String id : culture.mobIds()) {
                MythicMobType type = all.stream().filter(candidate -> candidate.id().equals(id)).findFirst()
                    .orElseThrow(() -> new IllegalStateException("missing mythic mob definition: " + id));
                if (type.culture() != culture) {
                    throw new IllegalStateException("mythic mob " + id + " belongs to " + culture.id());
                }
                byId.put(id, type);
            }
        }
        if (byId.size() != all.size()) {
            throw new IllegalStateException("mythic mob catalog has definitions outside the culture table");
        }
        return new MythicMobCatalog(byId);
    }

    private static Builder mob(String id, MythicCulture culture, String name, EntityType base) {
        return new Builder(id, culture, name, base);
    }

    private static final class Builder {
        private final String id;
        private final MythicCulture culture;
        private final String name;
        private final EntityType base;
        private double health;
        private double damage;
        private double speed;
        private double scale = 1.0;
        private double armor;
        private double knockbackResistance;
        private MythicBehaviour behaviour = MythicBehaviour.MELEE;
        private MythicProjectile projectile = MythicProjectile.NONE;
        private final List<MythicEffect> onHit = new ArrayList<>();
        private String summonId = "";
        private double fragmentChance;
        private double weight;
        private int xp;
        private final List<MythicDrop> drops = new ArrayList<>();
        private boolean elite;
        private boolean floating;
        private boolean phasesThroughWalls;

        private Builder(String id, MythicCulture culture, String name, EntityType base) {
            this.id = id;
            this.culture = culture;
            this.name = name;
            this.base = base;
        }

        Builder stats(double health, double damage, double speed) {
            this.health = health;
            this.damage = damage;
            this.speed = speed;
            return this;
        }

        Builder scale(double scale) {
            this.scale = scale;
            return this;
        }

        Builder armor(double armor, double knockbackResistance) {
            this.armor = armor;
            this.knockbackResistance = knockbackResistance;
            return this;
        }

        Builder behaviour(MythicBehaviour behaviour) {
            this.behaviour = behaviour;
            return this;
        }

        Builder projectile(MythicProjectile projectile) {
            this.projectile = projectile;
            return this;
        }

        Builder hit(String effect, int seconds, int amplifier) {
            onHit.add(MythicEffect.of(effect, seconds, amplifier));
            return this;
        }

        Builder summons(String mobId) {
            this.summonId = mobId;
            return this;
        }

        Builder loot(double fragmentChance, double weight, int xp) {
            this.fragmentChance = fragmentChance;
            this.weight = weight;
            this.xp = xp;
            return this;
        }

        Builder drop(Material material, int min, int max, double chance) {
            drops.add(new MythicDrop(material, min, max, chance));
            return this;
        }

        Builder elite() {
            this.elite = true;
            return this;
        }

        Builder floating() {
            this.floating = true;
            return this;
        }

        Builder phasesThroughWalls() {
            this.phasesThroughWalls = true;
            return this;
        }

        MythicMobType build() {
            return new MythicMobType(id, culture, name, base, health, damage, speed, scale, armor,
                knockbackResistance, behaviour, projectile, onHit, summonId, fragmentChance, weight, xp, drops, elite,
                floating, phasesThroughWalls);
        }
    }
}
