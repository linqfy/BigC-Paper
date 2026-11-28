package dev.linqfy.bigCasares.modules.toffy.eventos;

import org.bukkit.Material;

/**
 * Blocks the code-built arenas are made of, configurable as {@code arena.materials} in {@code toffy_eventos.yml}. The
 * defaults are the blocks the arenas always used.
 */
public record ArenaMaterials(
    Material kothPlatform,
    Material kothEdge,
    Material hill,
    Material hillRing,
    Material tntPlatform,
    Material tntPlatformAlt,
    Material tntOuter,
    Material wall,
    Material spleefFloor
) {
    public static ArenaMaterials defaults() {
        return new ArenaMaterials(
            Material.SMOOTH_STONE,
            Material.POLISHED_BLACKSTONE,
            Material.GOLD_BLOCK,
            Material.STONE_BRICKS,
            Material.RED_CONCRETE,
            Material.WHITE_CONCRETE,
            Material.BLACK_CONCRETE,
            Material.BARRIER,
            Material.SNOW_BLOCK);
    }
}
