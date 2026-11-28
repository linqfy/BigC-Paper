package dev.linqfy.bigCasares.modules.toffy.eventos;

import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.configuration.ConfigurationSection;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * {@code toffy_eventos.yml} settings (top-level keys). Invalid values fall back to the defaults with a warning.
 * Every default reproduces the behaviour the events had before these keys existed.
 */
public record ToffyEventSettings(
    boolean enabled,
    boolean arenaDimension,
    String fallbackArenaWorld,
    int arenaY,
    int lobbySeconds,
    List<Integer> lobbyCountdownSeconds,
    Lifesteal lifesteal,
    Koth koth,
    TntTag tntTag,
    Spleef spleef,
    Arena arena,
    Map<ToffyEventType, BossBarSpec> bossBars,
    Map<String, SoundSpec> sounds,
    List<String> allowedCommands,
    Tournaments tournaments,
    List<String> warnings
) {
    /** Keys of {@link #sound(String)}: the sounds the events play themselves (not the chat texts). */
    public static final String SOUND_LOBBY_COUNTDOWN = "lobby-countdown";
    public static final String SOUND_BEGIN = "begin";
    public static final String SOUND_TAG = "tag";
    public static final String SOUND_TNT_WARNING = "tnt-warning";
    public static final String SOUND_EXPLOSION = "explosion";
    public static final String SOUND_WINNER = "winner";

    private static final String PREFIX = "";

    public ToffyEventSettings {
        fallbackArenaWorld = fallbackArenaWorld == null ? "" : fallbackArenaWorld.trim();
        lobbyCountdownSeconds = List.copyOf(lobbyCountdownSeconds);
        bossBars = Map.copyOf(bossBars);
        sounds = Map.copyOf(sounds);
        allowedCommands = List.copyOf(allowedCommands);
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

    /**
     * Lifesteal settings. {@code keepInventoryEveryone} true keeps the inventory of every death on the server while the
     * event runs (the original behaviour); false keeps it only for the sign-ups of the event.
     */
    public record Lifesteal(int durationMinutes, LifestealRules rules, int maxPermanentBonusHearts, int minPlayers,
                            int permanentHeartReward, Rewards rewards, List<Integer> warningSeconds,
                            boolean keepInventoryEveryone) {
        public Lifesteal {
            warningSeconds = List.copyOf(warningSeconds);
        }
    }

    public record Koth(int durationMinutes, int captureSeconds, int minPlayers, int platformRadius,
                       int hillRadius, int knockbackLevel, Rewards rewards) {
    }

    /** TNT Tag settings. {@code warningSeconds} is the countdown below which the fuse sound plays. */
    public record TntTag(int roundSeconds, int playersPerTagger, int minPlayers, int platformRadius,
                         int maxMinutes, Rewards rewards, int warningSeconds, int tagCooldownMillis) {
    }

    public record Spleef(int minPlayers, int radius, int layers, int layerGap, int efficiencyLevel,
                         int graceSeconds, int maxMinutes, Rewards rewards) {
    }

    /** Arena look and build speed: materials, time of day in the arena world, blocks placed per tick. */
    public record Arena(int timeOfDay, int buildBlocksPerTick, ArenaMaterials materials) {
    }

    /** Colour, style and title of the boss bar of one event. An empty title uses the event name. */
    public record BossBarSpec(BarColor color, BarStyle style, String title) {
    }

    /** A sound played by an event: {@code key} is the vanilla id, an empty key plays nothing. */
    public record SoundSpec(String key, float volume, float pitch) {
        public SoundSpec {
            key = key == null ? "" : key;
            volume = ToffyEvents.Text.clampVolume(volume);
            pitch = ToffyEvents.Text.clampPitch(pitch);
        }

        public boolean silent() {
            return key == null || key.isBlank();
        }
    }

    /**
     * {@code torneos}: timings of the scheduled tournament teleports and events, and how times are read and shown.
     * {@code zone} is {@code system} or an IANA zone id. The formats use {@link DateTimeFormatter} patterns.
     */
    public record Tournaments(List<Integer> teleportWarningMinutes, List<Integer> eventWarningMinutes,
                              int lostAfterMinutes, String zone, String dateFormat, String timeFormat) {
        public Tournaments {
            teleportWarningMinutes = List.copyOf(teleportWarningMinutes);
            eventWarningMinutes = List.copyOf(eventWarningMinutes);
        }

        /** The timings and formats the tournaments had before they were configurable. */
        public static Tournaments defaults() {
            return new Tournaments(List.of(5, 1), List.of(5, 1), 5, "system", "dd/MM/yyyy", "HH:mm");
        }

        public ZoneId zoneId() {
            return "system".equalsIgnoreCase(zone) ? ZoneId.systemDefault() : ZoneId.of(zone);
        }
    }

    public int minPlayers(ToffyEventType type) {
        return switch (type) {
            case LIFESTEAL -> lifesteal.minPlayers();
            case KOTH -> koth.minPlayers();
            case TNT_TAG -> tntTag.minPlayers();
            case SPLEEF -> spleef.minPlayers();
        };
    }

    /** Boss bar of the event type; the built-in look when the file has none. */
    public BossBarSpec bossBar(ToffyEventType type) {
        return bossBars.getOrDefault(type, defaultBossBar(type));
    }

    /** Sound of one of the {@code SOUND_*} keys; silent when the key is unknown. */
    public SoundSpec sound(String name) {
        return sounds.getOrDefault(name, new SoundSpec("", 1f, 1f));
    }

    /** Loads the settings without checking the arena materials for solidity (see {@link #load(ConfigurationSection, Predicate)}). */
    public static ToffyEventSettings load(ConfigurationSection config) {
        return load(config, material -> true);
    }

    /**
     * Loads the settings. {@code solid} says whether an arena material may be used (the server passes
     * {@link #isSolidBlock(Material)}; the tests pass their own rule, since the block registry needs a server).
     */
    public static ToffyEventSettings load(ConfigurationSection config, Predicate<Material> solid) {
        Reader reader = new Reader(config, solid);
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
            reader.nonNegative("lifesteal.max-permanent-bonus-hearts", 10),
            reader.atLeast("lifesteal.min-players", 1, 1),
            reader.between("lifesteal.permanent-heart-reward", 1, 0, 20),
            reader.rewards("lifesteal.rewards"),
            reader.seconds("lifesteal.warning-seconds", List.of(300, 60), 1, 86_400),
            reader.bool("lifesteal.keep-inventory-everyone", true));
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
            reader.rewards("tnt-tag.rewards", List.of("nuke_shot:1")),
            reader.between("tnt-tag.warning-seconds", 5, 1, 60),
            reader.between("tnt-tag.tag-cooldown-ms", 1000, 0, 60_000));
        Spleef spleef = new Spleef(
            reader.atLeast("spleef.min-players", 2, 2),
            reader.between("spleef.radius", 12, 4, 40),
            reader.between("spleef.layers", 2, 1, 5),
            reader.between("spleef.layer-gap", 6, 3, 20),
            reader.between("spleef.efficiency-level", 255, 1, 255),
            reader.between("spleef.grace-seconds", 5, 0, 60),
            reader.positive("spleef.max-minutes", 10),
            reader.rewards("spleef.rewards"));
        Arena arena = new Arena(
            reader.between("arena.time-of-day", 6000, 0, 23_999),
            reader.between("arena.build-blocks-per-tick", 512, 16, 100_000),
            reader.materials());
        Map<ToffyEventType, BossBarSpec> bars = new EnumMap<>(ToffyEventType.class);
        for (ToffyEventType type : ToffyEventType.values()) {
            bars.put(type, reader.bossBar(type));
        }
        Map<String, SoundSpec> sounds = new LinkedHashMap<>();
        sounds.put(SOUND_LOBBY_COUNTDOWN, reader.sound(SOUND_LOBBY_COUNTDOWN, "minecraft:block.note_block.hat", 1f, 1.2f));
        sounds.put(SOUND_BEGIN, reader.sound(SOUND_BEGIN, "minecraft:entity.ender_dragon.growl", 0.6f, 1.4f));
        sounds.put(SOUND_TAG, reader.sound(SOUND_TAG, "minecraft:entity.tnt.primed", 1f, 1f));
        sounds.put(SOUND_TNT_WARNING, reader.sound(SOUND_TNT_WARNING, "minecraft:entity.tnt.primed", 0.8f, 1f));
        sounds.put(SOUND_EXPLOSION, reader.sound(SOUND_EXPLOSION, "minecraft:entity.generic.explode", 1f, 1f));
        sounds.put(SOUND_WINNER, reader.sound(SOUND_WINNER, "minecraft:ui.toast.challenge_complete", 1f, 1f));
        List<String> commands = reader.commands("allowed-commands",
            List.of("anotar", "toffy", "tcomp", "evento", "eventos"));
        Tournaments tournaments = reader.tournaments();
        return new ToffyEventSettings(
            config.getBoolean(PREFIX + "enabled", true),
            config.getBoolean(PREFIX + "arena-dimension", true),
            config.getString(PREFIX + "fallback-arena-world", ""),
            reader.between("arena-y", 100, 0, 250),
            reader.between("lobby-seconds", 30, 5, 600),
            reader.seconds("lobby-countdown-seconds", List.of(20, 10), 5, 600),
            lifesteal, koth, tntTag, spleef, arena, bars, sounds, commands, tournaments, reader.warnings);
    }

    /** Key of the event in the {@code bossbar} section of toffy_eventos.yml. */
    static String bossBarKey(ToffyEventType type) {
        return type == ToffyEventType.TNT_TAG ? "tnt-tag" : type.id();
    }

    /** The look of an event when the file does not change it. */
    static BossBarSpec defaultBossBar(ToffyEventType type) {
        return type == ToffyEventType.LIFESTEAL
            ? new BossBarSpec(BarColor.RED, BarStyle.SEGMENTED_10, "")
            : new BossBarSpec(BarColor.YELLOW, BarStyle.SOLID, "");
    }

    /** A solid full block: what the arena walls and floors need. */
    public static boolean isSolidBlock(Material material) {
        // Barrier is solid but not occluding; it is the default invisible arena wall, so it is allowed explicitly.
        return material.isBlock() && material.isSolid() && (material.isOccluding() || material == Material.BARRIER);
    }

    private static final class Reader {
        private final ConfigurationSection config;
        private final Predicate<Material> solid;
        private final List<String> warnings = new ArrayList<>();

        private Reader(ConfigurationSection config, Predicate<Material> solid) {
            this.config = config;
            this.solid = solid;
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

        boolean bool(String path, boolean fallback) {
            return config.getBoolean(PREFIX + path, fallback);
        }

        /** A list of seconds or minutes; invalid entries are dropped with a warning. */
        List<Integer> seconds(String path, List<Integer> fallback, int minimum, int maximum) {
            String fullPath = PREFIX + path;
            if (!config.isList(fullPath)) {
                if (config.contains(fullPath)) {
                    warnings.add(path + " must be a list of numbers; using " + fallback);
                }
                return fallback;
            }
            List<Integer> values = new ArrayList<>();
            for (Object raw : config.getList(fullPath, List.of())) {
                if (raw instanceof Number number && number.intValue() >= minimum && number.intValue() <= maximum) {
                    values.add(number.intValue());
                } else {
                    warnings.add(path + " has an invalid entry '" + raw + "' (must be between " + minimum
                        + " and " + maximum + ")");
                }
            }
            return values;
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

        /**
         * Command names that a participant may use. Entries are lower case, without the slash and without a
         * {@code namespace:} (the runtime compares the bare name). An empty list means the default list.
         */
        List<String> commands(String path, List<String> fallback) {
            String fullPath = PREFIX + path;
            if (!config.isList(fullPath)) {
                return fallback;
            }
            List<String> names = new ArrayList<>();
            for (String raw : config.getStringList(fullPath)) {
                String name = raw.trim().toLowerCase(Locale.ROOT);
                if (name.startsWith("/")) {
                    name = name.substring(1);
                }
                if (name.contains(":")) {
                    name = name.substring(name.indexOf(':') + 1);
                }
                if (!name.isEmpty() && !names.contains(name)) {
                    names.add(name);
                }
            }
            if (names.isEmpty()) {
                warnings.add(path + " esta vacio; se usa la lista por defecto.");
                return fallback;
            }
            return names;
        }

        ArenaMaterials materials() {
            ArenaMaterials defaults = ArenaMaterials.defaults();
            return new ArenaMaterials(
                material("arena.materials.koth-platform", defaults.kothPlatform()),
                material("arena.materials.koth-edge", defaults.kothEdge()),
                material("arena.materials.hill", defaults.hill()),
                material("arena.materials.hill-ring", defaults.hillRing()),
                material("arena.materials.tnt-platform", defaults.tntPlatform()),
                material("arena.materials.tnt-platform-alt", defaults.tntPlatformAlt()),
                material("arena.materials.tnt-outer", defaults.tntOuter()),
                material("arena.materials.wall", defaults.wall()),
                material("arena.materials.spleef-floor", defaults.spleefFloor()));
        }

        Material material(String path, Material fallback) {
            String raw = config.getString(PREFIX + path);
            if (raw == null || raw.isBlank()) {
                return fallback;
            }
            Material material = Material.matchMaterial(raw.trim());
            if (material == null || !solid.test(material)) {
                warnings.add(path + " must be a solid block (for example WHITE_CONCRETE); using " + fallback.name());
                return fallback;
            }
            return material;
        }

        BossBarSpec bossBar(ToffyEventType type) {
            BossBarSpec defaults = defaultBossBar(type);
            String base = "bossbar." + bossBarKey(type);
            return new BossBarSpec(
                enumValue(base + ".color", BarColor.class, defaults.color()),
                enumValue(base + ".style", BarStyle.class, defaults.style()),
                config.getString(PREFIX + base + ".title", ""));
        }

        <E extends Enum<E>> E enumValue(String path, Class<E> type, E fallback) {
            String raw = config.getString(PREFIX + path);
            if (raw == null || raw.isBlank()) {
                return fallback;
            }
            try {
                return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException unknown) {
                warnings.add(path + " must be one of " + Arrays.toString(type.getEnumConstants())
                    + "; using " + fallback.name());
                return fallback;
            }
        }

        SoundSpec sound(String name, String key, float volume, float pitch) {
            String base = "sounds." + name;
            String configured = config.getString(PREFIX + base + ".sonido");
            if (configured == null) {
                return new SoundSpec(key, volume, pitch);
            }
            float v = (float) config.getDouble(PREFIX + base + ".volumen", volume);
            float p = (float) config.getDouble(PREFIX + base + ".tono", pitch);
            if (v != ToffyEvents.Text.clampVolume(v) || p != ToffyEvents.Text.clampPitch(p)) {
                warnings.add(base + ": volumen debe estar entre 0 y 10 y tono entre 0.5 y 2; se ajusta.");
            }
            String soundKey = ToffyEvents.Text.soundKey(configured);
            if (soundKey == null) {
                warnings.add(base + ".sonido '" + configured + "' no es una clave valida (por ejemplo minecraft:block.note_block.hat); queda mudo.");
                soundKey = "";
            }
            return new SoundSpec(soundKey, v, p);
        }

        Tournaments tournaments() {
            String base = "torneos.";
            String zone = config.getString(PREFIX + base + "zona-horaria", "system").trim();
            try {
                if (!"system".equalsIgnoreCase(zone)) {
                    ZoneId.of(zone);
                }
            } catch (DateTimeException unknown) {
                warnings.add(base + "zona-horaria '" + zone + "' is not a zone id; using system");
                zone = "system";
            }
            return new Tournaments(
                seconds(base + "avisos-tp-minutos", List.of(5, 1), 1, 1440),
                seconds(base + "avisos-evento-minutos", List.of(5, 1), 1, 1440),
                between(base + "perdido-tras-minutos", 5, 1, 1440),
                zone,
                pattern(base + "formato-fecha", "dd/MM/yyyy"),
                pattern(base + "formato-hora", "HH:mm"));
        }

        String pattern(String path, String fallback) {
            String raw = config.getString(PREFIX + path, fallback).trim();
            try {
                DateTimeFormatter.ofPattern(raw, Locale.ROOT);
                return raw;
            } catch (IllegalArgumentException invalid) {
                warnings.add(path + " is not a valid format '" + raw + "'; using " + fallback);
                return fallback;
            }
        }
    }
}
