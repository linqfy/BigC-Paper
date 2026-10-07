package dev.linqfy.bigCasares.modules.donpollos;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class DonPollosSettingsLoader {

    public DonPollosSettings load(ConfigurationSection config) {
        ConfigurationSection variantsSection = config.getConfigurationSection("variants");
        if (variantsSection == null) {
            throw new IllegalArgumentException("don-pollos.yml needs a variants section");
        }
        Map<DonPolloVariant, DonPollosVariantSettings> variants = new EnumMap<>(DonPolloVariant.class);
        for (String key : variantsSection.getKeys(false)) {
            DonPolloVariant variant = DonPolloVariant.fromId(key)
                .orElseThrow(() -> new IllegalArgumentException("Unknown Don Pollo variant: " + key));
            ConfigurationSection section = variantsSection.getConfigurationSection(key);
            if (section == null) {
                throw new IllegalArgumentException("Don Pollo variant " + key + " must be a section");
            }
            variants.put(variant, loadVariant(variant, section));
        }
        return new DonPollosSettings(
            variants,
            roulette(config),
            config.getDouble("salsero.follow-radius", 12.0),
            config.getDouble("salsero.stop-distance", 2.5),
            config.getLong("salsero.update-ticks", 10L),
            config.isList("salsero.dances")
                ? config.getStringList("salsero.dances")
                : List.of("dance1", "dance2", "dance3", "dance4", "dance5"),
            battle(config),
            cities(config),
            boss(config)
        );
    }

    private DonPollosVariantSettings loadVariant(DonPolloVariant variant, ConfigurationSection section) {
        String owner = "variants." + variant.id();
        List<DonPollosTrade> trades = new ArrayList<>();
        for (Map<?, ?> raw : section.getMapList("trades")) {
            trades.add(new DonPollosTrade(
                item(raw.get("cost"), owner + ".trades.cost"),
                item(raw.get("result"), owner + ".trades.result")
            ));
        }
        List<DonPollosOffer> offers = new ArrayList<>();
        for (Map<?, ?> raw : section.getMapList("offers")) {
            Object item = raw.get("item");
            DonPollosProduct product = DonPollosProduct.fromId(item == null ? null : item.toString())
                .orElseThrow(() -> new IllegalArgumentException("Unknown product in " + owner + ".offers: " + item));
            offers.add(new DonPollosOffer(product, intValue(raw.get("amount"), 1), intValue(raw.get("price"), 1)));
        }
        return new DonPollosVariantSettings(
            variant,
            section.getString("display-name"),
            section.getString("model-key"),
            section.getString("animation"),
            section.getBoolean("wander", false),
            section.getDouble("speed", 1.0),
            section.getString("menu-title"),
            trades,
            offers
        );
    }

    private DonPollosBossSettings boss(ConfigurationSection config) {
        DonPollosBossSettings d = DonPollosBossSettings.defaults();
        DonPollosItem reward = config.isConfigurationSection("boss.reward")
            ? new DonPollosItem(validMaterial(config.getString("boss.reward.material", "DIAMOND"), "boss.reward"),
                config.getInt("boss.reward.amount", d.reward().amount()))
            : d.reward();
        return new DonPollosBossSettings(
            config.getDouble("boss.health", d.health()),
            config.getDouble("boss.scale", d.scale()),
            config.getDouble("boss.melee-damage", d.meleeDamage()),
            config.getDouble("boss.laser-damage", d.laserDamage()),
            config.getDouble("boss.laser-range", d.laserRange()),
            config.getInt("boss.laser-cooldown-seconds", d.laserCooldownSeconds()),
            reward,
            config.getDouble("boss.fase-2.keep-distance", d.keepDistance()),
            config.getDouble("boss.fase-2.meteor-damage", d.meteorDamage()),
            config.getDouble("boss.fase-2.meteor-cooldown-seconds", d.meteorCooldownSeconds()),
            config.getDouble("boss.fase-2.chicken-wave-seconds", d.chickenWaveSeconds()),
            config.getInt("boss.fase-2.chickens-per-wave", d.chickensPerWave()),
            config.getInt("boss.fase-2.max-chickens", d.maxChickens()),
            config.getDouble("boss.fase-2.chicken-health", d.chickenHealth()),
            config.getDouble("boss.fase-2.chicken-damage", d.chickenDamage()),
            phase3(config, d.phase3()),
            phase4(config, d.phase4()));
    }

    private DonPollosBossPhase4Settings phase4(ConfigurationSection config, DonPollosBossPhase4Settings d) {
        String p = "boss.fase-final.";
        return new DonPollosBossPhase4Settings(
            config.getDouble(p + "grow-factor", d.growFactor()),
            config.getDouble(p + "beam-cooldown-seconds", d.beamCooldownSeconds()),
            config.getDouble(p + "beam-damage", d.beamDamage()),
            config.getDouble(p + "beam-range", d.beamRange()),
            config.getDouble(p + "jump-cooldown-seconds", d.jumpCooldownSeconds()),
            config.getDouble(p + "jump-land-distance", d.jumpLandDistance()),
            config.getDouble(p + "shockwave-radius", d.shockwaveRadius()),
            config.getDouble(p + "shockwave-damage", d.shockwaveDamage()),
            config.getDouble(p + "sanders-seconds", d.colonelSeconds()),
            config.getInt(p + "sanders-per-wave", d.colonelsPerWave()),
            config.getInt(p + "max-sanders", d.maxColonels()),
            config.getDouble(p + "sanders-speed", d.colonelSpeed()),
            config.getDouble(p + "sanders-damage-extra", d.colonelDamage()),
            config.getDouble(p + "sanders-health", d.colonelHealth()));
    }

    private DonPollosBossPhase3Settings phase3(ConfigurationSection config, DonPollosBossPhase3Settings d) {
        List<DonPollosCubeRound> rounds = new ArrayList<>();
        for (Map<?, ?> round : config.getMapList("boss.fase-3.batallas-del-cubo.rondas")) {
            rounds.add(new DonPollosCubeRound(
                number(round.get("labubus"), 10).intValue(),
                number(round.get("cada-segundos"), 1).doubleValue(),
                number(round.get("coronel-velocidad"), 1).doubleValue(),
                number(round.get("coronel-dano-extra"), 0).doubleValue()));
        }
        return new DonPollosBossPhase3Settings(
            config.getDouble("boss.fase-3.levitate-seconds", d.levitateSeconds()),
            config.getDouble("boss.fase-3.levitate-height", d.levitateHeight()),
            config.getInt("boss.fase-3.pit-depth", d.pitDepth()),
            config.getInt("boss.fase-3.batallas-del-cubo.max-labubus-vivos", d.maxAlive()),
            config.getDouble("boss.fase-3.batallas-del-cubo.pausa-segundos", d.roundPauseSeconds()),
            rounds.isEmpty() ? d.rounds() : rounds);
    }

    private static Number number(Object value, Number fallback) {
        return value instanceof Number number ? number : fallback;
    }

    private DonPollosCitySettings cities(ConfigurationSection config) {
        DonPollosCitySettings d = DonPollosCitySettings.defaults();
        Map<DonPolloVariant, Integer> pollos = new EnumMap<>(DonPolloVariant.class);
        for (DonPolloVariant variant : DonPolloVariant.values()) {
            pollos.put(variant, config.getInt("ciudades.don-pollos." + variant.id(), d.count(variant)));
        }
        return new DonPollosCitySettings(
            config.getBoolean("ciudades.enabled", d.enabled()),
            config.isList("ciudades.worlds") ? config.getStringList("ciudades.worlds") : d.worlds(),
            config.getInt("ciudades.spacing", d.spacing()),
            config.getDouble("ciudades.chance", d.chance()),
            config.getInt("ciudades.min-distance-from-spawn", d.minDistanceFromSpawn()),
            config.getInt("ciudades.blocks-per-tick", d.blocksPerTick()),
            pollos);
    }

    private DonPollosBattleSettings battle(ConfigurationSection config) {
        DonPollosBattleSettings d = DonPollosBattleSettings.defaults();
        String p = "salsero.pvp.";
        DonPollosItem reward = config.isConfigurationSection(p + "reward")
            ? new DonPollosItem(
                validMaterial(config.getString(p + "reward.material", "DIAMOND"), p + "reward"),
                config.getInt(p + "reward.amount", 5))
            : d.reward();
        return new DonPollosBattleSettings(
            config.getInt(p + "hits-to-win", d.hitsToWin()),
            config.getInt(p + "lives", d.lives()),
            config.getInt(p + "window-ticks", d.startWindowTicks()),
            config.getInt(p + "min-window-ticks", d.minWindowTicks()),
            config.getInt(p + "pause-ticks", d.pauseTicks()),
            reward,
            config.getDouble(p + "max-distance", d.maxDistance()));
    }

    private DonPollosRoulette roulette(ConfigurationSection config) {
        DonPollosRoulette defaults = DonPollosRoulette.standard();
        Map<DonPollosRouletteColor, Integer> weights = new EnumMap<>(DonPollosRouletteColor.class);
        Map<DonPollosRouletteColor, Integer> multipliers = new EnumMap<>(DonPollosRouletteColor.class);
        for (DonPollosRouletteColor color : DonPollosRouletteColor.values()) {
            String path = "fino.roulette." + color.id();
            weights.put(color, config.getInt(path + ".weight", defaults.weight(color)));
            multipliers.put(color, config.getInt(path + ".multiplier", defaults.multiplier(color)));
        }
        return new DonPollosRoulette(weights, multipliers);
    }

    private DonPollosItem item(Object raw, String owner) {
        if (!(raw instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(owner + " must be a map with material and amount");
        }
        Object material = map.get("material");
        return new DonPollosItem(
            validMaterial(material == null ? null : material.toString(), owner),
            intValue(map.get("amount"), 1)
        );
    }

    private String validMaterial(String raw, String owner) {
        Material material = raw == null ? null : Material.matchMaterial(raw);
        if (material == null || material.name().endsWith("AIR")) {
            throw new IllegalArgumentException("Invalid material in " + owner + ": " + raw);
        }
        return material.name();
    }

    private static int intValue(Object raw, int fallback) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(raw.toString().strip());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Expected a number but found: " + raw, exception);
        }
    }
}
