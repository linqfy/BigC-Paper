package dev.linqfy.bigCasares.modules.alexscaves.abyssal;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * The mod's barter loot tables ({@code gameplay/deep_one_barter}, {@code deep_one_knight_barter} and
 * {@code deep_one_mage_barter}): one roll of a weighted entry with a uniform count. Item ids are module catalog ids
 * ({@code ac_...}) or vanilla material names.
 */
public final class DeepOneBarter {
    /** What an entry does to its item besides setting the count. */
    public enum Special {
        NONE,
        /** {@code enchant_randomly}: one random enchantment (from {@code detail} when it lists some, comma separated). */
        ENCHANT,
        /** {@code set_potion}: the potion type in {@code detail}. */
        POTION
    }

    public record Entry(String item, int weight, int min, int max, Special special, String detail) {
        public Entry {
            if (weight <= 0 || min < 1 || max < min) {
                throw new IllegalArgumentException("Entrada de trueque inválida: " + item);
            }
        }
    }

    /** One roll: the entry and the count it gives. */
    public record Result(Entry entry, int count) {
    }

    private static Entry entry(String item, int weight, int min, int max) {
        return new Entry(item, weight, min, max, Special.NONE, "");
    }

    private static Entry enchanted(String item, int weight, String enchantments) {
        return new Entry(item, weight, 1, 1, Special.ENCHANT, enchantments);
    }

    private static Entry potion(int weight, String type) {
        return new Entry("potion", weight, 1, 1, Special.POTION, type);
    }

    public static final List<Entry> DEEP_ONE = List.of(
        entry("kelp", 70, 1, 3),
        enchanted("fishing_rod", 7, ""),
        entry("water_bucket", 3, 1, 1),
        entry("sponge", 20, 1, 1),
        entry("ac_ping_pong_sponge", 20, 1, 1),
        entry("ac_dusk_anemone", 20, 1, 1),
        entry("ac_twilight_anemone", 20, 1, 1),
        entry("ac_midnight_anemone", 20, 1, 1),
        entry("ac_mussel", 25, 1, 2),
        entry("ac_abyssmarine", 20, 5, 15),
        entry("ink_sac", 10, 1, 1),
        entry("glow_ink_sac", 10, 1, 1),
        entry("ac_ink_bomb", 35, 2, 5),
        entry("clay_ball", 60, 1, 1),
        entry("ac_gazing_pearl", 26, 1, 1),
        entry("ac_rusty_barrel", 4, 1, 1),
        entry("glass_bottle", 6, 1, 1),
        entry("prismarine_shard", 80, 6, 15),
        entry("cod", 80, 1, 2),
        entry("salmon", 80, 1, 2),
        entry("pufferfish", 20, 1, 2),
        entry("ac_sea_glass_shards", 10, 4, 8));

    public static final List<Entry> KNIGHT = List.of(
        entry("trident", 5, 1, 1),
        entry("ac_ortholance", 10, 1, 1),
        entry("ac_magic_conch", 10, 1, 1),
        entry("nautilus_shell", 6, 1, 1),
        entry("turtle_scute", 8, 1, 1),
        entry("ac_hollow_bone", 4, 1, 3),
        entry("prismarine_crystals", 80, 6, 12),
        enchanted("iron_boots", 1, "depth_strider"),
        enchanted("leather_boots", 1, "depth_strider"),
        enchanted("leather_helmet", 1, "respiration"),
        enchanted("iron_helmet", 1, "respiration"),
        entry("prismarine_shard", 70, 6, 12),
        entry("bone", 20, 1, 3),
        entry("gold_nugget", 6, 1, 5),
        entry("ac_ink_bomb", 10, 2, 5),
        entry("ac_sea_glass_shards", 14, 4, 8));

    public static final List<Entry> MAGE = List.of(
        entry("raw_gold", 10, 1, 2),
        entry("diamond", 6, 1, 1),
        entry("nautilus_shell", 3, 1, 1),
        entry("ac_sea_staff", 12, 1, 1),
        entry("experience_bottle", 20, 2, 4),
        entry("prismarine_shard", 50, 6, 12),
        entry("prismarine_crystals", 30, 6, 12),
        entry("prismarine", 60, 10, 13),
        entry("dark_prismarine", 25, 5, 10),
        entry("lapis_lazuli", 15, 2, 7),
        enchanted("book", 3, "depth_strider"),
        enchanted("book", 3, "respiration"),
        potion(20, "water_breathing"),
        potion(15, "night_vision"),
        entry("ac_glow_ink_bomb", 24, 2, 5),
        entry("ac_sea_glass_shards", 3, 4, 8));

    private DeepOneBarter() {
    }

    public static int totalWeight(List<Entry> table) {
        int total = 0;
        for (Entry entry : table) {
            total += entry.weight();
        }
        return total;
    }

    public static Result roll(List<Entry> table, RandomGenerator random) {
        int pick = random.nextInt(totalWeight(table));
        for (Entry entry : table) {
            pick -= entry.weight();
            if (pick < 0) {
                return new Result(entry, entry.min() + random.nextInt(entry.max() - entry.min() + 1));
            }
        }
        throw new IllegalStateException("Tabla de trueque vacía");
    }
}
