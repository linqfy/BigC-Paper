package dev.linqfy.bigCasares.modules.mythicdungeons.worldgen;

import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Everything that makes one culture's dungeon look and spread differently: layout family, block
 * palette, architectural style, biomes, structure spacing, spawner mob and loot. The piece
 * geometry is shared by all cultures of a family ({@link MythicSurfacePieces},
 * {@link MythicUndergroundPieces}); the style adds the culture's set-pieces. No biome belongs to two
 * cultures (the tests check it): each culture has its own structure set, so a shared biome would let two
 * complexes overlap.
 */
public record MythicDungeonTheme(
    String culture,
    Layout layout,
    Palette palette,
    EntranceStyle entrance,
    List<String> biomes,
    Spread spread,
    String spawnerMob,
    String commonLoot,
    String treasureLoot
) {

    public static final String NAMESPACE = "bigc_myth";

    public MythicDungeonTheme {
        Objects.requireNonNull(culture, "culture");
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(palette, "palette");
        Objects.requireNonNull(entrance, "entrance");
        biomes = List.copyOf(biomes);
        if (biomes.isEmpty()) {
            throw new IllegalArgumentException("Culture " + culture + " needs biomes");
        }
        Objects.requireNonNull(spread, "spread");
    }

    /**
     * Layout family. {@link #SURFACE}: a building complex standing on the ground around a central
     * courtyard (the start), boss hall included; the pieces are bearded into the terrain, which limits
     * {@code max_distance_from_center} to 116 (vanilla adds 12 for any adaptation and refuses more than
     * 128). {@link #UNDERGROUND}: a themed surface entrance and everything else below it, no terrain
     * adaptation, so the full 128 is available.
     */
    public enum Layout {
        SURFACE("beard_box", 116), UNDERGROUND("none", 128);

        private final String terrainAdaptation;
        private final int maxDistance;

        Layout(String terrainAdaptation, int maxDistance) {
            this.terrainAdaptation = terrainAdaptation;
            this.maxDistance = maxDistance;
        }

        public String terrainAdaptation() {
            return terrainAdaptation;
        }

        public int maxDistanceFromCenter() {
            return maxDistance;
        }
    }

    /**
     * Architectural style of the culture: the surface entrance of underground dungeons (each a 15x15
     * footprint with the descent hole near the centre) and the set-pieces of both families.
     */
    public enum EntranceStyle {
        TEMPLE, LONGHOUSE, RUINED_KEEP, PYRAMID, ZIGGURAT, TORII_SHRINE, STEPPED_PYRAMID, DOMED_PAVILION, CAVE_MOUTH
    }

    /**
     * Random-spread placement in chunks; salts must be unique per culture. Two starts of one set are at
     * least {@code separation + 1} chunks apart, so {@code separation * 16 - 48} must exceed twice the
     * layout's max distance (the tests check it): two dungeons of a culture never overlap.
     */
    public record Spread(int spacing, int separation, int salt) {
    }

    /**
     * Vanilla blocks of a theme. {@code stairs}, {@code slab} and {@code roofStairs} are block ids
     * whose shape properties are added by the builders.
     */
    public record Palette(
        BlockStateSpec wall,
        BlockStateSpec wallAlt,
        BlockStateSpec floor,
        BlockStateSpec floorAlt,
        BlockStateSpec ceiling,
        BlockStateSpec pillar,
        String stairs,
        String slab,
        BlockStateSpec light,
        BlockStateSpec accent,
        BlockStateSpec accent2,
        BlockStateSpec clutter,
        BlockStateSpec hangingLight,
        BlockStateSpec roof,
        String roofStairs
    ) {
        public BlockStateSpec stairs(String facing, boolean top) {
            return stairsOf(stairs, facing, top);
        }

        public BlockStateSpec roofStairs(String facing, boolean top) {
            return stairsOf(roofStairs, facing, top);
        }

        public BlockStateSpec slab(String type) {
            return BlockStateSpec.of(slab, Map.of("type", type));
        }

        private static BlockStateSpec stairsOf(String id, String facing, boolean top) {
            return BlockStateSpec.of(id, Map.of("facing", facing, "half", top ? "top" : "bottom", "shape", "straight"));
        }
    }

    public NamespacedId structureId() {
        return NamespacedId.of(NAMESPACE, culture);
    }

    public NamespacedId id(String path) {
        return NamespacedId.of(NAMESPACE, culture + "/" + path);
    }

    private static BlockStateSpec b(String id) {
        return BlockStateSpec.of(id);
    }

    private static BlockStateSpec axisY(String id) {
        return BlockStateSpec.of(id, Map.of("axis", "y"));
    }

    private static BlockStateSpec hanging(String id) {
        return BlockStateSpec.of(id, Map.of("hanging", "true"));
    }

    public static final MythicDungeonTheme GREEK = new MythicDungeonTheme("greek", Layout.SURFACE,
        new Palette(b("minecraft:smooth_quartz"), b("minecraft:calcite"), b("minecraft:polished_diorite"),
            b("minecraft:calcite"), b("minecraft:smooth_quartz"), axisY("minecraft:quartz_pillar"),
            "minecraft:quartz_stairs", "minecraft:smooth_quartz_slab", b("minecraft:sea_lantern"),
            b("minecraft:gold_block"), b("minecraft:chiseled_quartz_block"),
            BlockStateSpec.of("minecraft:lightning_rod", Map.of("facing", "up")), hanging("minecraft:lantern"),
            b("minecraft:smooth_quartz"), "minecraft:quartz_stairs"),
        EntranceStyle.TEMPLE,
        List.of("minecraft:meadow", "minecraft:plains", "minecraft:stony_shore"),
        new Spread(64, 22, 734_512_001), "minecraft:skeleton",
        "minecraft:chests/simple_dungeon", "minecraft:chests/stronghold_library");

    public static final MythicDungeonTheme NORSE = new MythicDungeonTheme("norse", Layout.UNDERGROUND,
        new Palette(b("minecraft:stone_bricks"), b("minecraft:mossy_stone_bricks"), b("minecraft:spruce_planks"),
            b("minecraft:stone_bricks"), b("minecraft:spruce_planks"), axisY("minecraft:stripped_spruce_log"),
            "minecraft:stone_brick_stairs", "minecraft:spruce_slab", b("minecraft:shroomlight"),
            b("minecraft:packed_ice"), b("minecraft:chiseled_stone_bricks"),
            BlockStateSpec.of("minecraft:barrel", Map.of("facing", "up")), hanging("minecraft:lantern"),
            b("minecraft:spruce_planks"), "minecraft:spruce_stairs"),
        EntranceStyle.LONGHOUSE,
        List.of("minecraft:snowy_plains", "minecraft:snowy_taiga", "minecraft:taiga", "minecraft:grove",
            "minecraft:frozen_peaks", "minecraft:snowy_slopes"),
        new Spread(62, 24, 734_512_002), "minecraft:stray",
        "minecraft:chests/simple_dungeon", "minecraft:chests/stronghold_corridor");

    public static final MythicDungeonTheme MEDIEVAL = new MythicDungeonTheme("medieval", Layout.SURFACE,
        new Palette(b("minecraft:stone_bricks"), b("minecraft:cracked_stone_bricks"), b("minecraft:cobblestone"),
            b("minecraft:mossy_cobblestone"), b("minecraft:dark_oak_planks"), axisY("minecraft:dark_oak_log"),
            "minecraft:stone_brick_stairs", "minecraft:stone_brick_slab", b("minecraft:glowstone"),
            b("minecraft:red_nether_bricks"), b("minecraft:chiseled_stone_bricks"), b("minecraft:cobweb"),
            hanging("minecraft:soul_lantern"), b("minecraft:cobblestone"), "minecraft:cobblestone_stairs"),
        EntranceStyle.RUINED_KEEP,
        List.of("minecraft:forest", "minecraft:dark_forest", "minecraft:birch_forest", "minecraft:old_growth_birch_forest"),
        new Spread(60, 22, 734_512_003), "minecraft:zombie",
        "minecraft:chests/stronghold_crossing", "minecraft:chests/stronghold_corridor");

    public static final MythicDungeonTheme EGYPTIAN = new MythicDungeonTheme("egyptian", Layout.SURFACE,
        new Palette(b("minecraft:cut_sandstone"), b("minecraft:sandstone"), b("minecraft:smooth_sandstone"),
            b("minecraft:orange_terracotta"), b("minecraft:blue_terracotta"), b("minecraft:chiseled_sandstone"),
            "minecraft:sandstone_stairs", "minecraft:cut_sandstone_slab", axisY("minecraft:ochre_froglight"),
            b("minecraft:gold_block"), b("minecraft:orange_terracotta"), b("minecraft:potted_dead_bush"),
            hanging("minecraft:lantern"), b("minecraft:cut_sandstone"), "minecraft:sandstone_stairs"),
        EntranceStyle.PYRAMID,
        List.of("minecraft:desert"),
        new Spread(58, 20, 734_512_004), "minecraft:husk",
        "minecraft:chests/desert_pyramid", "minecraft:chests/desert_pyramid");

    public static final MythicDungeonTheme MESOPOTAMIAN = new MythicDungeonTheme("mesopotamian", Layout.UNDERGROUND,
        new Palette(b("minecraft:mud_bricks"), b("minecraft:packed_mud"), b("minecraft:packed_mud"),
            b("minecraft:terracotta"), b("minecraft:packed_mud"), b("minecraft:yellow_terracotta"),
            "minecraft:mud_brick_stairs", "minecraft:mud_brick_slab", b("minecraft:shroomlight"),
            b("minecraft:lapis_block"), b("minecraft:orange_terracotta"), b("minecraft:flower_pot"),
            hanging("minecraft:lantern"), b("minecraft:mud_bricks"), "minecraft:mud_brick_stairs"),
        EntranceStyle.ZIGGURAT,
        List.of("minecraft:savanna", "minecraft:savanna_plateau", "minecraft:swamp"),
        new Spread(64, 24, 734_512_005), "minecraft:zombie",
        "minecraft:chests/simple_dungeon", "minecraft:chests/stronghold_corridor");

    public static final MythicDungeonTheme JAPANESE = new MythicDungeonTheme("japanese", Layout.UNDERGROUND,
        new Palette(b("minecraft:deepslate_tiles"), b("minecraft:cracked_deepslate_tiles"), b("minecraft:cherry_planks"),
            b("minecraft:dark_oak_planks"), b("minecraft:dark_oak_planks"), axisY("minecraft:dark_oak_log"),
            "minecraft:deepslate_tile_stairs", "minecraft:deepslate_tile_slab", axisY("minecraft:pearlescent_froglight"),
            b("minecraft:red_terracotta"), axisY("minecraft:stripped_cherry_log"), b("minecraft:potted_cherry_sapling"),
            hanging("minecraft:lantern"), b("minecraft:deepslate_tiles"), "minecraft:deepslate_tile_stairs"),
        EntranceStyle.TORII_SHRINE,
        List.of("minecraft:cherry_grove", "minecraft:bamboo_jungle", "minecraft:old_growth_pine_taiga",
            "minecraft:old_growth_spruce_taiga"),
        new Spread(62, 24, 734_512_006), "minecraft:skeleton",
        "minecraft:chests/simple_dungeon", "minecraft:chests/stronghold_library");

    public static final MythicDungeonTheme MESOAMERICAN = new MythicDungeonTheme("mesoamerican", Layout.SURFACE,
        new Palette(b("minecraft:mossy_stone_bricks"), b("minecraft:mossy_cobblestone"), b("minecraft:stone_bricks"),
            b("minecraft:cobblestone"), b("minecraft:mossy_stone_bricks"), b("minecraft:chiseled_stone_bricks"),
            "minecraft:mossy_stone_brick_stairs", "minecraft:mossy_stone_brick_slab", axisY("minecraft:verdant_froglight"),
            b("minecraft:gold_block"), b("minecraft:green_terracotta"),
            BlockStateSpec.of("minecraft:skeleton_skull", Map.of("rotation", "0")), hanging("minecraft:soul_lantern"),
            b("minecraft:mossy_stone_bricks"), "minecraft:mossy_stone_brick_stairs"),
        EntranceStyle.STEPPED_PYRAMID,
        List.of("minecraft:jungle", "minecraft:sparse_jungle"),
        new Spread(58, 20, 734_512_007), "minecraft:spider",
        "minecraft:chests/jungle_temple", "minecraft:chests/jungle_temple");

    public static final MythicDungeonTheme ARABIAN = new MythicDungeonTheme("arabian", Layout.SURFACE,
        new Palette(b("minecraft:smooth_sandstone"), b("minecraft:cut_sandstone"), b("minecraft:smooth_red_sandstone"),
            b("minecraft:white_terracotta"), b("minecraft:smooth_sandstone"), b("minecraft:cut_red_sandstone"),
            "minecraft:smooth_sandstone_stairs", "minecraft:smooth_sandstone_slab", b("minecraft:glowstone"),
            b("minecraft:waxed_oxidized_cut_copper"), b("minecraft:cyan_terracotta"), b("minecraft:flower_pot"),
            hanging("minecraft:lantern"), b("minecraft:waxed_oxidized_cut_copper"), "minecraft:waxed_oxidized_cut_copper_stairs"),
        EntranceStyle.DOMED_PAVILION,
        List.of("minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands"),
        new Spread(60, 22, 734_512_008), "minecraft:husk",
        "minecraft:chests/desert_pyramid", "minecraft:chests/stronghold_corridor");

    public static final MythicDungeonTheme ANDEAN = new MythicDungeonTheme("andean", Layout.UNDERGROUND,
        new Palette(b("minecraft:polished_andesite"), b("minecraft:andesite"), b("minecraft:coarse_dirt"),
            b("minecraft:moss_block"), b("minecraft:stone"), b("minecraft:chiseled_tuff"),
            "minecraft:andesite_stairs", "minecraft:andesite_slab", b("minecraft:glowstone"),
            b("minecraft:amethyst_block"), b("minecraft:mossy_cobblestone"),
            BlockStateSpec.of("minecraft:amethyst_cluster", Map.of("facing", "up")), hanging("minecraft:soul_lantern"),
            b("minecraft:stone"), "minecraft:stone_stairs"),
        EntranceStyle.CAVE_MOUTH,
        List.of("minecraft:windswept_hills", "minecraft:windswept_gravelly_hills", "minecraft:stony_peaks",
            "minecraft:jagged_peaks", "minecraft:windswept_savanna"),
        new Spread(64, 26, 734_512_009), "minecraft:zombie",
        "minecraft:chests/abandoned_mineshaft", "minecraft:chests/stronghold_corridor");

    /** The nine cultures in a fixed order (pack output order does not depend on it). */
    public static List<MythicDungeonTheme> all() {
        return List.of(GREEK, NORSE, MEDIEVAL, EGYPTIAN, MESOPOTAMIAN, JAPANESE, MESOAMERICAN, ARABIAN, ANDEAN);
    }
}
