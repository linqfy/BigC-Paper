package dev.linqfy.bigCasares.modules.villagermood;

import org.bukkit.configuration.file.FileConfiguration;

record VillagerMafiaSettings(boolean enabled, double radius, int minBoxedVillagers, long minConfinedTicks,
                            double chancePerCheck, int crewSize, long durationTicks, long cooldownTicks,
                            double health, double damage, double driveByChance, long shotCooldownTicks) {

    private static final String ROOT = "villager-mood.mafia.";

    static VillagerMafiaSettings defaults() {
        return new VillagerMafiaSettings(true, 24.0, 6, 6_000L, 0.02, 3, 1_200L, 24_000L,
            20.0, 8.0, 0.35, 40L);
    }

    static VillagerMafiaSettings load(FileConfiguration config) {
        VillagerMafiaSettings d = defaults();
        return new VillagerMafiaSettings(
            config.getBoolean(ROOT + "enabled", d.enabled()),
            config.getDouble(ROOT + "radius", d.radius()),
            config.getInt(ROOT + "min-boxed-villagers", d.minBoxedVillagers()),
            config.getLong(ROOT + "min-confined-ticks", d.minConfinedTicks()),
            config.getDouble(ROOT + "chance-per-check", d.chancePerCheck()),
            config.getInt(ROOT + "crew-size", d.crewSize()),
            config.getLong(ROOT + "duration-ticks", d.durationTicks()),
            config.getLong(ROOT + "cooldown-ticks", d.cooldownTicks()),
            config.getDouble(ROOT + "health", d.health()),
            config.getDouble(ROOT + "damage", d.damage()),
            config.getDouble(ROOT + "drive-by-chance", d.driveByChance()),
            config.getLong(ROOT + "shot-cooldown-ticks", d.shotCooldownTicks())
        );
    }

    VillagerMafiaSettings {
        if (!within(radius, 4.0, 64.0) || minBoxedVillagers < 2 || minBoxedVillagers > 256
            || minConfinedTicks < 0L || !within(chancePerCheck, 0.0, 1.0)
            || crewSize < 1 || crewSize > 8 || durationTicks < 10L || durationTicks > 72_000L
            || cooldownTicks < 0L || cooldownTicks > 1_728_000L
            || !within(health, 1.0, 1_024.0) || !within(damage, 0.0, 1_000.0)
            || !within(driveByChance, 0.0, 1.0) || shotCooldownTicks < 10L || shotCooldownTicks > 1_200L) {
            throw new IllegalArgumentException("La configuración de la mafia de aldeanos no es válida.");
        }
    }

    private static boolean within(double value, double min, double max) {
        return Double.isFinite(value) && value >= min && value <= max;
    }
}
