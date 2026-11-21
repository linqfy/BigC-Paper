package dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.thirdparty;

import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicDungeonTheme;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.thirdparty.YungsBetterDungeonsSource.Kind;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

/**
 * Deterministic re-implementation of the YUNG's Better Dungeons structure processors listed in the
 * mod's {@code worldgen/processor_list} files (small_dungeon_*, small_nether_dungeon_*,
 * zombie_dungeon), run once at pack-build time. Processors that inspect the live world (ceilings,
 * "legs" down to the ground, rot where the template meets caves) assume the room is buried in solid
 * ground, which is where the module places side rooms and corridors.
 */
final class YungsPlaceholders {

    /** Marker blocks of the mod that must never reach the generated pack. */
    static final Set<String> PLACEHOLDERS = Set.of(
        "minecraft:orange_stained_glass", "minecraft:yellow_stained_glass", "minecraft:magenta_stained_glass",
        "minecraft:brown_stained_glass", "minecraft:cyan_stained_glass", "minecraft:gray_stained_glass",
        "minecraft:black_stained_glass", "minecraft:orange_wool", "minecraft:orange_terracotta",
        "minecraft:purple_terracotta", "minecraft:cyan_terracotta", "minecraft:purpur_slab", "minecraft:warped_stairs",
        "minecraft:brick_stairs", "minecraft:light_blue_concrete", "minecraft:lime_concrete", "minecraft:sea_pickle",
        "minecraft:chain");

    private static final Set<String> KEPT_BLOCK_ENTITIES = Set.of("minecraft:chest", "minecraft:mob_spawner", "minecraft:jigsaw");
    private static final List<String> CANDLES = List.of("minecraft:candle", "minecraft:white_candle",
        "minecraft:gray_candle", "minecraft:light_gray_candle", "minecraft:brown_candle", "minecraft:black_candle");
    /** Chests kept unconditionally per small dungeon, and the most it may hold (mod defaults). */
    private static final int SMALL_CHEST_MIN = 1;
    private static final int SMALL_CHEST_MAX = 3;
    private static final int BANNER_MAX = 1;

    private YungsPlaceholders() {
    }

    /** One {@code minecraft:rule} entry: {@code input} becomes {@code output} with {@code probability}. */
    private record Rule(String input, String output, double probability) {
    }

    private static final List<Rule> SMALL_RULES = List.of(
        new Rule("minecraft:mossy_cobblestone", "minecraft:cobblestone", 0.25),
        new Rule("minecraft:stone_bricks", "minecraft:mossy_stone_bricks", 0.5),
        new Rule("minecraft:stone_bricks", "minecraft:cracked_stone_bricks", 0.2));
    private static final List<Rule> NETHER_RULES = List.of(
        new Rule("minecraft:nether_gold_ore", "minecraft:nether_quartz_ore", 0.5),
        new Rule("minecraft:purple_terracotta", "minecraft:crying_obsidian", 0.25),
        new Rule("minecraft:purple_terracotta", "minecraft:obsidian", 1.0));
    private static final List<Rule> ZOMBIE_RULES = List.of(
        new Rule("minecraft:stone_bricks", "minecraft:mossy_stone_bricks", 0.2),
        new Rule("minecraft:stone_bricks", "minecraft:cracked_stone_bricks", 0.3),
        new Rule("minecraft:mossy_cobblestone", "minecraft:cobblestone", 0.3),
        new Rule("minecraft:mossy_cobblestone", "minecraft:coarse_dirt", 0.5),
        new Rule("minecraft:cobblestone", "minecraft:mossy_cobblestone", 0.2));

    private static final class Counters {
        int chests;
        int banners;
    }

    static void resolve(TemplateGrid grid, Kind kind, Random random, MythicDungeonTheme theme) {
        Counters counters = new Counters();
        grid.forEach(cell -> {
            int x = cell.x();
            int y = cell.y();
            int z = cell.z();
            String name = cell.name();
            BlockStateSpec state = cell.state();
            BlockStateSpec result = switch (kind) {
                case SMALL -> small(name, state, random, counters);
                case NETHER -> nether(name, state, random, counters);
                case ZOMBIE -> zombie(name, state, random);
            };
            if (result == null) {
                grid.clear(x, y, z);
                return;
            }
            result = common(result, random);
            result = applyRules(result, kind == Kind.SMALL ? SMALL_RULES : kind == Kind.NETHER ? NETHER_RULES : ZOMBIE_RULES, random);
            String resolved = result.block().asString();
            if (resolved.equals("minecraft:spawner")) {
                grid.set(x, y, z, result, ThirdPartyBlocks.spawner(theme.spawnerMob()));
            } else if (resolved.equals("minecraft:chest")) {
                String loot = cell.nbt() != null && String.valueOf(cell.nbt().getString("LootTable")).contains("special")
                    ? theme.treasureLoot() : theme.commonLoot();
                grid.set(x, y, z, result, ThirdPartyBlocks.chest(loot));
            } else if (result.equals(state) && cell.nbt() != null && KEPT_BLOCK_ENTITIES.contains(cell.nbt().getString("id"))) {
                grid.set(x, y, z, result, cell.nbt());
            } else {
                // Banners, skulls and other block entities keep only their block (no mod NBT).
                grid.set(x, y, z, result);
            }
        });
    }

    private static BlockStateSpec small(String name, BlockStateSpec state, Random random, Counters counters) {
        return switch (name) {
            // Ceiling layer: the mod keeps the surrounding ground; buried rooms get a solid ceiling.
            case "minecraft:orange_stained_glass" -> BlockStateSpec.of("minecraft:smooth_stone");
            // Corner legs.
            case "minecraft:yellow_stained_glass" -> BlockStateSpec.of("minecraft:mossy_cobblestone");
            case "minecraft:magenta_stained_glass" -> chainOrAir(random, 0.2);
            case "minecraft:brown_stained_glass" -> chainOrAir(random, 0.5);
            case "minecraft:cyan_stained_glass" -> chainOrAir(random, 0.625);
            case "minecraft:red_wall_banner" -> banner(state, random, counters);
            case "minecraft:chest" -> {
                if (counters.chests < SMALL_CHEST_MIN || (counters.chests < SMALL_CHEST_MAX && random.nextFloat() <= 0.2f)) {
                    counters.chests++;
                    yield state;
                }
                yield TemplateGrid.AIR;
            }
            default -> state;
        };
    }

    private static BlockStateSpec nether(String name, BlockStateSpec state, Random random, Counters counters) {
        return switch (name) {
            case "minecraft:gray_stained_glass" -> chainOrAir(random, 0.5);
            case "minecraft:orange_terracotta", "minecraft:brick_stairs", "minecraft:light_blue_concrete",
                 "minecraft:lime_concrete" -> BlockStateSpec.of("minecraft:nether_bricks");
            case "minecraft:orange_wool" -> BlockStateSpec.of("minecraft:lava");
            case "minecraft:gray_wall_banner" -> banner(state, random, counters);
            case "minecraft:skeleton_skull", "minecraft:wither_skeleton_skull", "minecraft:skeleton_wall_skull",
                 "minecraft:wither_skeleton_wall_skull" -> random.nextFloat() > 0.167f ? TemplateGrid.AIR : state;
            default -> state;
        };
    }

    private static BlockStateSpec zombie(String name, BlockStateSpec state, Random random) {
        return switch (name) {
            // Leg tops become smooth stone; purpur slabs only fill air under the room, so they keep the ground.
            case "minecraft:magenta_stained_glass" -> BlockStateSpec.of("minecraft:smooth_stone");
            case "minecraft:purpur_slab" -> null;
            // Roof layer; buried in the ground it is the ceiling.
            case "minecraft:cyan_terracotta" -> BlockStateSpec.of("minecraft:smooth_stone");
            // Marker of the staircase the mod digs to the surface.
            case "minecraft:warped_stairs" -> TemplateGrid.AIR;
            case "minecraft:cobblestone_stairs" -> {
                float roll = random.nextFloat();
                if (roll < 0.4f) {
                    yield copyProperties("minecraft:mossy_cobblestone_stairs", state);
                } else if (roll < 0.5f) {
                    yield slab("minecraft:cobblestone_slab", state);
                } else if (roll < 0.6f) {
                    yield slab("minecraft:mossy_cobblestone_slab", state);
                }
                yield state;
            }
            case "minecraft:potted_cornflower" -> {
                float roll = random.nextFloat();
                String pot = roll < 0.1f ? "minecraft:potted_cornflower" : roll < 0.2f ? "minecraft:potted_brown_mushroom"
                    : roll < 0.3f ? "minecraft:potted_red_mushroom" : roll < 0.7f ? "minecraft:potted_dead_bush"
                    : roll < 0.8f ? "minecraft:potted_poppy" : "minecraft:flower_pot";
                yield BlockStateSpec.of(pot);
            }
            case "minecraft:black_stained_glass" -> BlockStateSpec.of("minecraft:spawner");
            default -> state;
        };
    }

    /** Processors shared by every dungeon type: candles, renamed ids. */
    private static BlockStateSpec common(BlockStateSpec state, Random random) {
        String name = state.block().asString();
        if (name.equals("minecraft:sea_pickle")) {
            String candle = CANDLES.get(random.nextInt(CANDLES.size()));
            return BlockStateSpec.of(candle, Map.of("candles", String.valueOf(random.nextInt(4) + 1),
                "lit", String.valueOf(random.nextFloat() < 0.1f), "waterlogged", "false"));
        }
        if (name.equals("minecraft:chain")) {
            return copyProperties("minecraft:iron_chain", state);
        }
        if (name.equals("minecraft:cave_air")) {
            return TemplateGrid.AIR;
        }
        return state;
    }

    private static BlockStateSpec applyRules(BlockStateSpec state, List<Rule> rules, Random random) {
        String name = state.block().asString();
        for (Rule rule : rules) {
            if (rule.input().equals(name) && (rule.probability() >= 1.0 || random.nextFloat() < rule.probability())) {
                return BlockStateSpec.of(rule.output());
            }
        }
        return state;
    }

    private static BlockStateSpec chainOrAir(Random random, double chance) {
        return random.nextFloat() < chance ? BlockStateSpec.of("minecraft:iron_chain", Map.of("axis", "y")) : TemplateGrid.AIR;
    }

    private static BlockStateSpec banner(BlockStateSpec state, Random random, Counters counters) {
        if (counters.banners >= BANNER_MAX || random.nextFloat() > 0.1f) {
            return TemplateGrid.AIR;
        }
        counters.banners++;
        return state;
    }

    private static BlockStateSpec copyProperties(String id, BlockStateSpec state) {
        return new BlockStateSpec(NamespacedId.parse(id), new TreeMap<>(state.properties()));
    }

    private static BlockStateSpec slab(String id, BlockStateSpec stairs) {
        String half = stairs.properties().getOrDefault("half", "bottom");
        return BlockStateSpec.of(id, Map.of("type", half.equals("top") ? "top" : "bottom", "waterlogged", "false"));
    }
}
