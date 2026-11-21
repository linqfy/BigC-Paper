/*
 * Transcribed from Doomlike Dungeons (https://github.com/BlackJar72/DoomlikeDungeons),
 * MIT License, Copyright (c) 2014-2020 Jared Blackburn; 1.20 port by JaredBGreat and Xiroc.
 * The full licence text ships in mythicdungeons/third_party/doomlike_dungeons/LICENSE.
 */
package dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.thirdparty;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The Doomlike Dungeons themes ({@code dldungeons/theming/themes/*.cfg}) reduced to what the
 * layout uses: the probability tables of each feature, fences and liquids. Walls, floors, ceilings
 * and pillars come from the culture palette instead of the theme's block lists.
 */
enum DoomlikeTheme {

    COMMON(t(1, 30, 50, 20, 10, 0), t(5, 30, 60, 40, 20, 0), t(5, 15, 30, 75, 25, 0), t(5, 10, 25, 20, 10, 0),
        t(5, 50, 10, 50, 20, 0), t(50, 5, 15, 50, 10, 0), t(5, 10, 25, 75, 15, 0), t(5, 20, 50, 45, 5, 0),
        List.of("minecraft:cobblestone_wall", "minecraft:stone_brick_wall"),
        List.of("minecraft:water", "minecraft:water", "minecraft:magma_block"), false),
    DANK(t(0, 0, 0, 5, 10, 85), t(5, 30, 60, 40, 20, 0), t(5, 15, 30, 75, 25, 0), t(25, 50, 20, 5, 0, 0),
        t(0, 5, 50, 50, 20, 0), t(50, 5, 15, 50, 10, 0), t(5, 10, 25, 75, 15, 0), t(5, 20, 50, 45, 5, 0),
        List.of("minecraft:mossy_cobblestone_wall", "minecraft:oak_fence"),
        List.of("minecraft:water"), true),
    DESERT(t(10, 50, 30, 10, 1, 0), t(5, 30, 60, 40, 20, 0), t(5, 15, 30, 75, 25, 0), t(5, 10, 25, 20, 10, 0),
        t(5, 50, 10, 50, 20, 0), t(50, 5, 15, 50, 10, 0), t(5, 10, 25, 75, 15, 0), t(5, 20, 50, 45, 5, 0),
        List.of("minecraft:oak_fence"),
        List.of("minecraft:water", "minecraft:magma_block", "minecraft:air", "minecraft:sand"), false),
    FROZEN(t(1, 30, 50, 20, 10, 0), t(5, 30, 60, 40, 20, 0), t(5, 15, 30, 75, 25, 0), t(5, 10, 25, 20, 10, 0),
        t(5, 50, 10, 50, 20, 0), t(50, 5, 15, 50, 10, 0), t(5, 10, 25, 75, 15, 0), t(5, 20, 50, 45, 5, 0),
        List.of("minecraft:cobblestone_wall", "minecraft:oak_fence", "minecraft:stone_brick_wall"),
        List.of("minecraft:ice", "minecraft:ice", "minecraft:powder_snow"), false),
    JUNGLE(t(0, 20, 50, 30, 10, 0), t(0, 20, 60, 60, 40, 5), t(0, 0, 0, 75, 25, 5), t(5, 10, 25, 20, 10, 0),
        t(5, 50, 10, 50, 20, 0), t(0, 5, 15, 50, 10, 0), t(0, 0, 15, 75, 10, 0), t(5, 20, 50, 45, 5, 0),
        List.of("minecraft:mossy_cobblestone_wall", "minecraft:mossy_stone_brick_wall"),
        List.of("minecraft:water", "minecraft:magma_block"), false),
    MESA(t(10, 50, 30, 10, 1, 0), t(5, 30, 60, 40, 20, 0), t(5, 15, 30, 75, 25, 0), t(5, 10, 25, 20, 10, 0),
        t(5, 50, 10, 50, 20, 0), t(50, 5, 15, 50, 10, 0), t(5, 10, 25, 75, 15, 0), t(5, 20, 50, 45, 5, 0),
        // The cfg names "minecraft:fence", which no longer exists; oak fence is what it resolved to.
        List.of("minecraft:oak_fence"),
        List.of("minecraft:water", "minecraft:water", "minecraft:lava", "minecraft:air", "minecraft:air", "minecraft:sand",
            "minecraft:red_sand"), false),
    URBAN(t(10, 30, 50, 20, 1, 0), t(5, 30, 60, 40, 20, 0), t(0, 5, 15, 75, 25, 5), t(15, 25, 20, 10, 0, 0),
        t(0, 0, 0, 10, 90, 0), t(50, 5, 15, 50, 10, 0), t(5, 10, 25, 75, 15, 0), t(0, 0, 0, 25, 50, 25),
        List.of("minecraft:cobblestone_wall", "minecraft:oak_fence", "minecraft:stone_brick_wall", "minecraft:brick_wall"),
        List.of("minecraft:water", "minecraft:water", "minecraft:magma_block"), false),
    VILLAGELIKE(t(10, 30, 50, 20, 0, 0), t(30, 60, 40, 20, 5, 0), t(5, 15, 30, 75, 25, 0), t(15, 25, 20, 10, 0, 0),
        t(0, 5, 50, 50, 20, 5), t(50, 5, 15, 50, 10, 0), t(5, 10, 25, 75, 15, 0), t(5, 20, 50, 45, 5, 0),
        List.of("minecraft:cobblestone_wall", "minecraft:oak_fence"),
        List.of("minecraft:water"), false),
    VOLCANIC(t(0, 0, 5, 85, 10, 0), t(5, 30, 60, 40, 20, 0), t(20, 30, 75, 25, 0, 0), t(0, 0, 5, 55, 15, 0),
        t(5, 50, 10, 50, 20, 0), t(0, 5, 15, 50, 50, 10), t(5, 10, 25, 75, 15, 0), t(5, 20, 50, 45, 5, 0),
        List.of("minecraft:cobblestone_wall"),
        List.of("minecraft:lava", "minecraft:magma_block"), false);

    /**
     * Culture -> the two Doomlike themes its dungeon variants use. Desert and mesa suit the sand
     * cultures, frozen the northern one, jungle the Mesoamerican one, urban/villagelike the city
     * builders, dank the medieval crypts and volcanic the Andes.
     */
    static final Map<String, List<DoomlikeTheme>> BY_CULTURE = Map.of(
        "greek", List.of(URBAN, VILLAGELIKE),
        "norse", List.of(FROZEN, FROZEN),
        "medieval", List.of(DANK, VILLAGELIKE),
        "egyptian", List.of(DESERT, DESERT),
        "mesopotamian", List.of(DESERT, DANK),
        "japanese", List.of(URBAN, COMMON),
        "mesoamerican", List.of(JUNGLE, JUNGLE),
        "arabian", List.of(MESA, DESERT),
        "andean", List.of(VOLCANIC, MESA));

    /** Doomlike's Degree: a probability out of ten. */
    enum Degree {
        NONE(0), FEW(1), SOME(3), PLENTY(5), HEAPS(9), ALWAYS(10);

        final int value;

        Degree(int value) {
            this.value = value;
        }

        boolean use(Random random) {
            return random.nextInt(10) < value;
        }
    }

    /** Doomlike's Element: weights for NONE..ALWAYS, one degree drawn per dungeon. */
    record Table(int[] weights) {
        Degree select(Random random) {
            int total = 0;
            for (int weight : weights) {
                total += weight;
            }
            if (total <= 0) {
                return Degree.NONE;
            }
            int roll = random.nextInt(total);
            for (int index = 0; index < weights.length; index++) {
                if (roll < weights[index]) {
                    return Degree.values()[index];
                }
                roll -= weights[index];
            }
            return Degree.NONE;
        }
    }

    final Table liquids;
    final Table pillars;
    final Table symmetry;
    final Table verticle;
    final Table islands;
    final Table degeneracy;
    final Table complexity;
    final Table subrooms;
    final List<String> fences;
    final List<String> liquidBlocks;
    /** Swampy themes sink pools by one block instead of two. */
    final boolean swampy;

    DoomlikeTheme(Table liquids, Table pillars, Table symmetry, Table verticle, Table islands, Table degeneracy,
                  Table complexity, Table subrooms, List<String> fences, List<String> liquidBlocks, boolean swampy) {
        this.liquids = liquids;
        this.pillars = pillars;
        this.symmetry = symmetry;
        this.verticle = verticle;
        this.islands = islands;
        this.degeneracy = degeneracy;
        this.complexity = complexity;
        this.subrooms = subrooms;
        this.fences = fences;
        this.liquidBlocks = liquidBlocks;
        this.swampy = swampy;
    }

    private static Table t(int... weights) {
        return new Table(weights);
    }
}
