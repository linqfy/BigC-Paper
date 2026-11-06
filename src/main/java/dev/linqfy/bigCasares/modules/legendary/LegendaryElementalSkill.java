package dev.linqfy.bigCasares.modules.legendary;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Map;

/** Names and defaults from the user's supplied specification. Damage units are HP. */
enum LegendaryElementalSkill {
    LIGHTNING_DASH(LegendaryWeapon.THUNDER_SPEAR, 1, "lightning-dash", 20, 4, 8, 0, 1),
    THUNDER_MARK(LegendaryWeapon.THUNDER_SPEAR, 2, "thunder-mark", 40, 5, 0, 4, 6),
    CHAIN_LIGHTNING(LegendaryWeapon.THUNDER_SPEAR, 3, "chain-lightning", 35, 6, 15, 5, 1),
    THUNDERSTORM(LegendaryWeapon.THUNDER_SPEAR, 4, "thunderstorm", 60, 4, 20, 6, 8),
    WRATH_OF_ZEUS(LegendaryWeapon.THUNDER_SPEAR, 5, "wrath-of-zeus", 90, 12, 20, 5, 1.5),
    WIND_DASH(LegendaryWeapon.WINDWEAVER, 1, "wind-dash", 18, 0, 9, 0, 1),
    SKYBREAKER(LegendaryWeapon.WINDWEAVER, 2, "skybreaker", 40, 8, 8, 5, 10),
    TORNADO(LegendaryWeapon.WINDWEAVER, 3, "tornado", 40, 2, 15, 3, 5),
    WIND_PRISON(LegendaryWeapon.WINDWEAVER, 4, "wind-prison", 50, 0, 12, 5, 5),
    HEAVENS_FALL(LegendaryWeapon.WINDWEAVER, 5, "heavens-fall", 90, 12, 12, 7, 12);

    final LegendaryWeapon weapon;
    final int level;
    final String id;
    private final double cooldown, damage, range, radius, duration;

    LegendaryElementalSkill(
            LegendaryWeapon weapon,
            int level,
            String id,
            double cooldown,
            double damage,
            double range,
            double radius,
            double duration) {
        this.weapon = weapon;
        this.level = level;
        this.id = id;
        this.cooldown = cooldown;
        this.damage = damage;
        this.range = range;
        this.radius = radius;
        this.duration = duration;
    }

    record Settings(LegendaryElementalSkill skill, Map<String, Object> values) {
        Settings {
            values = Map.copyOf(values);
        }

        double number(String key, double fallback, double maximum) {
            Object raw = values.get(key);
            double value = raw instanceof Number numeric ? numeric.doubleValue() : fallback;
            return Double.isFinite(value) ? Math.max(0, Math.min(maximum, value)) : fallback;
        }

        double damage() {
            return number("damage", skill.damage, 40);
        }

        double range() {
            return number("range", skill.range, 64);
        }

        double radius() {
            return number("radius", skill.radius, 24);
        }

        int durationTicks() {
            return Math.max(1, (int) Math.round(number("duration", skill.duration, 30) * 20));
        }

        long cooldownMillis() {
            return Math.max(1000, Math.round(number("cooldown", skill.cooldown, 600) * 1000));
        }
    }

    Settings settings(FileConfiguration config) {
        var section = config.getConfigurationSection("legendary-weapons.abilities." + id);
        return new Settings(
                this, section == null ? Map.of() : Map.copyOf(section.getValues(false)));
    }

    static LegendaryElementalSkill click(
            LegendaryWeapon weapon,
            int level,
            boolean sneak,
            boolean right,
            boolean air,
            float pitch) {
        if (weapon != LegendaryWeapon.WINDWEAVER && weapon != LegendaryWeapon.THUNDER_SPEAR)
            return null;
        if (right)
            return weapon == LegendaryWeapon.THUNDER_SPEAR
                    ? (sneak ? CHAIN_LIGHTNING : THUNDER_MARK)
                    : (sneak ? TORNADO : SKYBREAKER);
        if (!sneak) return null;
        if (weapon == LegendaryWeapon.THUNDER_SPEAR && pitch < -45 && level >= 4)
            return THUNDERSTORM;
        if (weapon == LegendaryWeapon.WINDWEAVER && pitch > 45 && level >= 4) return WIND_PRISON;
        return air ? (weapon == LegendaryWeapon.THUNDER_SPEAR ? LIGHTNING_DASH : WIND_DASH) : null;
    }

    static double chainDamage(double initial, int jump) {
        return initial * Math.pow(.75, jump);
    }
}
