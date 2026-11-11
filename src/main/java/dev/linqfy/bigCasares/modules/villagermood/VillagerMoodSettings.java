package dev.linqfy.bigCasares.modules.villagermood;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * Settings of villager trading and mood: infinite trades, plus the drawbacks of abusing them.
 *
 * @param checkIntervalTicks how often every loaded villager is evaluated
 * @param trading            infinite trades and restock behaviour
 * @param depression         how confinement in a 1x1 cell turns into depression
 * @param prices             how depression and annoyance raise prices
 * @param spam               when rapid trading annoys a villager
 * @param escape             escaping through 1x1 holes
 * @param cliff              depressed villagers jumping off cliffs
 * @param riot               villagers rioting against a player
 * @param mafia              temporary enforcer crews attracted by many boxed villagers
 * @param insults            lines a fed-up villager says to the player
 * @param riotShouts         lines shouted during a riot
 */
public record VillagerMoodSettings(
    long checkIntervalTicks,
    Trading trading,
    Depression depression,
    Prices prices,
    Spam spam,
    Escape escape,
    Cliff cliff,
    Riot riot,
    VillagerMafiaSettings mafia,
    List<String> insults,
    List<String> riotShouts
) {

    private static final String ROOT = "villager-mood.";

    public record Trading(boolean infinite, boolean refreshOnOpen) {
    }

    /**
     * @param confinedTicksBeforeDepression confinement time before the first depression level
     * @param ticksPerLevel                 further confinement time for each additional level
     * @param maxLevel                      highest depression level
     * @param recoveryRate                  how much faster than it builds up the confinement time drains once free
     */
    public record Depression(long confinedTicksBeforeDepression, long ticksPerLevel, int maxLevel, double recoveryRate) {
    }

    /**
     * Fractions of each trade's base cost added as extra price.
     */
    public record Prices(double perDepressionLevel, double perAnnoyanceLevel, double maxSurcharge) {
    }

    /**
     * @param windowTicks       sliding window in which trades are counted
     * @param maxTradesInWindow trades allowed in the window before the villager gets annoyed
     * @param ticksPerLevel     how long each annoyance level lasts before decaying
     * @param maxLevel          highest annoyance level
     */
    public record Spam(long windowTicks, int maxTradesInWindow, long ticksPerLevel, int maxLevel) {
    }

    public record Escape(boolean enabled, long minConfinedTicks, double chancePerCheck) {
    }

    public record Cliff(boolean enabled, int minDepression, double chancePerCheck, int searchRadius, int minDrop,
                        long attemptTicks) {
    }

    /**
     * @param minMood     depression + annoyance needed for a villager to join a riot
     * @param minRioters  unhappy villagers needed around the player
     * @param damage      damage of each punch of a rioter next to the player
     */
    public record Riot(boolean enabled, double radius, int minMood, int minRioters, double chancePerCheck,
                       long durationTicks, long cooldownTicks, double damage, boolean summonGolem) {
    }

    public static VillagerMoodSettings defaults() {
        return new VillagerMoodSettings(
            100L,
            new Trading(true, true),
            // 10 minutes boxed in before the first level, then one level every 5 minutes.
            new Depression(12_000L, 6_000L, 5, 2.0),
            new Prices(0.15, 0.10, 1.5),
            // More than 32 trades in 10 seconds annoys the villager for 5 minutes per level.
            new Spam(200L, 32, 6_000L, 5),
            new Escape(true, 6_000L, 0.02),
            new Cliff(true, 2, 0.05, 12, 6, 400L),
            new Riot(true, 12.0, 2, 3, 0.02, 400L, 12_000L, 2.0, true),
            VillagerMafiaSettings.defaults(),
            List.of(
                "¿Otra vez vos? ¡Andá a laburar!",
                "Me tenés podrido con tanto trueque.",
                "¿Te creés que soy un cajero automático?",
                "Pará un poco, que no soy tu esclavo.",
                "Hmmm... ahora te sale más caro, vivo.",
                "¡Tomatelás! Volvé cuando aprendas modales."
            ),
            List.of(
                "¡Libertad para los aldeanos!",
                "¡Basta de celdas de un bloque!",
                "¡Abajo el explotador!",
                "¡Hmmm! ¡HMMM!",
                "¡Queremos ventanas!"
            )
        );
    }

    public static VillagerMoodSettings load(FileConfiguration config) {
        VillagerMoodSettings d = defaults();
        try {
            return new VillagerMoodSettings(
                config.getLong(ROOT + "check-interval-ticks", d.checkIntervalTicks()),
                new Trading(
                    config.getBoolean(ROOT + "trading.infinite", d.trading().infinite()),
                    config.getBoolean(ROOT + "trading.refresh-on-open", d.trading().refreshOnOpen())
                ),
                new Depression(
                    config.getLong(ROOT + "depression.confined-ticks-before-depression",
                        d.depression().confinedTicksBeforeDepression()),
                    config.getLong(ROOT + "depression.ticks-per-level", d.depression().ticksPerLevel()),
                    config.getInt(ROOT + "depression.max-level", d.depression().maxLevel()),
                    config.getDouble(ROOT + "depression.recovery-rate", d.depression().recoveryRate())
                ),
                new Prices(
                    config.getDouble(ROOT + "prices.per-depression-level", d.prices().perDepressionLevel()),
                    config.getDouble(ROOT + "prices.per-annoyance-level", d.prices().perAnnoyanceLevel()),
                    config.getDouble(ROOT + "prices.max-surcharge", d.prices().maxSurcharge())
                ),
                new Spam(
                    config.getLong(ROOT + "spam.window-ticks", d.spam().windowTicks()),
                    config.getInt(ROOT + "spam.max-trades-in-window", d.spam().maxTradesInWindow()),
                    config.getLong(ROOT + "spam.ticks-per-level", d.spam().ticksPerLevel()),
                    config.getInt(ROOT + "spam.max-level", d.spam().maxLevel())
                ),
                new Escape(
                    config.getBoolean(ROOT + "escape.enabled", d.escape().enabled()),
                    config.getLong(ROOT + "escape.min-confined-ticks", d.escape().minConfinedTicks()),
                    config.getDouble(ROOT + "escape.chance-per-check", d.escape().chancePerCheck())
                ),
                new Cliff(
                    config.getBoolean(ROOT + "cliff.enabled", d.cliff().enabled()),
                    config.getInt(ROOT + "cliff.min-depression", d.cliff().minDepression()),
                    config.getDouble(ROOT + "cliff.chance-per-check", d.cliff().chancePerCheck()),
                    config.getInt(ROOT + "cliff.search-radius", d.cliff().searchRadius()),
                    config.getInt(ROOT + "cliff.min-drop", d.cliff().minDrop()),
                    config.getLong(ROOT + "cliff.attempt-ticks", d.cliff().attemptTicks())
                ),
                new Riot(
                    config.getBoolean(ROOT + "riot.enabled", d.riot().enabled()),
                    config.getDouble(ROOT + "riot.radius", d.riot().radius()),
                    config.getInt(ROOT + "riot.min-mood", d.riot().minMood()),
                    config.getInt(ROOT + "riot.min-rioters", d.riot().minRioters()),
                    config.getDouble(ROOT + "riot.chance-per-check", d.riot().chancePerCheck()),
                    config.getLong(ROOT + "riot.duration-ticks", d.riot().durationTicks()),
                    config.getLong(ROOT + "riot.cooldown-ticks", d.riot().cooldownTicks()),
                    config.getDouble(ROOT + "riot.damage", d.riot().damage()),
                    config.getBoolean(ROOT + "riot.summon-golem", d.riot().summonGolem())
                ),
                VillagerMafiaSettings.load(config),
                lines(config, "insults", d.insults()),
                lines(config, "riot-shouts", d.riotShouts())
            );
        } catch (IllegalArgumentException invalid) {
            return d;
        }
    }

    private static List<String> lines(FileConfiguration config, String key, List<String> fallback) {
        ConfigurationSection section = config.getConfigurationSection("villager-mood");
        if (section == null || !section.isList(key)) {
            return fallback;
        }
        List<String> lines = section.getStringList(key).stream().filter(line -> !line.isBlank()).toList();
        return lines.isEmpty() ? fallback : lines;
    }

    public VillagerMoodSettings {
        insults = List.copyOf(insults);
        riotShouts = List.copyOf(riotShouts);
        if (checkIntervalTicks <= 0L) {
            throw new IllegalArgumentException("check-interval-ticks debe ser mayor a cero.");
        }
        if (depression.confinedTicksBeforeDepression() < 0L || depression.ticksPerLevel() <= 0L
            || depression.maxLevel() < 0 || depression.recoveryRate() < 0.0) {
            throw new IllegalArgumentException("La configuración de depresión no es válida.");
        }
        if (prices.perDepressionLevel() < 0.0 || prices.perAnnoyanceLevel() < 0.0 || prices.maxSurcharge() < 0.0) {
            throw new IllegalArgumentException("Los recargos de precio no pueden ser negativos.");
        }
        if (spam.windowTicks() <= 0L || spam.maxTradesInWindow() <= 0 || spam.ticksPerLevel() <= 0L
            || spam.maxLevel() < 0) {
            throw new IllegalArgumentException("La configuración de spam no es válida.");
        }
        requireChance(escape.chancePerCheck());
        requireChance(cliff.chancePerCheck());
        requireChance(riot.chancePerCheck());
        if (escape.minConfinedTicks() < 0L || cliff.searchRadius() < 1 || cliff.minDrop() < 1
            || cliff.attemptTicks() <= 0L) {
            throw new IllegalArgumentException("La configuración de escape o precipicio no es válida.");
        }
        if (riot.radius() <= 0.0 || riot.minRioters() < 1 || riot.durationTicks() <= 0L
            || riot.cooldownTicks() < 0L || riot.damage() < 0.0) {
            throw new IllegalArgumentException("La configuración de motín no es válida.");
        }
    }

    private static void requireChance(double chance) {
        if (chance < 0.0 || chance > 1.0) {
            throw new IllegalArgumentException("Las probabilidades deben estar entre 0 y 1.");
        }
    }
}
