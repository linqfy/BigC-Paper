package dev.linqfy.bigCasares.modules.sift;

import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

/**
 * What a Singer gives for a Soul Block: the port of the roll in the mod's {@code SingerBarter.reward}. The
 * table adds up to 200; the last entry, taken by every roll that no other entry caught, is a random wool.
 * Items are given as ids (vanilla ones as {@code minecraft:*}) so the roll runs without a server.
 */
final class SingerBarter {

    static final String MUSIC_DISC_WELLSPRING = "the_sift:music_disc_wellspring";

    /** The mod's {@code Blocks.WOOL.asList()} order. */
    static final List<String> WOOL = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black").stream()
        .map(color -> "minecraft:" + color + "_wool").toList();

    private static final int TOTAL = 200;
    private static final int REROLLS = 32;

    /** One row of the table: the weight, the item and the inclusive count range. */
    record Entry(int weight, String item, int minimum, int maximum) {
    }

    static final List<Entry> TABLE = List.of(
        new Entry(30, "minecraft:iron_block", 2, 4),
        new Entry(20, "minecraft:netherite_upgrade_smithing_template", 1, 1),
        new Entry(19, "minecraft:ancient_debris", 4, 8),
        new Entry(3, MUSIC_DISC_WELLSPRING, 1, 1),
        new Entry(20, "minecraft:gold_block", 1, 2),
        new Entry(11, "minecraft:wheat_seeds", 6, 16),
        new Entry(9, "minecraft:beetroot_seeds", 4, 8),
        new Entry(10, "minecraft:potato", 6, 16),
        new Entry(10, "minecraft:carrot", 8, 20),
        new Entry(11, "minecraft:netherite_ingot", 1, 1),
        new Entry(16, "minecraft:bookshelf", 4, 12),
        new Entry(8, "minecraft:end_stone", 2, 12),
        new Entry(6, "minecraft:chorus_flower", 1, 1),
        new Entry(5, "minecraft:echo_shard", 1, 3),
        new Entry(5, "minecraft:redstone_block", 4, 16),
        new Entry(8, "minecraft:string", 4, 16));

    /** What the last, unlisted outcome gives: a random wool, nine to 24 of them. */
    private static final int WOOL_MINIMUM = 9;
    private static final int WOOL_MAXIMUM = 24;

    /** One stack handed over. */
    record Reward(String item, int count) {
    }

    private SingerBarter() {
    }

    /** One roll: a weighted pick from {@link #TABLE}, then the count (the mod's draw order). */
    static Reward roll(Random random) {
        int roll = random.nextInt(TOTAL);
        for (Entry entry : TABLE) {
            roll -= entry.weight();
            if (roll < 0) {
                return new Reward(entry.item(), count(random, entry.minimum(), entry.maximum()));
            }
        }
        String wool = WOOL.get(random.nextInt(WOOL.size()));
        return new Reward(wool, count(random, WOOL_MINIMUM, WOOL_MAXIMUM));
    }

    /**
     * As {@link #roll}, but an outcome whose item the server does not have (the Music Disc Wellspring, which is
     * not imported) is rolled again, up to {@value #REROLLS} times, then gives a white wool.
     */
    static Reward rollAvailable(Random random, Predicate<String> available) {
        for (int attempt = 0; attempt < REROLLS; attempt++) {
            Reward reward = roll(random);
            if (available.test(reward.item())) {
                return reward;
            }
        }
        return new Reward(WOOL.get(0), count(random, WOOL_MINIMUM, WOOL_MAXIMUM));
    }

    private static int count(Random random, int minimum, int maximum) {
        return minimum + random.nextInt(maximum - minimum + 1);
    }
}
