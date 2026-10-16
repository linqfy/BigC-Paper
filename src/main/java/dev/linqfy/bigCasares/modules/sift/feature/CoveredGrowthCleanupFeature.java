package dev.linqfy.bigCasares.modules.sift.feature;

import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;
import dev.linqfy.bigCasares.modules.sift.SiftBlocks;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Port of the mod's {@code CoveredGrowthCleanupFeature}: a late pass over one chunk
 * that repairs what other features left behind. Per column, from 56 blocks below
 * the surface up to it, it
 * <ul>
 *     <li>removes lake plants (oasis flowers, shore plants) that float above Ichor
 *     with only air between them and the liquid within six blocks;</li>
 *     <li>turns Siftslate Growth / Dry Healthy Sculk Growth that is covered by a
 *     full block back into Siftslate / Dry Healthy Sculk;</li>
 *     <li>turns Siftslate Growth on top of Dry Healthy Sculk into Dry Healthy Sculk
 *     Growth;</li>
 *     <li>syncs the snowy flag of Siftslate Growth with an Ichor Snow cover.</li>
 * </ul>
 *
 * <p>Differences: Sift plants are vanilla stand-ins, so lake plants are matched by
 * vanilla block type (shared with ordinary vanilla plants of the same type); Ichor
 * is water, so "unsupported Ichor below" means water (not lava or waterlogged
 * blocks); "covered by a full block" uses the region's solid-and-occluding test, so
 * non-occluding full blocks such as Ichor Glass do not count.</p>
 */
final class CoveredGrowthCleanupFeature implements SiftFeature {

    private static final int SCAN_DEPTH = 56;
    private static final int ICHOR_SEARCH_DEPTH = 6;
    private static final List<String> LAKE_PLANT_PATHS = List.of("overgrown_chard", "overgrown_stalks", "overgrown_fronds",
        "overgrown_lotus", "sunburst_plant", "whisperbloom", "siftslate_stalks", "healthy_sculk_sprouts",
        "dry_healthy_sculk_sprouts");
    private static final Set<String> LAKE_PLANT_TYPES = lakePlantTypes();
    /** Backing block types of the growth states, to skip the full state read for most blocks. */
    private static final Set<String> GROWTH_TYPES = Set.copyOf(List.of(SiftTerrain.SIFTSLATE_GROWTH.block().asString(),
        SiftTerrain.SIFTSLATE_GROWTH_SNOWY.block().asString(), SiftTerrain.DRY_HEALTHY_SCULK_GROWTH.block().asString()));

    private static Set<String> lakePlantTypes() {
        Set<String> types = new HashSet<>(Set.of("minecraft:wildflowers", "minecraft:torchflower",
            "minecraft:torchflower_crop", "minecraft:pitcher_crop", "minecraft:pitcher_plant"));
        for (String path : LAKE_PLANT_PATHS) {
            types.add(SiftBlocks.remapType(path).orElseThrow(() -> new IllegalStateException("Unknown Sift plant " + path)));
        }
        return Set.copyOf(types);
    }

    @Override
    public boolean place(WorldRegion region, Random random, int x, int y, int z) {
        int minX = x & ~15;
        int minZ = z & ~15;
        // The mod stops one block below the inclusive build limit; maxY() here is exclusive.
        int maxY = region.maxY() - 2;
        boolean changed = false;
        for (int cx = minX; cx < minX + 16; cx++) {
            for (int cz = minZ; cz < minZ + 16; cz++) {
                int surface = region.height(WorldRegion.Heightmap.WORLD_SURFACE_WG, cx, cz);
                int minY = Math.max(region.minY(), surface - SCAN_DEPTH);
                int columnMaxY = Math.min(maxY, surface);
                for (int cy = minY; cy <= columnMaxY; cy++) {
                    changed |= cleanBlock(region, cx, cy, cz);
                }
            }
        }
        return changed;
    }

    private static boolean cleanBlock(WorldRegion region, int x, int y, int z) {
        String type = region.blockId(x, y, z);
        if (LAKE_PLANT_TYPES.contains(type)) {
            return hasUnsupportedIchorBelow(region, x, y, z) && region.set(x, y, z, SiftTerrain.AIR);
        }
        if (!GROWTH_TYPES.contains(type)) {
            return false;
        }
        BlockStateSpec state = region.state(x, y, z);
        boolean growth = SiftTerrain.isGrowth(state);
        if (!growth && !state.equals(SiftTerrain.DRY_HEALTHY_SCULK_GROWTH)) {
            return false;
        }
        BlockStateSpec covering = region.state(x, y + 1, z);
        boolean dry = state.equals(SiftTerrain.DRY_HEALTHY_SCULK_GROWTH)
            || region.state(x, y - 1, z).equals(SiftTerrain.DRY_HEALTHY_SCULK);
        boolean ichorSnowCover = isIchorSnowCover(covering);
        if (region.isSolid(x, y + 1, z) && !ichorSnowCover) {
            return region.set(x, y, z, dry ? SiftTerrain.DRY_HEALTHY_SCULK : SiftTerrain.SIFTSLATE);
        }
        if (dry && growth) {
            return region.set(x, y, z, SiftTerrain.DRY_HEALTHY_SCULK_GROWTH);
        }
        if (!growth || state.equals(SiftTerrain.SIFTSLATE_GROWTH_SNOWY) == ichorSnowCover) {
            return false;
        }
        return region.set(x, y, z, ichorSnowCover ? SiftTerrain.SIFTSLATE_GROWTH_SNOWY : SiftTerrain.SIFTSLATE_GROWTH);
    }

    /** {@code SiftslateGrowthBlock.isIchorSnowCover}: Ichor Snow (snow layer) or Ichor Snow Block. */
    private static boolean isIchorSnowCover(BlockStateSpec state) {
        return state.block().asString().equals("minecraft:snow") || state.equals(SiftTerrain.ICHOR_SNOW_BLOCK);
    }

    /** The first fluid within six blocks below, skipping air, is Ichor (water); any other block ends the search. */
    private static boolean hasUnsupportedIchorBelow(WorldRegion region, int x, int y, int z) {
        for (int offset = 1; offset <= ICHOR_SEARCH_DEPTH; offset++) {
            int belowY = y - offset;
            if (SiftTerrain.isFluid(region, x, belowY, z)) {
                return region.blockId(x, belowY, z).equals("minecraft:water");
            }
            if (!region.isAir(x, belowY, z)) {
                return false;
            }
        }
        return false;
    }
}
