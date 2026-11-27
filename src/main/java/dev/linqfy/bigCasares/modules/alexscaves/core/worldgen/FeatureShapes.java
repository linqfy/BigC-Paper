package dev.linqfy.bigCasares.modules.alexscaves.core.worldgen;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Small shape and block-class helpers shared by the biome features. */
public final class FeatureShapes {
    /** Natural stone-like blocks a cave biome repaints with its own rock. */
    public static final Set<String> STONE_LIKE = Set.of(
        "STONE", "DEEPSLATE", "TUFF", "ANDESITE", "DIORITE", "GRANITE", "CALCITE", "DIRT", "GRAVEL",
        "COBBLESTONE", "COBBLED_DEEPSLATE", "SMOOTH_BASALT", "DRIPSTONE_BLOCK", "MOSS_BLOCK", "CLAY");
    public static final List<int[]> DIRECTIONS = List.of(
        new int[] {0, -1, 0}, new int[] {0, 1, 0}, new int[] {0, 0, -1},
        new int[] {0, 0, 1}, new int[] {-1, 0, 0}, new int[] {1, 0, 0});
    /** Names matching {@link #DIRECTIONS}, as used by module block variants. */
    public static final List<String> DIRECTION_NAMES = List.of("down", "up", "north", "south", "west", "east");

    private FeatureShapes() {
    }

    public static boolean isStoneLike(String material) {
        return STONE_LIKE.contains(material.toUpperCase(Locale.ROOT));
    }

    public static boolean isIronOre(String material) {
        String name = material.toUpperCase(Locale.ROOT);
        return name.equals("IRON_ORE") || name.equals("DEEPSLATE_IRON_ORE");
    }

    public static boolean isAir(String material) {
        String name = material.toUpperCase(Locale.ROOT);
        return name.equals("AIR") || name.equals("CAVE_AIR");
    }

    /** True when {@code (dx, dy, dz)} lies inside a sphere of the given radius (block centres). */
    public static boolean inSphere(int dx, int dy, int dz, double radius) {
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    public static int opposite(int direction) {
        return direction ^ 1;
    }
}
