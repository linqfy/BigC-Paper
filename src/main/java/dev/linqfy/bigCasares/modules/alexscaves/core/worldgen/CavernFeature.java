package dev.linqfy.bigCasares.modules.alexscaves.core.worldgen;

import org.bukkit.Material;

/**
 * Shared rules for carving caves ({@link BowlFeature}) and placing terrain in them: only rock,
 * dirt and ores are carved, never fluids or structures.
 */
public final class CavernFeature {
    private CavernFeature() {
    }

    public static boolean carvable(Material material) {
        String name = material.name();
        return FeatureShapes.isStoneLike(name) || name.endsWith("_ORE");
    }

    /**
     * Blocks the mod's surface rules turn into the biome's rock: the terrain's stone and deepslate, and what vanilla
     * ore features put in it (the mod runs its surface rule first, so those never appear). Geodes, dungeons and the
     * module's own structures, placed after the surface rule in the mod, are left alone.
     */
    public static boolean surfaceRock(Material material) {
        return switch (material) {
            case STONE, DEEPSLATE, TUFF, ANDESITE, DIORITE, GRANITE, DIRT, GRAVEL, CLAY -> true;
            default -> material.name().endsWith("_ORE") || vanillaCaveRock(material);
        };
    }

    /**
     * Blocks of the vanilla cave biomes (dripstone caves, lush caves, deep dark, 26.2 sulfur caves). Inside a cave
     * biome of the mod those biomes never generate, so their blocks become the biome's rock.
     */
    public static boolean vanillaCaveRock(Material material) {
        return switch (material) {
            case DRIPSTONE_BLOCK, MOSS_BLOCK, SCULK, SCULK_CATALYST, ROOTED_DIRT -> true;
            default -> material.name().equals("SULFUR") || material.name().equals("CINNABAR");
        };
    }

    /** Plants and spikes of the vanilla cave biomes: cleared inside a cave biome of the mod, like their blocks. */
    public static boolean vanillaCaveDecoration(Material material) {
        return switch (material) {
            case POINTED_DRIPSTONE, SCULK_VEIN, SCULK_SENSOR, SCULK_SHRIEKER, SPORE_BLOSSOM, AZALEA, FLOWERING_AZALEA,
                 BIG_DRIPLEAF, BIG_DRIPLEAF_STEM, SMALL_DRIPLEAF, CAVE_VINES, CAVE_VINES_PLANT, MOSS_CARPET,
                 HANGING_ROOTS, GLOW_LICHEN -> true;
            default -> false;
        };
    }
}
