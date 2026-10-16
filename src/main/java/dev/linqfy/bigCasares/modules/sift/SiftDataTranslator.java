package dev.linqfy.bigCasares.modules.sift;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.linqfy.bigCasares.blocks.CustomBlockDefinition;
import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.dimension.json.Json;
import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;
import dev.linqfy.bigCasares.dimension.worldgen.DensityFunction;
import dev.linqfy.bigCasares.dimension.worldgen.DensityFunctions;
import dev.linqfy.bigCasares.dimension.worldgen.GenerationContext;
import dev.linqfy.bigCasares.dimension.worldgen.NoiseDefinition;
import dev.linqfy.bigCasares.dimension.worldgen.SurfaceRule;
import dev.linqfy.bigCasares.dimension.worldgen.SurfaceRule.SurfaceCondition;
import dev.linqfy.bigCasares.dimension.worldgen.VerticalAnchor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Translates Mielon's Minecraft 26.3 data files into the Minecraft 26.2 model.
 *
 * <p>Only the constructs the mod actually uses are supported; anything else
 * fails with the JSON path, so a future mod update cannot silently produce a
 * datapack the server rejects. Mod blocks are remapped through {@link SiftBlocks}.
 * Differences handled here:</p>
 * <ul>
 *   <li>noises: {@code base_octave/octave_count/amplitude_modifiers + normalize: legacy}
 *       &rarr; {@code firstOctave/amplitudes} (legacy normalisation is 26.2's only mode)</li>
 *   <li>density functions: {@code left/right} &rarr; {@code argument1/argument2},
 *       {@code input} &rarr; {@code argument}, {@code gradient(axis=y, clamp_to_edge)}
 *       &rarr; {@code y_clamped_gradient}, {@code noise} with shifts &rarr; {@code shifted_noise},
 *       {@code interpolated} cell sizes &rarr; noise-settings cell size</li>
 *   <li>block states: {@code {"id": ...}} &rarr; {@code {"Name": ...}}; providers
 *       {@code simple/weighted} &rarr; {@code simple_state_provider/weighted_state_provider}</li>
 *   <li>features lose the {@code config} wrapper in 26.3 and regain it here;
 *       the {@code offset} placement becomes {@code random_offset}</li>
 * </ul>
 */
public final class SiftDataTranslator {

    /** Vanilla density functions that exist in 26.2 and may be referenced. */
    private static final Set<String> VANILLA_FUNCTIONS = Set.of("minecraft:shift_x", "minecraft:shift_z", "minecraft:y", "minecraft:zero");

    private final GenerationContext context;
    private final Function<String, JsonElement> densityFunctionSource;
    private final Map<NamespacedId, DensityFunction> namedFunctions = new LinkedHashMap<>();
    private int cellSizeXz = 4;
    private int cellSizeY = 8;

    /**
     * @param densityFunctionSource returns the raw JSON of a mod density function by
     *     path (e.g. {@code final_density}), or null when absent
     */
    public SiftDataTranslator(GenerationContext context, Function<String, JsonElement> densityFunctionSource) {
        this.context = context;
        this.densityFunctionSource = densityFunctionSource;
    }

    public Map<NamespacedId, DensityFunction> namedFunctions() {
        return Map.copyOf(namedFunctions);
    }

    public int cellSizeXz() {
        return cellSizeXz;
    }

    public int cellSizeY() {
        return cellSizeY;
    }

    // --- noises -------------------------------------------------------------------------

    public static NoiseDefinition noise(NamespacedId id, JsonObject json) {
        String normalize = json.has("normalize") ? json.get("normalize").getAsString() : null;
        if (json.has("base_amplitude") || normalize != null && !normalize.equals("legacy")) {
            throw new IllegalArgumentException("Noise " + id + " uses non-legacy normalisation, unsupported by 26.2");
        }
        int firstOctave = json.get("base_octave").getAsInt();
        int count = json.has("octave_count") ? json.get("octave_count").getAsInt() : 1;
        List<Double> amplitudes = new ArrayList<>();
        if (json.has("amplitude_modifiers")) {
            for (JsonElement element : json.getAsJsonArray("amplitude_modifiers")) {
                amplitudes.add(element.getAsDouble());
            }
        }
        while (amplitudes.size() < count) {
            amplitudes.add(1.0);
        }
        return new NoiseDefinition(id, firstOctave, amplitudes);
    }

    // --- density functions --------------------------------------------------------------

    public DensityFunction densityFunction(JsonElement json, String path) {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
            return DensityFunctions.constant(json.getAsDouble());
        }
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            return reference(json.getAsString(), path);
        }
        if (!json.isJsonObject()) {
            throw unsupported(path, "density function " + json);
        }
        JsonObject object = json.getAsJsonObject();
        String type = string(object, "type", path);
        return switch (type) {
            case "minecraft:add" -> DensityFunctions.add(arg(object, "left", "argument1", path), arg(object, "right", "argument2", path));
            case "minecraft:mul" -> DensityFunctions.mul(arg(object, "left", "argument1", path), arg(object, "right", "argument2", path));
            case "minecraft:min" -> DensityFunctions.min(arg(object, "left", "argument1", path), arg(object, "right", "argument2", path));
            case "minecraft:max" -> DensityFunctions.max(arg(object, "left", "argument1", path), arg(object, "right", "argument2", path));
            case "minecraft:abs" -> DensityFunctions.abs(arg(object, "input", "argument", path));
            case "minecraft:square" -> DensityFunctions.square(arg(object, "input", "argument", path));
            case "minecraft:cube" -> DensityFunctions.cube(arg(object, "input", "argument", path));
            case "minecraft:clamp" -> DensityFunctions.clamp(arg(object, "input", "input", path),
                object.get("min").getAsDouble(), object.get("max").getAsDouble());
            case "minecraft:range_choice" -> DensityFunctions.rangeChoice(arg(object, "input", "input", path),
                object.get("min_inclusive").getAsDouble(), object.get("max_exclusive").getAsDouble(),
                arg(object, "when_in_range", "when_in_range", path), arg(object, "when_out_of_range", "when_out_of_range", path));
            case "minecraft:gradient" -> gradient(object, path);
            case "minecraft:y_clamped_gradient" -> DensityFunctions.yGradient(object.get("from_y").getAsInt(),
                object.get("to_y").getAsInt(), object.get("from_value").getAsDouble(), object.get("to_value").getAsDouble());
            case "minecraft:interpolated" -> {
                if (object.has("cell_size_xz")) {
                    cellSizeXz = object.get("cell_size_xz").getAsInt();
                }
                if (object.has("cell_size_y")) {
                    cellSizeY = object.get("cell_size_y").getAsInt();
                }
                yield DensityFunctions.interpolated(arg(object, "input", "argument", path));
            }
            case "minecraft:noise", "minecraft:shifted_noise" -> noiseSample(object, path);
            case "minecraft:beardifier" -> DensityFunctions.beardifier();
            default -> throw unsupported(path, "density function type " + type);
        };
    }

    private DensityFunction gradient(JsonObject object, String path) {
        String axis = object.has("axis") ? object.get("axis").getAsString() : "y";
        String tiling = object.has("tiling") ? object.get("tiling").getAsString() : "clamp_to_edge";
        if (!axis.equals("y") || !tiling.equals("clamp_to_edge")) {
            throw unsupported(path, "gradient axis=" + axis + " tiling=" + tiling + " (26.2 only has y_clamped_gradient)");
        }
        return DensityFunctions.yGradient(object.get("from_coordinate").getAsInt(), object.get("to_coordinate").getAsInt(),
            object.get("from_value").getAsDouble(), object.get("to_value").getAsDouble());
    }

    private DensityFunction noiseSample(JsonObject object, String path) {
        NamespacedId noise = NamespacedId.parse(string(object, "noise", path));
        double xzScale = object.has("xz_scale") ? object.get("xz_scale").getAsDouble() : 1.0;
        double yScale = object.has("y_scale") ? object.get("y_scale").getAsDouble() : 1.0;
        boolean shifted = object.has("shift_x") || object.has("shift_y") || object.has("shift_z");
        if (!shifted) {
            return context.noise(noise, xzScale, yScale);
        }
        return new DensityFunction.ShiftedNoise(noise, xzScale, yScale,
            shift(object, "shift_x", path), shift(object, "shift_y", path), shift(object, "shift_z", path));
    }

    private DensityFunction shift(JsonObject object, String key, String path) {
        return object.has(key) ? densityFunction(object.get(key), path + "." + key) : DensityFunctions.ZERO;
    }

    private DensityFunction reference(String raw, String path) {
        NamespacedId id = NamespacedId.parse(raw);
        if (id.namespace().equals("minecraft")) {
            if (!VANILLA_FUNCTIONS.contains(id.asString())) {
                throw unsupported(path, "vanilla density function reference " + raw);
            }
            return DensityFunctions.ref(id);
        }
        NamespacedId scoped = context.id("density/" + id.path());
        if (!namedFunctions.containsKey(scoped)) {
            JsonElement source = densityFunctionSource.apply(id.path());
            if (source == null) {
                throw unsupported(path, "missing density function " + raw);
            }
            // Reserve the id first so recursive references terminate.
            namedFunctions.put(scoped, DensityFunctions.ZERO);
            namedFunctions.put(scoped, densityFunction(source, raw));
        }
        return DensityFunctions.ref(scoped);
    }

    private DensityFunction arg(JsonObject object, String key, String legacyKey, String path) {
        JsonElement value = object.has(key) ? object.get(key) : object.get(legacyKey);
        if (value == null) {
            throw unsupported(path, "missing argument " + key);
        }
        return densityFunction(value, path + "." + key);
    }

    // --- surface rules ------------------------------------------------------------------

    public static SurfaceRule surfaceRule(JsonElement json, String path) {
        JsonObject object = json.getAsJsonObject();
        String type = string(object, "type", path);
        return switch (type) {
            case "minecraft:sequence" -> {
                List<SurfaceRule> rules = new ArrayList<>();
                int index = 0;
                for (JsonElement element : object.getAsJsonArray("sequence")) {
                    rules.add(surfaceRule(element, path + "[" + index++ + "]"));
                }
                yield SurfaceRule.sequence(rules);
            }
            case "minecraft:condition" -> SurfaceRule.when(
                surfaceCondition(object.get("if_true"), path + ".if_true"),
                surfaceRule(object.get("then_run"), path + ".then_run"));
            case "minecraft:block" -> SurfaceRule.block(blockState(object.getAsJsonObject("result_state"), path));
            default -> throw unsupported(path, "surface rule type " + type);
        };
    }

    private static SurfaceCondition surfaceCondition(JsonElement json, String path) {
        JsonObject object = json.getAsJsonObject();
        String type = string(object, "type", path);
        return switch (type) {
            case "minecraft:biome" -> {
                List<NamespacedId> biomes = new ArrayList<>();
                for (JsonElement element : asArray(object.get("biome_is"))) {
                    biomes.add(NamespacedId.parse(element.getAsString()));
                }
                yield new SurfaceRule.BiomeIs(biomes);
            }
            case "minecraft:noise_threshold" -> new SurfaceRule.NoiseThreshold(
                NamespacedId.parse(string(object, "noise", path)),
                object.get("min_threshold").getAsDouble(), object.get("max_threshold").getAsDouble());
            case "minecraft:stone_depth" -> new SurfaceRule.StoneDepth(
                object.get("offset").getAsInt(),
                object.get("add_surface_depth").getAsBoolean(),
                object.get("secondary_depth_range").getAsInt(),
                SurfaceRule.CaveSurface.valueOf(object.get("surface_type").getAsString().toUpperCase(java.util.Locale.ROOT)));
            case "minecraft:vertical_gradient" -> new SurfaceRule.VerticalGradient(
                string(object, "random_name", path),
                anchor(object.getAsJsonObject("true_at_and_below"), path),
                anchor(object.getAsJsonObject("false_at_and_above"), path));
            case "minecraft:not" -> new SurfaceRule.Not(surfaceCondition(object.get("invert"), path + ".invert"));
            default -> throw unsupported(path, "surface condition type " + type);
        };
    }

    private static VerticalAnchor anchor(JsonObject object, String path) {
        if (object.has("absolute")) {
            return VerticalAnchor.absolute(object.get("absolute").getAsInt());
        }
        if (object.has("above_bottom")) {
            return VerticalAnchor.aboveBottom(object.get("above_bottom").getAsInt());
        }
        if (object.has("below_top")) {
            return VerticalAnchor.belowTop(object.get("below_top").getAsInt());
        }
        throw unsupported(path, "vertical anchor " + object);
    }

    // --- block states -------------------------------------------------------------------

    /** Translates a block state object or bare id string, remapping mod blocks. */
    public static BlockStateSpec blockState(JsonElement json, String path) {
        String id;
        Map<String, String> properties = new TreeMap<>();
        if (json.isJsonPrimitive()) {
            id = json.getAsString();
        } else {
            JsonObject object = json.getAsJsonObject();
            id = object.has("id") ? object.get("id").getAsString() : string(object, "Name", path);
            JsonObject props = object.has("properties") ? object.getAsJsonObject("properties")
                : object.has("Properties") ? object.getAsJsonObject("Properties") : null;
            if (props != null) {
                props.entrySet().forEach(entry -> properties.put(entry.getKey(), entry.getValue().getAsString()));
            }
        }
        NamespacedId block = NamespacedId.parse(id);
        if (block.namespace().equals("minecraft")) {
            return new BlockStateSpec(block, new TreeMap<>(properties));
        }
        if (!block.namespace().equals(SiftBlocks.NS)) {
            throw unsupported(path, "block from unknown namespace " + id);
        }
        return SiftBlocks.remap(block.path(), properties)
            .orElseThrow(() -> unsupported(path, "unmapped Sift block " + id));
    }

    /** Remaps a block id used where only block types are allowed (predicates, tags). */
    public static String blockType(String id, String path) {
        NamespacedId block = NamespacedId.parse(id);
        if (block.namespace().equals("minecraft")) {
            return block.asString();
        }
        return SiftBlocks.remapType(block.path()).orElseThrow(() -> unsupported(path, "unmapped Sift block " + id));
    }

    // --- features -----------------------------------------------------------------------

    /**
     * Converts a 26.3 configured feature of a vanilla type into its 26.2 config.
     * {@code blockTags} resolves the mod's block tags to their member ids.
     */
    public static JsonObject featureConfig(JsonObject feature, String path, Function<String, List<String>> blockTags) {
        String type = string(feature, "type", path);
        JsonObject config = new JsonObject();
        switch (type) {
            case "minecraft:ore" -> {
                config.addProperty("discard_chance_on_air_exposure", feature.get("discard_chance_on_air_exposure").getAsFloat());
                config.addProperty("size", feature.get("size").getAsInt());
                JsonArray targets = new JsonArray();
                for (JsonElement element : feature.getAsJsonArray("targets")) {
                    JsonObject target = element.getAsJsonObject();
                    BlockStateSpec state = blockState(target.get("state"), path + ".state");
                    for (JsonObject test : ruleTests(target.getAsJsonObject("target"), path, blockTags)) {
                        targets.add(Json.object().put("state", state).put("target", test).build());
                    }
                }
                config.add("targets", targets);
            }
            case "minecraft:simple_block" -> config.add("to_place", stateProvider(feature.getAsJsonObject("to_place"), path));
            case "minecraft:lake" -> {
                config.add("barrier", stateProvider(feature.getAsJsonObject("barrier"), path + ".barrier"));
                config.add("can_place_feature", blockPredicate(feature.get("can_place_feature"), path, blockTags));
                config.add("can_replace_with_air_or_fluid", blockPredicate(feature.get("can_replace_with_air_or_fluid"), path, blockTags));
                config.add("can_replace_with_barrier", blockPredicate(feature.get("can_replace_with_barrier"), path, blockTags));
                config.add("fluid", stateProvider(feature.getAsJsonObject("fluid"), path + ".fluid"));
            }
            case "minecraft:fossil" -> {
                for (String key : List.of("fossil_processors", "fossil_structures", "max_empty_corners_allowed",
                    "overlay_processors", "overlay_structures")) {
                    config.add(key, feature.get(key).deepCopy());
                }
            }
            default -> throw unsupported(path, "feature type " + type);
        }
        return config;
    }

    private static List<JsonObject> ruleTests(JsonObject test, String path, Function<String, List<String>> blockTags) {
        String type = test.get("predicate_type").getAsString();
        List<JsonObject> tests = new ArrayList<>();
        switch (type) {
            case "minecraft:tag_match" -> {
                String tag = test.get("tag").getAsString();
                if (tag.startsWith("minecraft:")) {
                    tests.add(test.deepCopy());
                } else {
                    // Custom blocks share a vanilla block type, so match their exact state.
                    for (String member : blockTags.apply(tag)) {
                        tests.add(Json.object()
                            .put("block_state", blockState(new JsonPrimitive(member), path))
                            .put("predicate_type", "minecraft:blockstate_match")
                            .build());
                    }
                }
            }
            case "minecraft:block_match" -> tests.add(Json.object()
                .put("block", blockType(test.get("block").getAsString(), path))
                .put("predicate_type", "minecraft:block_match")
                .build());
            case "minecraft:always_true" -> tests.add(test.deepCopy());
            default -> throw unsupported(path, "rule test " + type);
        }
        return tests;
    }

    static JsonObject stateProvider(JsonObject provider, String path) {
        String type = string(provider, "type", path);
        return switch (type) {
            case "minecraft:simple", "minecraft:simple_state_provider" -> Json.object()
                .put("type", "minecraft:simple_state_provider")
                .put("state", blockState(provider.get("state"), path))
                .build();
            default -> throw unsupported(path, "block state provider " + type);
        };
    }

    public static JsonObject blockPredicate(JsonElement json, String path, Function<String, List<String>> blockTags) {
        if (json == null || !json.isJsonObject()) {
            throw unsupported(path, "missing block predicate");
        }
        JsonObject predicate = json.getAsJsonObject();
        String type = string(predicate, "type", path);
        Json.ObjectBuilder out = Json.object().put("type", type);
        switch (type) {
            case "minecraft:matching_blocks" -> {
                Set<String> blocks = new LinkedHashSet<>();
                for (JsonElement element : asArray(predicate.get("blocks"))) {
                    blocks.add(blockType(element.getAsString(), path));
                }
                out.put("blocks", blocks.size() == 1 ? blocks.iterator().next() : List.copyOf(blocks));
                copyOffset(predicate, out);
            }
            case "minecraft:matching_block_tag" -> {
                out.put("tag", predicate.get("tag").getAsString());
                copyOffset(predicate, out);
            }
            case "minecraft:all_of", "minecraft:any_of" -> {
                JsonArray predicates = new JsonArray();
                int index = 0;
                for (JsonElement element : predicate.getAsJsonArray("predicates")) {
                    predicates.add(blockPredicate(element, path + "[" + index++ + "]", blockTags));
                }
                out.put("predicates", predicates);
            }
            case "minecraft:not" -> out.put("predicate", blockPredicate(predicate.get("predicate"), path + ".not", blockTags));
            case "minecraft:true", "minecraft:solid", "minecraft:replaceable" -> copyOffset(predicate, out);
            case "minecraft:inside_world_bounds" -> copyOffset(predicate, out);
            case "minecraft:has_sturdy_face" -> {
                out.put("direction", predicate.get("direction"));
                copyOffset(predicate, out);
            }
            default -> throw unsupported(path, "block predicate " + type);
        }
        return out.build();
    }

    private static void copyOffset(JsonObject source, Json.ObjectBuilder out) {
        if (source.has("offset")) {
            out.put("offset", source.get("offset"));
        }
    }

    /** Converts one 26.3 placement modifier. */
    public static JsonObject placement(JsonObject modifier, String path, Function<String, List<String>> blockTags) {
        String type = string(modifier, "type", path);
        return switch (type) {
            case "minecraft:count", "minecraft:in_square", "minecraft:heightmap", "minecraft:biome",
                 "minecraft:rarity_filter", "minecraft:height_range", "minecraft:surface_relative_threshold_filter" ->
                modifier.deepCopy();
            case "minecraft:block_predicate_filter" -> Json.object()
                .put("type", type)
                .put("predicate", blockPredicate(modifier.get("predicate"), path + ".predicate", blockTags))
                .build();
            case "minecraft:environment_scan" -> {
                Json.ObjectBuilder scan = Json.object().put("type", type);
                if (modifier.has("allowed_search_condition")) {
                    scan.put("allowed_search_condition",
                        blockPredicate(modifier.get("allowed_search_condition"), path, blockTags));
                }
                yield scan
                    .put("direction_of_search", modifier.get("direction_of_search"))
                    .put("max_steps", modifier.get("max_steps"))
                    .put("target_condition", blockPredicate(modifier.get("target_condition"), path, blockTags))
                    .build();
            }
            case "minecraft:offset" -> {
                JsonElement x = modifier.get("x");
                JsonElement z = modifier.get("z");
                if (x == null || !x.equals(z)) {
                    throw unsupported(path, "offset with different x/z providers (26.2 random_offset shares one)");
                }
                yield Json.object()
                    .put("type", "minecraft:random_offset")
                    .put("xz_spread", x)
                    .put("y_spread", modifier.has("y") ? modifier.get("y") : new JsonPrimitive(0))
                    .build();
            }
            default -> throw unsupported(path, "placement modifier " + type);
        };
    }

    // --- processors ---------------------------------------------------------------------

    public static JsonObject processorList(JsonObject list, String path) {
        JsonArray processors = new JsonArray();
        int index = 0;
        for (JsonElement element : list.getAsJsonArray("processors")) {
            JsonObject processor = element.getAsJsonObject();
            String type = processor.get("processor_type").getAsString();
            String at = path + "[" + index++ + "]";
            switch (type) {
                case "minecraft:block_rot", "minecraft:protected_blocks" -> processors.add(processor.deepCopy());
                case "minecraft:rule" -> {
                    JsonArray rules = new JsonArray();
                    for (JsonElement rawRule : processor.getAsJsonArray("rules")) {
                        JsonObject rule = rawRule.getAsJsonObject();
                        JsonObject input = rule.getAsJsonObject("input_predicate");
                        List<JsonObject> inputs = ruleTests(input, at, tag -> List.of());
                        for (JsonObject test : inputs) {
                            rules.add(Json.object()
                                .put("input_predicate", test)
                                .put("location_predicate", rule.get("location_predicate"))
                                .put("output_state", blockState(rule.get("output_state"), at))
                                .build());
                        }
                    }
                    processors.add(Json.object().put("processor_type", type).put("rules", rules).build());
                }
                default -> throw unsupported(at, "processor " + type);
            }
        }
        return Json.object().put("processors", processors).build();
    }

    /** Block tag translation: mod members become their vanilla backing types (deduplicated). */
    public static List<String> blockTagValues(JsonObject tag, String path) {
        Set<String> values = new LinkedHashSet<>();
        for (JsonElement element : tag.getAsJsonArray("values")) {
            String value = element.getAsString();
            values.add(value.startsWith("#") ? value : blockType(value, path));
        }
        return List.copyOf(values);
    }

    /** Custom block definitions that a mod tag resolves to (for exact-state matching). */
    public static List<CustomBlockDefinition> customMembers(List<String> modIds) {
        List<CustomBlockDefinition> members = new ArrayList<>();
        for (String id : modIds) {
            NamespacedId parsed = NamespacedId.parse(id);
            SiftBlocks.custom(parsed.path()).ifPresent(members::add);
        }
        return members;
    }

    // --- helpers ------------------------------------------------------------------------

    private static JsonArray asArray(JsonElement element) {
        if (element.isJsonArray()) {
            return element.getAsJsonArray();
        }
        JsonArray array = new JsonArray();
        array.add(element);
        return array;
    }

    private static String string(JsonObject object, String key, String path) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()) {
            throw unsupported(path, "missing string field '" + key + "'");
        }
        return value.getAsString();
    }

    static IllegalArgumentException unsupported(String path, String what) {
        return new IllegalArgumentException("Cannot translate " + what + " at " + path);
    }
}
