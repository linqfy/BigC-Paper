package dev.linqfy.bigCasares.modules.tideframe.engine.core.demo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything a server owner can customise about the boss key prompts (config.yml, section boss-fight):
 * which keys appear, timing, lives and damage; every colour, position and size of the overlay; every text and sound.
 * Immutable and validated; {@link #defaults()} is the built-in look.
 */
public record BossFightSettings(List<KeyChallenge.Key> keys, int lives, int startTicks, int minTicks, int enragePercent, int hitDamage,
                                double missDamage, Map<String, String> colors, Map<String, Double> layout, Map<String, Boolean> show,
                                Map<String, String> texts, Map<String, String> sounds) {
    public static final Map<String, String> DEFAULT_COLORS = ordered(
            "boss-name", "#ffffff",
            "key", "#ffffff", "key-gradient", "#d9d9d9", "key-letter", "#111111", "key-edge", "#000000", "key-depth", "#5c5c5c",
            "timer", "#ffffff", "timer-track", "#000000", "text", "#ffffff", "outline", "#000000",
            "victory", "#ffffff", "victory-gradient", "#ffd84a", "defeat", "#ffffff", "defeat-gradient", "#ff5a5a");
    public static final Map<String, Double> DEFAULT_LAYOUT = Map.of("boss-y", .76, "key-y", -.3, "key-size", .8);
    public static final Map<String, Boolean> DEFAULT_SHOW = Map.of("lives", true, "combo", true,
            "countdown", true, "text-shadow", false);
    public static final Map<String, String> DEFAULT_TEXTS = ordered(
            "get-ready", "GET READY", "go", "GO!", "perfect", "PERFECT!", "great", "GREAT!", "good", "GOOD!",
            "final-blow", "FINAL BLOW!", "enraged", "HE IS ENRAGED!", "wrong-key", "WRONG KEY!", "too-slow", "TOO SLOW!",
            "you-fall", "YOU FALL...", "victory", "VICTORY!", "defeated", "DEFEATED", "victory-line", "{boss_name} FALLS",
            "defeat-line", "{boss_name} WINS THIS TIME", "lives", "LIVES", "combo", "COMBO x", "score", "SCORE",
            "best-combo", "BEST COMBO", "hits", "HITS", "misses", "MISSES", "boss-name", "THE TIDE KING");
    public static final Map<String, String> DEFAULT_SOUNDS = ordered(
            "hit", "minecraft:entity.player.attack.strong", "final-hit", "minecraft:entity.player.attack.crit",
            "miss", "minecraft:entity.player.hurt", "prompt", "minecraft:block.note_block.pling",
            "countdown", "minecraft:block.note_block.hat", "go", "minecraft:block.bell.use",
            "win", "minecraft:ui.toast.challenge_complete", "lose", "minecraft:entity.wither.spawn");
    /** Keys from older configs (the removed boss health bar): still accepted, ignored. */
    static final Set<String> RETIRED = Set.of("boss-bar", "boss-bar-track", "boss-bar-edge", "boss-bar-width", "boss-health");
    private static final String SOUND = "[a-z0-9_.-]+:[a-z0-9_./-]+|none";

    public BossFightSettings {
        keys = List.copyOf(keys);
        if (keys.isEmpty() || Set.copyOf(keys).size() != keys.size()) throw new IllegalArgumentException("boss-fight.keys needs at least one key, without repeats");
        if (lives < 1 || lives > 20) throw new IllegalArgumentException("boss-fight.lives must be 1-20");
        if (startTicks < 6 || startTicks > 200 || minTicks < 4 || minTicks > startTicks) throw new IllegalArgumentException("boss-fight time: 0.3-10 s, and time-min <= time-start");
        if (enragePercent < 0 || enragePercent > 99) throw new IllegalArgumentException("boss-fight.enrage-at must be 0-99");
        if (hitDamage < 1 || hitDamage > 100) throw new IllegalArgumentException("boss-fight.hit-damage must be 1-100");
        if (!Double.isFinite(missDamage) || missDamage < 0 || missDamage > 40) throw new IllegalArgumentException("boss-fight.miss-damage must be 0-40");
        colors = merge(DEFAULT_COLORS, colors, "colors");
        for (var e : colors.entrySet()) if (!e.getValue().matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("boss-fight.colors." + e.getKey() + " must be #RRGGBB");
        layout = merge(DEFAULT_LAYOUT, layout, "layout");
        check(layout, "boss-y", -1, 1); check(layout, "key-y", -1, 1); check(layout, "key-size", .6, 1.6);
        show = merge(DEFAULT_SHOW, show, "show");
        texts = merge(DEFAULT_TEXTS, texts, "texts");
        for (var e : texts.entrySet()) if (!e.getValue().matches("[ -~]{0,32}")) throw new IllegalArgumentException("boss-fight.texts." + e.getKey() + " must be up to 32 plain ASCII characters");
        sounds = merge(DEFAULT_SOUNDS, sounds, "sounds");
        for (var e : sounds.entrySet()) if (!e.getValue().matches(SOUND)) throw new IllegalArgumentException("boss-fight.sounds." + e.getKey() + " must be a sound id (or none)");
    }

    public static BossFightSettings defaults() {
        return new BossFightSettings(List.of(KeyChallenge.Key.values()), KeyChallenge.PLAYER_MAX, 40, 14, 30, 6, 2.0,
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }

    public String color(String key) { return colors.get(key); }
    public double layout(String key) { return layout.get(key); }
    public boolean show(String key) { return show.get(key); }
    public String text(String key) { return texts.get(key); }
    /** Sound id, or null when set to none. */
    public String sound(String key) { String id = sounds.get(key); return id.equals("none") ? null : id; }

    private static void check(Map<String, Double> map, String key, double min, double max) {
        double value = map.get(key);
        if (!Double.isFinite(value) || value < min || value > max) throw new IllegalArgumentException("boss-fight.layout." + key + " must be " + min + " to " + max);
    }
    private static <V> Map<String, V> merge(Map<String, V> defaults, Map<String, V> given, String section) {
        var out = new LinkedHashMap<>(defaults);
        for (var e : given.entrySet()) {
            if (RETIRED.contains(e.getKey())) continue;
            if (!defaults.containsKey(e.getKey())) throw new IllegalArgumentException("Unknown boss-fight." + section + " key: " + e.getKey());
            if (e.getValue() == null) throw new IllegalArgumentException("boss-fight." + section + "." + e.getKey() + " is empty");
            out.put(e.getKey(), e.getValue());
        }
        return java.util.Collections.unmodifiableMap(out);
    }
    private static Map<String, String> ordered(String... pairs) {
        var out = new LinkedHashMap<String, String>();
        for (int i = 0; i < pairs.length; i += 2) out.put(pairs[i], pairs[i + 1]);
        return java.util.Collections.unmodifiableMap(out);
    }
}
