package dev.linqfy.bigCasares.modules.sift;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.dimension.datapack.DatapackBuilder;
import dev.linqfy.bigCasares.dimension.json.Json;
import dev.linqfy.bigCasares.dimension.worldgen.BiomeDefinition;
import dev.linqfy.bigCasares.dimension.worldgen.BiomeDefinition.MobCategory;
import dev.linqfy.bigCasares.dimension.worldgen.BiomeDefinition.SpawnEntry;
import dev.linqfy.bigCasares.dimension.worldgen.BiomeDefinition.Step;
import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;
import dev.linqfy.bigCasares.dimension.worldgen.CarverDefinition;
import dev.linqfy.bigCasares.dimension.worldgen.Climate;
import dev.linqfy.bigCasares.dimension.worldgen.DensityFunction;
import dev.linqfy.bigCasares.dimension.worldgen.DensityFunctions;
import dev.linqfy.bigCasares.dimension.worldgen.EnvironmentAttributes;
import dev.linqfy.bigCasares.dimension.worldgen.FeatureDefinition;
import dev.linqfy.bigCasares.dimension.worldgen.GenerationContext;
import dev.linqfy.bigCasares.dimension.worldgen.NoiseRouter;
import dev.linqfy.bigCasares.dimension.worldgen.PlacedFeatureDefinition;
import dev.linqfy.bigCasares.dimension.worldgen.SurfaceRule;
import dev.linqfy.bigCasares.dimension.worldgen.TerrainSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Assembles the Sift's native worldgen from the imported mod data.
 *
 * <p>Vanilla-typed features become native placed features; the mod's Java-only
 * feature types are collected as {@link CustomPlacement}s and executed by
 * {@link SiftPopulator} during the same decoration step.</p>
 */
public final class SiftWorldgen {

    /** Height range of the mod's dimension type. */
    public static final int MIN_Y = 0;
    public static final int HEIGHT = 256;

    /** A mod feature implemented in Java, with the biomes and placement it was declared with. */
    public record CustomPlacement(String placedId, String featureType, Step step, Set<NamespacedId> biomes, JsonArray placement) {
    }

    /**
     * Grass/foliage tints for vanilla stand-in plants (an addition of this port:
     * the mod's own plants carry their colours in their textures).
     */
    private static final Map<String, int[]> STAND_IN_TINTS = Map.of(
        "overgrown_forest", new int[]{0x6bb194, 0x8ae5ea},
        "overgrown_clearing", new int[]{0x72c09e, 0x8ae5ea},
        "overgrown_slopes", new int[]{0x6bb194, 0x8ae5ea},
        "overgrown_forest_slopes", new int[]{0x67b08e, 0x8ae5ea},
        "overgrown_peaks", new int[]{0x72c09e, 0x8ae5ea},
        "ichor_snowy_peaks", new int[]{0x9fc8c8, 0xc6e6ea},
        "siftslate_slopes", new int[]{0xb46a5f, 0x8ae5ea},
        "siftslate_peaks", new int[]{0xb46a5f, 0x8ae5ea},
        "sift_wastes", new int[]{0xc98a6d, 0xd8b090},
        "sift_deep_dark", new int[]{0x24767f, 0x24767f}
    );

    private final SiftData data;
    private final Logger logger;
    private final Map<String, PlacedFeatureDefinition> nativePlaced = new LinkedHashMap<>();
    private final Map<String, CustomPlacement> customPlaced = new LinkedHashMap<>();
    private final Map<String, CarverDefinition> carvers = new LinkedHashMap<>();
    private final Map<String, List<String>> modBlockTags = new LinkedHashMap<>();
    private final Set<String> droppedSpawns = new LinkedHashSet<>();

    public SiftWorldgen(SiftData data, Logger logger) {
        this.data = data;
        this.logger = logger;
    }

    public SiftData data() {
        return data;
    }

    // --- terrain ------------------------------------------------------------------------

    public TerrainSpec terrain(GenerationContext context) {
        JsonObject settings = data.json("worldgen/noise_settings/the_sift.json");
        JsonObject router = settings.getAsJsonObject("noise_router");
        SiftDataTranslator translator = new SiftDataTranslator(context,
            path -> data.optionalJson("worldgen/density_function/" + path + ".json").orElse(null));
        registerNoises(context, router, settings.get("material_rule"));

        DensityFunction finalDensity = translator.densityFunction(router.get("final_density"), "noise_router.final_density");
        NoiseRouter translated = NoiseRouter.builder()
            .temperature(translator.densityFunction(router.get("temperature"), "noise_router.temperature"))
            .vegetation(translator.densityFunction(router.get("vegetation"), "noise_router.vegetation"))
            .continents(translator.densityFunction(router.get("continents"), "noise_router.continents"))
            .erosion(translator.densityFunction(router.get("erosion"), "noise_router.erosion"))
            .depth(translator.densityFunction(router.get("depth"), "noise_router.depth"))
            .ridges(translator.densityFunction(router.get("ridges"), "noise_router.ridges"))
            .finalDensity(finalDensity)
            .build();
        SurfaceRule surface = SiftDataTranslator.surfaceRule(settings.get("material_rule"), "material_rule");
        int sizeHorizontal = Math.max(1, translator.cellSizeXz() / 4);
        int sizeVertical = Math.max(1, translator.cellSizeY() / 4);
        JsonObject noise = settings.getAsJsonObject("noise");
        return new TerrainSpec(
            noise.get("min_y").getAsInt(),
            noise.get("height").getAsInt(),
            sizeHorizontal,
            sizeVertical,
            settings.get("sea_level").getAsInt(),
            SiftDataTranslator.blockState(settings.get("default_block"), "default_block"),
            SiftDataTranslator.blockState(settings.get("default_fluid"), "default_fluid"),
            false,
            false,
            settings.get("disable_mob_generation").getAsBoolean(),
            translated,
            surface,
            List.of(),
            translator.namedFunctions());
    }

    /** Registers every mod noise referenced by density functions or surface rules. */
    private void registerNoises(GenerationContext context, JsonObject router, JsonElement surface) {
        Set<String> referenced = new LinkedHashSet<>();
        collectNoiseIds(router, referenced);
        collectNoiseIds(surface, referenced);
        // Density function files may reference further density functions and noises;
        // follow them until no new ids appear.
        Set<String> expanded = new LinkedHashSet<>();
        while (!expanded.containsAll(referenced)) {
            for (String id : List.copyOf(referenced)) {
                if (!expanded.add(id) || !id.startsWith(SiftBlocks.NS + ":")) {
                    continue;
                }
                data.optionalJson("worldgen/density_function/" + id.substring(SiftBlocks.NS.length() + 1) + ".json")
                    .ifPresent(json -> collectNoiseIds(json, referenced));
            }
        }
        for (String id : referenced) {
            NamespacedId parsed = NamespacedId.parse(id);
            if (!parsed.namespace().equals(SiftBlocks.NS)) {
                continue;
            }
            data.optionalJson("worldgen/noise/" + parsed.path() + ".json")
                .ifPresent(json -> context.registerNoise(SiftDataTranslator.noise(parsed, json)));
        }
    }

    private static void collectNoiseIds(JsonElement element, Set<String> out) {
        if (element == null) {
            return;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                if (entry.getKey().equals("noise") && entry.getValue().isJsonPrimitive()) {
                    out.add(entry.getValue().getAsString());
                } else {
                    collectNoiseIds(entry.getValue(), out);
                }
            }
        } else if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> collectNoiseIds(child, out));
        } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String value = element.getAsString();
            if (value.startsWith(SiftBlocks.NS + ":")) {
                out.add(value);
            }
        }
    }

    // --- biomes -------------------------------------------------------------------------

    public List<BiomeDefinition> biomes(GenerationContext context) {
        Map<String, List<Climate.ParameterPoint>> placements = climatePlacements();
        List<BiomeDefinition> biomes = new ArrayList<>();
        for (String name : SiftData.BIOMES) {
            biomes.add(biome(name, placements.getOrDefault(name, List.of())));
        }
        if (!droppedSpawns.isEmpty()) {
            logger.info("[TheSift] Left out of biome spawn lists (the Echo Golem has its own spawner): " + droppedSpawns);
        }
        return biomes;
    }

    private Map<String, List<Climate.ParameterPoint>> climatePlacements() {
        JsonObject dimension = data.json("dimension/the_sift.json");
        JsonArray entries = dimension.getAsJsonObject("generator").getAsJsonObject("biome_source").getAsJsonArray("biomes");
        Map<String, List<Climate.ParameterPoint>> placements = new LinkedHashMap<>();
        for (JsonElement element : entries) {
            JsonObject entry = element.getAsJsonObject();
            String biome = NamespacedId.parse(entry.get("biome").getAsString()).path();
            JsonObject parameters = entry.getAsJsonObject("parameters");
            Climate.ParameterPoint point = new Climate.ParameterPoint(
                range(parameters.get("temperature")), range(parameters.get("humidity")),
                range(parameters.get("continentalness")), range(parameters.get("erosion")),
                range(parameters.get("depth")), range(parameters.get("weirdness")),
                parameters.has("offset") ? parameters.get("offset").getAsFloat() : 0.0F);
            placements.computeIfAbsent(biome, ignored -> new ArrayList<>()).add(point);
        }
        return placements;
    }

    private static Climate.Range range(JsonElement element) {
        if (element.isJsonArray()) {
            return Climate.Range.span(element.getAsJsonArray().get(0).getAsFloat(), element.getAsJsonArray().get(1).getAsFloat());
        }
        return Climate.Range.point(element.getAsFloat());
    }

    private BiomeDefinition biome(String name, List<Climate.ParameterPoint> placements) {
        JsonObject json = data.json("worldgen/biome/" + name + ".json");
        NamespacedId id = NamespacedId.of(SiftBlocks.NS, name);
        BiomeDefinition.Builder builder = BiomeDefinition.builder(id)
            .climate(json.get("temperature").getAsFloat(), json.get("downfall").getAsFloat(),
                json.get("has_precipitation").getAsBoolean());
        JsonObject effects = json.getAsJsonObject("effects");
        builder.waterColor(color(effects.get("water_color").getAsString()));
        int[] tints = STAND_IN_TINTS.get(name);
        if (tints != null) {
            builder.grassColor(tints[0]).foliageColor(tints[1]).dryFoliageColor(tints[1]);
        }
        builder.attributes(attributes(json.getAsJsonObject("attributes")));

        JsonObject spawns = json.getAsJsonObject("attributes").getAsJsonObject("minecraft:gameplay/natural_mob_spawns");
        if (spawns != null) {
            applySpawns(builder, spawns);
        }
        JsonElement probability = json.getAsJsonObject("attributes").get("minecraft:gameplay/creature_world_gen_spawn_probability");
        if (probability != null) {
            builder.creatureSpawnProbability(probability.getAsFloat());
        }
        for (JsonElement carver : json.getAsJsonArray("carvers")) {
            builder.carver(carver(NamespacedId.parse(carver.getAsString()).path()));
        }
        JsonArray steps = json.getAsJsonArray("features");
        for (int index = 0; index < steps.size() && index < Step.values().length; index++) {
            Step step = Step.values()[index];
            for (JsonElement feature : steps.get(index).getAsJsonArray()) {
                NamespacedId reference = NamespacedId.parse(feature.getAsString());
                if (!reference.namespace().equals(SiftBlocks.NS)) {
                    builder.existingFeature(step, reference);
                    continue;
                }
                PlacedFeatureDefinition placed = placedFeature(reference.path(), step, id);
                if (placed != null) {
                    builder.feature(step, placed);
                }
            }
        }
        placements.forEach(builder::placement);
        return builder.build();
    }

    private EnvironmentAttributes attributes(JsonObject source) {
        EnvironmentAttributes attributes = EnvironmentAttributes.create();
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            String path = NamespacedId.parse(entry.getKey()).path();
            if (path.equals("visual/ambient_particles")) {
                JsonElement value = entry.getValue();
                JsonArray list = value.isJsonObject() ? value.getAsJsonObject().getAsJsonArray("argument") : value.getAsJsonArray();
                Map<String, Double> chances = new LinkedHashMap<>();
                for (JsonElement particle : list) {
                    JsonObject object = particle.getAsJsonObject();
                    chances.merge(particle(object.getAsJsonObject("particle").get("type").getAsString()),
                        object.get("probability").getAsDouble(), Double::sum);
                }
                attributes.ambientParticles(chances);
            } else if (EnvironmentAttributes.KNOWN_26_2.contains(path)) {
                attributes.set(path, entry.getValue());
            }
            // 26.3-only attributes (natural_mob_spawns, creature_world_gen_spawn_probability)
            // are mapped to 26.2 biome fields by the caller.
        }
        return attributes;
    }

    /** Custom particles cannot be shown on unmodified clients; use the closest vanilla particle. */
    static String particle(String id) {
        return switch (id) {
            case "the_sift:sift_note" -> "minecraft:note";
            case "the_sift:soul_fragment", "the_sift:canyon_soul" -> "minecraft:soul";
            case "the_sift:ichor_bubble", "the_sift:ichor_splash", "the_sift:ichor_rain_splash" -> "minecraft:bubble_pop";
            default -> id.startsWith("minecraft:") ? id : "minecraft:white_ash";
        };
    }

    private void applySpawns(BiomeDefinition.Builder builder, JsonObject spawns) {
        JsonObject categories = spawns.getAsJsonObject("spawns_by_category");
        if (categories != null) {
            for (Map.Entry<String, JsonElement> category : categories.entrySet()) {
                MobCategory mobCategory = MobCategory.valueOf(category.getKey().toUpperCase(Locale.ROOT));
                for (JsonElement element : category.getValue().getAsJsonArray()) {
                    JsonObject spawn = element.getAsJsonObject();
                    String type = spawn.get("type").getAsString();
                    String mapped = SiftEntities.spawnBacking(type);
                    if (mapped == null) {
                        droppedSpawns.add(type);
                        continue;
                    }
                    int min;
                    int max;
                    JsonElement count = spawn.get("count");
                    if (count.isJsonObject()) {
                        min = count.getAsJsonObject().get("min_inclusive").getAsInt();
                        max = count.getAsJsonObject().get("max_inclusive").getAsInt();
                    } else {
                        min = count.getAsInt();
                        max = min;
                    }
                    builder.spawn(mobCategory, new SpawnEntry(NamespacedId.parse(mapped), spawn.get("weight").getAsInt(), min, max));
                }
            }
        }
        JsonObject costs = spawns.getAsJsonObject("spawn_costs");
        if (costs != null) {
            for (Map.Entry<String, JsonElement> cost : costs.entrySet()) {
                JsonObject value = cost.getValue().getAsJsonObject();
                builder.spawnCost(NamespacedId.parse(cost.getKey()), value.get("charge").getAsDouble(),
                    value.get("energy_budget").getAsDouble());
            }
        }
    }

    // --- carvers ------------------------------------------------------------------------

    private CarverDefinition carver(String name) {
        return carvers.computeIfAbsent(name, key -> {
            JsonObject json = data.json("worldgen/carver/" + key + ".json");
            String type = json.get("type").getAsString();
            NamespacedId id = NamespacedId.of(SiftBlocks.NS, key);
            JsonObject config = new JsonObject();
            config.addProperty("probability", json.get("probability").getAsFloat());
            config.add("y", json.get("y").deepCopy());
            config.add("lava_level", Json.object().put("above_bottom", 0).build());
            config.addProperty("replaceable", "#" + SiftBlocks.NS + ":carver_replaceables");
            if (type.equals("minecraft:cave")) {
                config.add("yScale", json.get("room_vertical_radius_multiplier").deepCopy());
                config.add("horizontal_radius_multiplier", json.get("horizontal_radius_multiplier").deepCopy());
                config.add("vertical_radius_multiplier", json.get("vertical_radius_multiplier").deepCopy());
                config.add("floor_level", json.get("floor_level").deepCopy());
            } else if (type.equals("minecraft:canyon")) {
                JsonObject shape = json.getAsJsonObject("shape").deepCopy();
                JsonElement yScale = shape.remove("y_scale");
                config.add("shape", shape);
                config.add("vertical_rotation", json.get("vertical_rotation").deepCopy());
                config.add("yScale", yScale == null ? Json.of(3.0) : yScale);
            } else {
                throw new IllegalArgumentException("Unsupported carver type " + type + " in " + key);
            }
            return new CarverDefinition(id, type, config);
        });
    }

    // --- features -----------------------------------------------------------------------

    private PlacedFeatureDefinition placedFeature(String placedId, Step step, NamespacedId biome) {
        CustomPlacement custom = customPlaced.get(placedId);
        if (custom != null) {
            custom.biomes().add(biome);
            return null;
        }
        PlacedFeatureDefinition existing = nativePlaced.get(placedId);
        if (existing != null) {
            return existing;
        }
        JsonObject placed = data.json("worldgen/placed_feature/" + placedId + ".json");
        String featureId = NamespacedId.parse(placed.get("feature").getAsString()).path();
        JsonObject feature = data.json("worldgen/feature/" + featureId + ".json");
        String type = feature.get("type").getAsString();
        if (type.startsWith(SiftBlocks.NS + ":")) {
            Set<NamespacedId> biomes = new LinkedHashSet<>();
            biomes.add(biome);
            customPlaced.put(placedId, new CustomPlacement(placedId, type, step, biomes, placed.getAsJsonArray("placement")));
            return null;
        }
        JsonObject config = SiftDataTranslator.featureConfig(feature, "feature/" + featureId, this::modBlockTag);
        FeatureDefinition configured = new FeatureDefinition(NamespacedId.of(SiftBlocks.NS, featureId), type, config);
        List<JsonObject> placement = new ArrayList<>();
        int index = 0;
        for (JsonElement modifier : placed.getAsJsonArray("placement")) {
            placement.add(SiftDataTranslator.placement(modifier.getAsJsonObject(),
                "placed_feature/" + placedId + "[" + index++ + "]", this::modBlockTag));
        }
        PlacedFeatureDefinition definition = configured.placed(NamespacedId.of(SiftBlocks.NS, placedId), placement);
        nativePlaced.put(placedId, definition);
        return definition;
    }

    /** Members of a mod block tag (mod ids), loaded once. */
    private List<String> modBlockTag(String tag) {
        return modBlockTags.computeIfAbsent(tag, key -> {
            NamespacedId id = NamespacedId.parse(key);
            JsonObject json = data.json("tags/block/" + id.path() + ".json");
            List<String> members = new ArrayList<>();
            json.getAsJsonArray("values").forEach(value -> members.add(value.getAsString()));
            return members;
        });
    }

    /** Java-implemented feature placements discovered while building biomes. */
    public List<CustomPlacement> customPlacements() {
        return List.copyOf(customPlaced.values());
    }

    // --- extra datapack resources --------------------------------------------------------

    /** Block tags, processor lists and vegetation-support tags the native features need. */
    public void contribute(DatapackBuilder datapack) {
        for (String tag : List.of("hanging_roots_ceiling_blocks", "overgrown_willow_logs", "sift_plant_support",
            "sift_surface_blob_replaceables", "siftslate_ore_replaceables", "siftslate_ores")) {
            JsonObject json = data.json("tags/block/" + tag + ".json");
            datapack.tag("block", NamespacedId.of(SiftBlocks.NS, tag), SiftDataTranslator.blockTagValues(json, "tags/block/" + tag));
        }
        datapack.tag("block", NamespacedId.of(SiftBlocks.NS, "carver_replaceables"), List.of(
            "minecraft:brown_mushroom_block", "minecraft:red_mushroom_block", "minecraft:snow", "minecraft:water"));
        JsonObject processors = data.json("worldgen/processor_list/fossil_siftslate_coal.json");
        datapack.json(DatapackBuilder.Registry.PROCESSOR_LIST, NamespacedId.of(SiftBlocks.NS, "fossil_siftslate_coal"),
            SiftDataTranslator.processorList(processors, "processor_list/fossil_siftslate_coal"));
        // Let vanilla stand-in plants survive on the custom soils/stone (mushroom blocks).
        List<String> mushrooms = List.of("minecraft:brown_mushroom_block", "minecraft:red_mushroom_block");
        for (String support : List.of("supports_vegetation", "supports_crimson_roots", "supports_dry_vegetation")) {
            datapack.tag("block", NamespacedId.minecraft(support), mushrooms);
        }
        // Sniffer nests plant on willow foliage, a mushroom stem state.
        datapack.tag("block", NamespacedId.minecraft("supports_vegetation"), List.of("minecraft:mushroom_stem"));
        // Sculkflower stand-ins (torchflower crop, warped fungus) and Dark Sniffer digging; these tags are
        // global, so crops and fungi also stand on vanilla sculk and huge red mushroom blocks elsewhere,
        // and every sniffer digs sculk (and gets infected) in any world.
        List<String> sculkflowerSoils = List.of("minecraft:sculk", "minecraft:red_mushroom_block");
        datapack.tag("block", NamespacedId.minecraft("supports_crops"), sculkflowerSoils);
        datapack.tag("block", NamespacedId.minecraft("supports_warped_fungus"), sculkflowerSoils);
        datapack.tag("block", NamespacedId.minecraft("supports_warped_fungus"), List.of("minecraft:farmland"));
        datapack.tag("block", NamespacedId.minecraft("sniffer_diggable_block"), List.of("minecraft:sculk"));
        // Blubs are backed by wolves (see SiftEntities); let them spawn on Sift ground.
        datapack.tag("block", NamespacedId.minecraft("wolves_spawnable_on"), mushrooms);
    }

    static int color(String hex) {
        return Integer.parseInt(hex.substring(1), 16) & 0xFFFFFF;
    }

    static BlockStateSpec state(String id) {
        return SiftDataTranslator.blockState(Json.of(id), id);
    }

    static DensityFunction zero() {
        return DensityFunctions.ZERO;
    }
}
