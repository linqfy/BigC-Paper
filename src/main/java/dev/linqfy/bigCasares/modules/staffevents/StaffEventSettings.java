package dev.linqfy.bigCasares.modules.staffevents;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@code staff-events} configuration. Invalid values fall back to the defaults with a warning. */
public record StaffEventSettings(
    boolean arenaDimension,
    String fallbackArenaWorld,
    int arenaY,
    int lobbySeconds,
    Lifesteal lifesteal,
    Koth koth,
    TntTag tntTag,
    Spleef spleef,
    List<String> warnings
) {
    private static final String PREFIX = "staff-events.";

    public StaffEventSettings {
        fallbackArenaWorld = fallbackArenaWorld == null ? "" : fallbackArenaWorld.trim();
        warnings = List.copyOf(warnings);
    }

    /** Items ({@code id:amount}, catalog ids or {@code minecraft:} materials) and console commands ({@code {player}}). */
    public record Rewards(List<String> items, List<String> commands) {
        public Rewards {
            items = List.copyOf(items);
            commands = List.copyOf(commands);
        }

        public static Rewards none() {
            return new Rewards(List.of(), List.of());
        }

        public boolean isEmpty() {
            return items.isEmpty() && commands.isEmpty();
        }
    }

    public record Lifesteal(int durationMinutes, LifestealRules rules, int maxPermanentBonusHearts) {
    }

    public record Koth(int durationMinutes, int captureSeconds, int minPlayers, int platformRadius,
                       int hillRadius, int knockbackLevel, Rewards rewards) {
    }

    public record TntTag(int roundSeconds, int playersPerTagger, int minPlayers, int platformRadius,
                         int maxMinutes, Rewards rewards) {
    }

    public record Spleef(int minPlayers, int radius, int layers, int layerGap, int efficiencyLevel,
                         int graceSeconds, int maxMinutes, Rewards rewards) {
    }

    public int minPlayers(StaffEventType type) {
        return switch (type) {
            case LIFESTEAL -> 1;
            case KOTH -> koth.minPlayers();
            case TNT_TAG -> tntTag.minPlayers();
            case SPLEEF -> spleef.minPlayers();
        };
    }

    public static StaffEventSettings load(ConfigurationSection config) {
        Reader reader = new Reader(config);
        LifestealRules rules;
        int floor = reader.positive("lifesteal.floor-hearts", 3);
        int max = reader.positive("lifesteal.max-hearts", 20);
        if (max < floor) {
            reader.warnings.add("lifesteal.max-hearts is lower than floor-hearts; using " + floor);
            max = floor;
        }
        rules = new LifestealRules(
            reader.positive("lifesteal.hearts-per-kill", 1), floor, max,
            reader.positive("lifesteal.permanent-reward-threshold", 12));
        Lifesteal lifesteal = new Lifesteal(
            reader.positive("lifesteal.duration-minutes", 60), rules,
            reader.nonNegative("lifesteal.max-permanent-bonus-hearts", 10));
        Koth koth = new Koth(
            reader.positive("koth.duration-minutes", 10),
            reader.positive("koth.capture-seconds", 120),
            reader.atLeast("koth.min-players", 2, 1),
            reader.between("koth.platform-radius", 20, 6, 48),
            reader.between("koth.hill-radius", 3, 1, 10),
            reader.between("koth.knockback-level", 5, 1, 255),
            reader.rewards("koth.rewards"));
        TntTag tntTag = new TntTag(
            reader.between("tnt-tag.round-seconds", 30, 5, 600),
            reader.positive("tnt-tag.players-per-tagger", 4),
            reader.atLeast("tnt-tag.min-players", 2, 2),
            reader.between("tnt-tag.platform-radius", 15, 6, 48),
            reader.positive("tnt-tag.max-minutes", 15),
            reader.rewards("tnt-tag.rewards", List.of("nuke_shot:1")));
        Spleef spleef = new Spleef(
            reader.atLeast("spleef.min-players", 2, 2),
            reader.between("spleef.radius", 12, 4, 40),
            reader.between("spleef.layers", 2, 1, 5),
            reader.between("spleef.layer-gap", 6, 3, 20),
            reader.between("spleef.efficiency-level", 255, 1, 255),
            reader.between("spleef.grace-seconds", 5, 0, 60),
            reader.positive("spleef.max-minutes", 10),
            reader.rewards("spleef.rewards"));
        return new StaffEventSettings(
            config.getBoolean(PREFIX + "arena-dimension", true),
            config.getString(PREFIX + "fallback-arena-world", ""),
            reader.between("arena-y", 100, 0, 250),
            reader.between("lobby-seconds", 30, 0, 600),
            lifesteal, koth, tntTag, spleef, reader.warnings);
    }

    private static final class Reader {
        private final ConfigurationSection config;
        private final List<String> warnings = new ArrayList<>();

        private Reader(ConfigurationSection config) {
            this.config = config;
        }

        int positive(String path, int fallback) {
            return atLeast(path, fallback, 1);
        }

        int nonNegative(String path, int fallback) {
            return atLeast(path, fallback, 0);
        }

        int atLeast(String path, int fallback, int minimum) {
            return between(path, fallback, minimum, Integer.MAX_VALUE);
        }

        int between(String path, int fallback, int minimum, int maximum) {
            int value = config.getInt(PREFIX + path, fallback);
            if (value < minimum || value > maximum) {
                warnings.add(path + " must be between " + minimum + " and " + maximum + "; using " + fallback);
                return fallback;
            }
            return value;
        }

        Rewards rewards(String path) {
            return rewards(path, List.of());
        }

        Rewards rewards(String path, List<String> defaultItems) {
            String itemsPath = PREFIX + path + ".items";
            List<String> items = config.isList(itemsPath) ? config.getStringList(itemsPath) : defaultItems;
            List<String> valid = new ArrayList<>();
            for (String item : items) {
                if (RewardItem.parse(item).isPresent()) {
                    valid.add(item.trim().toLowerCase(Locale.ROOT));
                } else {
                    warnings.add(path + ".items has an invalid entry '" + item + "' (expected id or id:amount)");
                }
            }
            return new Rewards(valid, config.getStringList(PREFIX + path + ".commands"));
        }
    }
}
