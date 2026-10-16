package dev.linqfy.bigCasares.modules.sift;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import dev.linqfy.bigCasares.blocks.CustomBlockDefinition;
import dev.linqfy.bigCasares.blocks.CustomBlockDefinition.Drop;
import dev.linqfy.bigCasares.blocks.CustomBlockDefinition.ToolTier;
import dev.linqfy.bigCasares.blocks.CustomBlockDefinition.ToolType;
import dev.linqfy.bigCasares.blocks.MushroomState;
import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.dimension.json.Json;
import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Sift's blocks as seen by unmodified 26.2 clients.
 *
 * <p>Full-cube blocks of Mielon's mod become custom blocks backed by reserved
 * mushroom states (rendered with the mod's own models). Everything else maps to a
 * vanilla stand-in chosen by colour and role: plants become tintable or
 * sturdy-face vanilla plants, Ichor becomes biome-tinted water, glass becomes
 * stained glass. <b>Backing states are persistent world data: never change an
 * assignment once released.</b> Grouping: brown caps hold the stone family, red
 * caps the soils, stems the willow wood, so vanilla block predicates that can
 * only match block types still select the right family.</p>
 */
public final class SiftBlocks {

    public static final String NS = "the_sift";

    private static final Map<String, CustomBlockDefinition> CUSTOM = new LinkedHashMap<>();
    private static final Map<String, BlockStateSpec> STAND_INS = new LinkedHashMap<>();

    static {
        // Stone family (brown mushroom caps). Hardness follows deepslate-like values.
        stone("siftslate", "brown:D", variants("the_sift:block/siftslate", "the_sift:block/siftslate_mirrored"),
            3.0F, List.of(new Drop("the_sift:cobbled_siftslate", 1, 1, false)), "block.deepslate");
        stone("cobbled_siftslate", "brown:ND", model("the_sift:block/cobbled_siftslate"), 3.5F, List.of(), "block.deepslate");
        stone("chiseled_siftslate", "brown:ED", model("the_sift:block/chiseled_siftslate"), 3.5F, List.of(), "block.deepslate_bricks");
        stone("siftslate_bricks", "brown:SD", model("the_sift:block/siftslate_bricks"), 3.5F, List.of(), "block.deepslate_bricks");
        stone("reinforced_siftslate", "brown:WD", model("the_sift:block/reinforced_siftslate"), 55.0F, List.of(), "block.deepslate");
        stone("sift_rubble", "brown:UD", model("the_sift:block/sift_rubble"), 1.5F, List.of(), "block.tuff");
        ore("siftslate_coal_ore", "brown:NED", "minecraft:coal", ToolTier.WOOD, 0, 2);
        ore("siftslate_diamond_ore", "brown:NSD", "minecraft:diamond", ToolTier.IRON, 3, 7);
        ore("siftslate_emerald_ore", "brown:NWD", "minecraft:emerald", ToolTier.IRON, 3, 7);
        ore("siftslate_charoite_ore", "brown:NUD", "the_sift:charoite", ToolTier.IRON, 2, 5);
        ore("siftslate_siftite_ore", "brown:ESD", "the_sift:siftite_nugget", ToolTier.DIAMOND, 4, 8);
        CUSTOM.put("sonorous_deepslate[mode=horn]", block("sonorous_deepslate", "brown:EWD",
            model("the_sift:block/sonorous_deepslate_horn"), 3.0F, ToolType.PICKAXE, ToolTier.WOOD, true,
            List.of(), "block.deepslate"));
        CUSTOM.put("sonorous_deepslate[mode=note]", block("sonorous_deepslate_note", "brown:EUD",
            model("the_sift:block/sonorous_deepslate_note"), 3.0F, ToolType.PICKAXE, ToolTier.WOOD, true,
            List.of(new Drop("the_sift:sonorous_deepslate", 1, 1, false)), "block.deepslate"));

        // Soils and surfaces (red mushroom caps).
        soil("siftslate_growth", "red:D",
            rotated("the_sift:block/siftslate_growth", "the_sift:block/siftslate_growth_mirrored"), 1.5F, ToolType.PICKAXE,
            List.of(new Drop("the_sift:cobbled_siftslate", 1, 1, false)));
        CUSTOM.put("siftslate_growth[snowy=true]", block("siftslate_growth_snowy", "red:ND",
            rotated("the_sift:block/siftslate_growth_ichor_snowy"), 1.5F, ToolType.PICKAXE, ToolTier.WOOD, true,
            List.of(new Drop("the_sift:cobbled_siftslate", 1, 1, false)), "block.nylium"));
        soil("healthy_sculk", "red:ED", variants("the_sift:block/healthy_sculk", "the_sift:block/healthy_sculk_rot90",
            "the_sift:block/healthy_sculk_rot180", "the_sift:block/healthy_sculk_rot270"), 0.6F, ToolType.HOE, List.of());
        soil("dry_healthy_sculk", "red:SD", variants("the_sift:block/dry_healthy_sculk",
            "the_sift:block/dry_healthy_sculk_rot90", "the_sift:block/dry_healthy_sculk_rot180",
            "the_sift:block/dry_healthy_sculk_rot270"), 0.6F, ToolType.HOE, List.of());
        soil("dry_healthy_sculk_growth", "red:WD", rotated("the_sift:block/dry_healthy_sculk_growth"), 0.6F,
            ToolType.HOE, List.of(new Drop("the_sift:dry_healthy_sculk", 1, 1, false)));
        soil("soul_block", "red:UD", model("the_sift:block/soul_block"), 0.5F, ToolType.SHOVEL, List.of());
        soil("ichor_snow_block", "red:NED", model("the_sift:block/ichor_snow_block"), 0.2F, ToolType.SHOVEL, List.of());

        // Overgrown willow (mushroom stems).
        wood("overgrown_willow_log[axis=y]", "overgrown_willow_log", "stem:D",
            model("the_sift:block/overgrown_willow_log"));
        wood("overgrown_willow_log[axis=x]", "overgrown_willow_log_x", "stem:ND",
            rotatedModel("the_sift:block/overgrown_willow_log_horizontal", 90, 90));
        wood("overgrown_willow_log[axis=z]", "overgrown_willow_log_z", "stem:ED",
            rotatedModel("the_sift:block/overgrown_willow_log_horizontal", 90, 0));
        wood("stripped_overgrown_willow_log[axis=y]", "stripped_overgrown_willow_log", "stem:SD",
            model("the_sift:block/stripped_overgrown_willow_log"));
        wood("stripped_overgrown_willow_log[axis=x]", "stripped_overgrown_willow_log_x", "stem:WD",
            rotatedModel("the_sift:block/stripped_overgrown_willow_log_horizontal", 90, 90));
        wood("stripped_overgrown_willow_log[axis=z]", "stripped_overgrown_willow_log_z", "stem:UD",
            rotatedModel("the_sift:block/stripped_overgrown_willow_log_horizontal", 90, 0));
        wood("overgrown_willow_wood", "overgrown_willow_wood", "stem:NED", model("the_sift:block/overgrown_willow_wood"));
        wood("stripped_overgrown_willow_wood", "stripped_overgrown_willow_wood", "stem:NSD",
            model("the_sift:block/stripped_overgrown_willow_wood"));
        wood("overgrown_willow_planks", "overgrown_willow_planks", "stem:NWD", model("the_sift:block/overgrown_willow_planks"));
        CUSTOM.put("overgrown_willow_foliage", block("overgrown_willow_foliage", "stem:NUD",
            model("the_sift:block/overgrown_willow_foliage"), 0.2F, ToolType.HOE, ToolTier.HAND, false,
            List.of(new Drop("the_sift:overgrown_willow_sapling", 0, 1, false)), "block.azalea_leaves"));

        // Vanilla stand-ins (unmodified clients cannot render new non-cube blocks).
        STAND_INS.put("healthy_sculk_sprouts", BlockStateSpec.of("minecraft:crimson_roots"));
        STAND_INS.put("dry_healthy_sculk_sprouts", BlockStateSpec.of("minecraft:short_dry_grass"));
        STAND_INS.put("siftslate_stalks", BlockStateSpec.of("minecraft:crimson_roots"));
        STAND_INS.put("overgrown_chard", BlockStateSpec.of("minecraft:fern"));
        STAND_INS.put("overgrown_fronds", BlockStateSpec.of("minecraft:short_grass"));
        STAND_INS.put("overgrown_stalks", BlockStateSpec.of("minecraft:tall_grass", Map.of("half", "lower")));
        STAND_INS.put("overgrown_lotus", BlockStateSpec.of("minecraft:lily_pad"));
        STAND_INS.put("overgrown_hanging_roots", BlockStateSpec.of("minecraft:hanging_roots"));
        STAND_INS.put("siftslate_hanging_roots", BlockStateSpec.of("minecraft:weeping_vines_plant"));
        STAND_INS.put("sunburst_plant", BlockStateSpec.of("minecraft:torchflower"));
        STAND_INS.put("whisperbloom", BlockStateSpec.of("minecraft:lily_of_the_valley"));
        STAND_INS.put("sculkflower", BlockStateSpec.of("minecraft:blue_orchid"));
        STAND_INS.put("sculkflower_crop", BlockStateSpec.of("minecraft:blue_orchid"));
        STAND_INS.put("overgrown_willow_sapling", BlockStateSpec.of("minecraft:mangrove_propagule"));
        // Willow vines are VineBlock-like in the mod; vanilla vines keep the faces and take the
        // biome foliage tint (set to the willow cyan in Sift biomes).
        STAND_INS.put("overgrown_willow_vines", BlockStateSpec.of("minecraft:vine", Map.of(
            "east", "false", "north", "false", "south", "false", "up", "false", "west", "false")));
        STAND_INS.put("overgrown_willow_vines_plant", BlockStateSpec.of("minecraft:vine", Map.of(
            "east", "false", "north", "false", "south", "false", "up", "false", "west", "false")));
        STAND_INS.put("ichor_snow", BlockStateSpec.of("minecraft:snow", Map.of("layers", "1")));
        STAND_INS.put("ichor_glass", BlockStateSpec.of("minecraft:light_blue_stained_glass"));
        STAND_INS.put("ichor", BlockStateSpec.of("minecraft:water", Map.of("level", "0")));
        STAND_INS.put("sift_portal", BlockStateSpec.of("minecraft:light", Map.of("level", "13", "waterlogged", "false")));
        STAND_INS.put("ichor_cauldron", BlockStateSpec.of("minecraft:water_cauldron", Map.of("level", "3")));
    }

    private SiftBlocks() {
    }

    /** Custom block definitions, in registration order. */
    public static List<CustomBlockDefinition> definitions() {
        return List.copyOf(CUSTOM.values());
    }

    /**
     * Resolves a mod block (optionally with properties) to what the server places.
     *
     * @param path       mod block path, e.g. {@code overgrown_willow_log}
     * @param properties the mod block's properties (axis, snowy, mode, layers...)
     */
    public static Optional<BlockStateSpec> remap(String path, Map<String, String> properties) {
        for (String key : candidateKeys(path, properties)) {
            CustomBlockDefinition custom = CUSTOM.get(key);
            if (custom != null) {
                return Optional.of(custom.state().toSpec());
            }
        }
        BlockStateSpec standIn = STAND_INS.get(path);
        if (standIn == null) {
            return Optional.empty();
        }
        if (path.equals("ichor_snow") && properties.containsKey("layers")) {
            return Optional.of(standIn.with("layers", properties.get("layers")));
        }
        if (standIn.block().path().equals("vine")) {
            BlockStateSpec vine = standIn;
            for (String face : List.of("north", "east", "south", "west", "up")) {
                if (properties.containsKey(face)) {
                    vine = vine.with(face, properties.get(face));
                }
            }
            return Optional.of(vine);
        }
        return Optional.of(standIn);
    }

    /** Vanilla block type a mod block resolves to (for type-only predicates and tags). */
    public static Optional<String> remapType(String path) {
        return remap(path, Map.of()).map(spec -> spec.block().asString());
    }

    public static boolean isKnown(String path) {
        return remap(path, Map.of()).isPresent();
    }

    public static Optional<CustomBlockDefinition> custom(String key) {
        return Optional.ofNullable(CUSTOM.get(key));
    }

    private static List<String> candidateKeys(String path, Map<String, String> properties) {
        List<String> keys = new ArrayList<>();
        for (String property : List.of("axis", "snowy", "mode")) {
            String value = properties.get(property);
            if (value != null) {
                keys.add(path + "[" + property + "=" + value + "]");
            }
        }
        keys.add(path);
        keys.add(path + "[axis=y]");
        keys.add(path + "[snowy=false]");
        keys.add(path + "[mode=horn]");
        return keys;
    }

    private static void stone(String path, String state, JsonElement apply, float hardness, List<Drop> drops, String sound) {
        CUSTOM.put(path, block(path, state, apply, hardness, ToolType.PICKAXE, ToolTier.WOOD, true, drops, sound));
    }

    private static void ore(String path, String state, String drop, ToolTier tier, int minXp, int maxXp) {
        CustomBlockDefinition definition = new CustomBlockDefinition(NamespacedId.of(NS, path), MushroomState.parse(state),
            model("the_sift:block/" + path), 4.5F, ToolType.PICKAXE, tier, true,
            List.of(new Drop(drop, 1, 1, true)), true, minXp, maxXp, "block.deepslate");
        CUSTOM.put(path, definition);
    }

    private static void soil(String path, String state, JsonElement apply, float hardness, ToolType tool, List<Drop> drops) {
        CUSTOM.put(path, block(path, state, apply, hardness, tool, ToolTier.HAND, false, drops, "block.nylium"));
    }

    private static void wood(String key, String id, String state, JsonElement apply) {
        List<Drop> drops = id.equals(key) || !id.contains("_log_")
            ? List.of()
            : List.of(new Drop("the_sift:" + id.substring(0, id.lastIndexOf('_')), 1, 1, false));
        CUSTOM.put(key, block(id, state, apply, 2.0F, ToolType.AXE, ToolTier.HAND, false, drops, "block.wood"));
    }

    private static CustomBlockDefinition block(
        String id, String state, JsonElement apply, float hardness, ToolType tool, ToolTier tier,
        boolean requiresTool, List<Drop> drops, String sound
    ) {
        return new CustomBlockDefinition(NamespacedId.of(NS, id), MushroomState.parse(state), apply, hardness, tool, tier,
            requiresTool, drops, true, 0, 0, sound);
    }

    private static JsonElement model(String model) {
        return Json.object().put("model", model).build();
    }

    private static JsonElement rotatedModel(String model, int x, int y) {
        Json.ObjectBuilder builder = Json.object().put("model", model);
        if (x != 0) {
            builder.put("x", x);
        }
        if (y != 0) {
            builder.put("y", y);
        }
        return builder.build();
    }

    private static JsonElement variants(String... models) {
        JsonArray array = new JsonArray();
        for (String model : models) {
            array.add(model(model));
        }
        return array;
    }

    /** The mod's "random rotation" variant list: each model in four Y rotations. */
    private static JsonElement rotated(String... models) {
        JsonArray array = new JsonArray();
        for (String model : models) {
            for (int y : new int[]{0, 90, 180, 270}) {
                array.add(rotatedModel(model, 0, y));
            }
        }
        return array;
    }

    /** Read-only view used by tests and the asset contract. */
    static Map<String, CustomBlockDefinition> customByKey() {
        return Collections.unmodifiableMap(CUSTOM);
    }

    static Map<String, BlockStateSpec> standIns() {
        return Collections.unmodifiableMap(STAND_INS);
    }
}
