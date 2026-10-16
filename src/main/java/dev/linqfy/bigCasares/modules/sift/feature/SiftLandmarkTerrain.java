package dev.linqfy.bigCasares.modules.sift.feature;

import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;

import java.util.List;
import java.util.Set;

/**
 * Port of the mod's {@code SiftLandmarkTerrain}: which blocks count as ground for
 * landmark structures (abandoned portals), which blocks a landmark may overwrite, and
 * how to find the ground level under plants and snow.
 *
 * <p>Differences: the mod tests block types ({@code state.is(ModBlocks.X)}), the port
 * compares backing states, so every custom-block variant is listed explicitly (e.g. the
 * snowy growth). The mod's {@code state.canBeReplaced()} for vanilla blocks becomes a
 * list of vanilla replaceable plants (grass, ferns, roots, vines, snow layers, light
 * blocks), which are also the stand-ins of the mod's replaceable Sift plants; the mod's
 * explicitly listed plants map to those stand-ins (see {@code SiftBlocks}).</p>
 */
final class SiftLandmarkTerrain {

    private static final Set<BlockStateSpec> GROUND = Set.of(
        SiftTerrain.SIFTSLATE,
        SiftTerrain.COBBLED_SIFTSLATE,
        SiftTerrain.SIFT_RUBBLE,
        SiftTerrain.SIFTSLATE_GROWTH,
        SiftTerrain.SIFTSLATE_GROWTH_SNOWY,
        SiftTerrain.HEALTHY_SCULK,
        SiftTerrain.DRY_HEALTHY_SCULK,
        SiftTerrain.DRY_HEALTHY_SCULK_GROWTH,
        SiftTerrain.block("siftslate_coal_ore"),
        SiftTerrain.block("siftslate_diamond_ore"),
        SiftTerrain.block("siftslate_emerald_ore"),
        SiftTerrain.block("siftslate_charoite_ore"),
        SiftTerrain.block("siftslate_siftite_ore"),
        SiftTerrain.ICHOR_SNOW_BLOCK);

    /** Vanilla ground blocks the mod lists besides its own ({@code Blocks.SCULK}, {@code Blocks.BONE_BLOCK}). */
    private static final Set<String> VANILLA_GROUND = Set.of("minecraft:sculk", "minecraft:bone_block");

    /** Vanilla blocks with the {@code replaceable} property that can occur in the Sift (fluids are excluded separately). */
    private static final Set<String> VANILLA_REPLACEABLE = Set.of(
        "minecraft:short_grass", "minecraft:tall_grass", "minecraft:fern", "minecraft:large_fern", "minecraft:dead_bush",
        "minecraft:vine", "minecraft:snow", "minecraft:glow_lichen", "minecraft:hanging_roots", "minecraft:crimson_roots",
        "minecraft:warped_roots", "minecraft:nether_sprouts", "minecraft:short_dry_grass", "minecraft:tall_dry_grass",
        "minecraft:bush", "minecraft:light", "minecraft:fire", "minecraft:soul_fire");

    /** Sculk blocks the mod lists explicitly, whatever their fluid state. */
    private static final Set<String> SCULK_FEATURES = Set.of("minecraft:sculk_vein", "minecraft:sculk_sensor",
        "minecraft:sculk_shrieker", "minecraft:sculk_catalyst");

    /** Stand-ins of the mod's explicitly listed Sift plants ({@code OVERGROWN_FRONDS} ... {@code OVERGROWN_HANGING_ROOTS}). */
    private static final Set<String> PLANT_STAND_INS = Set.copyOf(List.of(
        standIn("overgrown_fronds"), standIn("overgrown_chard"), standIn("overgrown_stalks"), standIn("siftslate_stalks"),
        standIn("healthy_sculk_sprouts"), standIn("dry_healthy_sculk_sprouts"), standIn("siftslate_hanging_roots"),
        standIn("overgrown_hanging_roots")));

    private SiftLandmarkTerrain() {
    }

    private static String standIn(String path) {
        return SiftTerrain.block(path).block().asString();
    }

    /** {@code isGround}: Sift stone, soils and ores, ichor snow blocks, sculk and bone blocks. */
    static boolean isGround(BlockStateSpec state) {
        return GROUND.contains(state) || VANILLA_GROUND.contains(state.block().asString());
    }

    /** {@code canReplace}: air, ground, replaceable non-fluid blocks, ichor snow, sculk features and Sift plants. */
    static boolean canReplace(WorldRegion region, int x, int y, int z) {
        // The block id is much cheaper than the full state, and air is the common case.
        if (region.isAir(x, y, z)) {
            return true;
        }
        String id = region.blockId(x, y, z);
        return isGround(region.state(x, y, z))
            || VANILLA_REPLACEABLE.contains(id) && !SiftTerrain.isFluid(region, x, y, z)
            || SCULK_FEATURES.contains(id)
            || PLANT_STAND_INS.contains(id);
    }

    /**
     * {@code surfaceBlockY}: Y of the topmost ground block below the surface heightmap, looking
     * through replaceable blocks (plants, snow) for at most 32 blocks. {@link Integer#MIN_VALUE}
     * when something else (rock, water, a tree) covers the ground, or the column is outside the region.
     */
    static int surfaceBlockY(WorldRegion region, int x, int z) {
        int top = region.height(WorldRegion.Heightmap.WORLD_SURFACE_WG, x, z) - 1;
        int minimum = Math.max(region.minY() + 1, top - 32);
        for (int y = top; y >= minimum; y--) {
            if (isGround(region.state(x, y, z))) {
                return y;
            }
            if (canReplace(region, x, y, z)) {
                continue;
            }
            return Integer.MIN_VALUE;
        }
        return Integer.MIN_VALUE;
    }
}
