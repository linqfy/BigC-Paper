package dev.linqfy.bigCasares.modules.sift.feature;

import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;

import java.util.HashSet;
import java.util.Set;

/**
 * The ground-shaping helpers of the mod's {@code SiftLandmarkTerrain} that the soul
 * canyon needs: what counts as landmark ground, what a landmark may overwrite, and
 * where the ground surface of a column is.
 *
 * <p>Differences: Sift plants are single-block vanilla stand-ins, so they are
 * matched by vanilla block type (see {@link SiftTerrain#PLANT_TYPES}); the mod's
 * "any replaceable, fluid-free block" rule is a fixed list of the vanilla
 * replaceable blocks, because the region only exposes block ids.</p>
 */
final class SoulCanyonTerrain {

    /** Returned by {@link #surfaceBlockY} when the column has no usable ground. */
    static final int NO_SURFACE = Integer.MIN_VALUE;

    /** {@code SiftLandmarkTerrain.isGround}: Sift terrain, rubble, ores, ichor snow, sculk and bone. */
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
    /** Vanilla ground blocks that match in every state. */
    private static final Set<String> VANILLA_GROUND = Set.of("minecraft:sculk", "minecraft:bone_block");
    private static final Set<String> AIR_TYPES = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air");
    /**
     * Blocks the mod's {@code canReplace} accepts whatever their fluid: ichor snow, sculk
     * extras, the Sift plants and hanging roots (stand-ins by vanilla type).
     */
    private static final Set<String> ALWAYS_REPLACEABLE = replaceableTypes();
    /**
     * Vanilla blocks with the {@code replaceable} property (26.2 {@code #minecraft:replaceable}),
     * only accepted without a fluid (the mod's {@code getFluidState().isEmpty() && canBeReplaced()}).
     * Water, lava, bubble columns and seagrass always hold a fluid and are left out.
     */
    private static final Set<String> VANILLA_REPLACEABLE = Set.of("minecraft:short_grass", "minecraft:fern",
        "minecraft:dead_bush", "minecraft:bush", "minecraft:short_dry_grass", "minecraft:tall_dry_grass",
        "minecraft:fire", "minecraft:soul_fire", "minecraft:snow", "minecraft:vine", "minecraft:glow_lichen",
        "minecraft:resin_clump", "minecraft:light", "minecraft:tall_grass", "minecraft:large_fern",
        "minecraft:structure_void", "minecraft:warped_roots", "minecraft:nether_sprouts", "minecraft:crimson_roots",
        "minecraft:leaf_litter", "minecraft:hanging_roots");

    private SoulCanyonTerrain() {
    }

    private static Set<String> replaceableTypes() {
        Set<String> types = new HashSet<>(SiftTerrain.PLANT_TYPES);
        types.add("minecraft:snow");
        types.add("minecraft:sculk_vein");
        types.add("minecraft:sculk_sensor");
        types.add("minecraft:sculk_shrieker");
        types.add("minecraft:sculk_catalyst");
        types.add("minecraft:hanging_roots");
        types.add("minecraft:weeping_vines_plant");
        return Set.copyOf(types);
    }

    static boolean isGround(BlockStateSpec state) {
        return GROUND.contains(state) || VANILLA_GROUND.contains(state.block().asString());
    }

    /** {@code SiftLandmarkTerrain.canReplace} for the block at the position. */
    static boolean canReplace(WorldRegion region, int x, int y, int z) {
        return canReplace(region, x, y, z, region.state(x, y, z));
    }

    private static boolean canReplace(WorldRegion region, int x, int y, int z, BlockStateSpec state) {
        String id = state.block().asString();
        if (AIR_TYPES.contains(id) || isGround(state) || ALWAYS_REPLACEABLE.contains(id)) {
            return true;
        }
        return VANILLA_REPLACEABLE.contains(id) && !SiftTerrain.isFluid(region, x, y, z);
    }

    /**
     * {@code SiftLandmarkTerrain.surfaceBlockY}: Y of the highest ground block of the
     * column, looking down at most 32 blocks through replaceable blocks only.
     *
     * @return the ground block's Y, or {@link #NO_SURFACE}
     */
    static int surfaceBlockY(WorldRegion region, int x, int z) {
        int top = region.height(WorldRegion.Heightmap.WORLD_SURFACE_WG, x, z) - 1;
        int minimum = Math.max(region.minY() + 1, top - 32);
        for (int y = top; y >= minimum; y--) {
            BlockStateSpec state = region.state(x, y, z);
            if (isGround(state)) {
                return y;
            }
            if (!canReplace(region, x, y, z, state)) {
                return NO_SURFACE;
            }
        }
        return NO_SURFACE;
    }
}
