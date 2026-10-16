package dev.linqfy.bigCasares.dimension.datapack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.linqfy.bigCasares.dimension.DimensionDefinition;
import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.dimension.datapack.DatapackBuilder.Registry;
import dev.linqfy.bigCasares.dimension.environment.EnvironmentCycle;
import dev.linqfy.bigCasares.dimension.json.Json;
import dev.linqfy.bigCasares.dimension.worldgen.BiomeDefinition;
import dev.linqfy.bigCasares.dimension.worldgen.CarverDefinition;
import dev.linqfy.bigCasares.dimension.worldgen.Climate;
import dev.linqfy.bigCasares.dimension.worldgen.DensityFunction;
import dev.linqfy.bigCasares.dimension.worldgen.FeatureDefinition;
import dev.linqfy.bigCasares.dimension.worldgen.GenerationContext;
import dev.linqfy.bigCasares.dimension.worldgen.NoiseDefinition;
import dev.linqfy.bigCasares.dimension.worldgen.PlacedFeatureDefinition;
import dev.linqfy.bigCasares.dimension.worldgen.StructureDefinition;
import dev.linqfy.bigCasares.dimension.worldgen.SurfaceRule;
import dev.linqfy.bigCasares.dimension.worldgen.TerrainSpec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compiles dimension definitions into one Minecraft 26.2 datapack.
 *
 * <p>The compiler walks every definition transitively (biome &rarr; placed feature
 * &rarr; configured feature, structure &rarr; pool &rarr; template), then checks that
 * every id referenced by density functions, surface rules and placements resolves
 * either to an emitted file or to the {@code minecraft} namespace. A broken
 * reference fails here instead of preventing the server from starting.</p>
 */
public final class DatapackCompiler {

    public static final String DESCRIPTION = "BigCasares custom dimensions (generated; do not edit)";

    /** Result of compiling one or more definitions. */
    public record Result(GeneratedDatapack datapack, Map<NamespacedId, CompiledDimension> dimensions) {
    }

    /** Per-dimension summary useful for diagnostics and tests. */
    public record CompiledDimension(
        NamespacedId key,
        TerrainSpec terrain,
        List<BiomeDefinition> biomes,
        List<StructureDefinition> structures,
        List<NoiseDefinition> noises
    ) {
    }

    public Result compile(Collection<DimensionDefinition> definitions) {
        DatapackBuilder builder = new DatapackBuilder();
        Map<NamespacedId, CompiledDimension> compiled = new LinkedHashMap<>();
        References references = new References();
        Set<NamespacedId> seenKeys = new LinkedHashSet<>();
        for (DimensionDefinition definition : definitions) {
            if (!seenKeys.add(definition.key().id())) {
                throw new IllegalArgumentException("Duplicate dimension key: " + definition.key());
            }
            compiled.put(definition.key().id(), compileOne(definition, builder, references));
        }
        GeneratedDatapack datapack = builder.build(DESCRIPTION);
        references.verify(datapack);
        return new Result(datapack, compiled);
    }

    private CompiledDimension compileOne(DimensionDefinition definition, DatapackBuilder out, References refs) {
        NamespacedId key = definition.key().id();
        GenerationContext context = new GenerationContext(definition.key(), definition.seedSalt());
        TerrainSpec terrain = definition.terrain().create(context);
        List<BiomeDefinition> biomes = List.copyOf(definition.biomes().create(context));
        List<StructureDefinition> structures = List.copyOf(definition.structures().create(context));
        if (biomes.isEmpty()) {
            throw new IllegalArgumentException("Dimension " + key + " defines no biomes");
        }
        if (terrain.minY() != definition.type().minY() || terrain.height() != definition.type().height()) {
            throw new IllegalArgumentException("Terrain height range of " + key + " must match its dimension type");
        }

        // Terrain: noises, named density functions, noise settings.
        for (NoiseDefinition noise : context.noises()) {
            out.json(Registry.NOISE, noise.id(), noise.toJson());
        }
        terrain.namedFunctions().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                out.json(Registry.DENSITY_FUNCTION, entry.getKey(), entry.getValue().toJson());
                refs.densityFunction(entry.getValue());
            });
        refs.densityFunction(terrain.router().finalDensity());
        refs.noiseRouter(terrain);
        refs.surfaceRule(terrain.surfaceRule());
        out.json(Registry.NOISE_SETTINGS, key, terrain.toJson());

        // Biomes and their decoration graph.
        JsonArray biomeSource = new JsonArray();
        Set<NamespacedId> biomeIds = new LinkedHashSet<>();
        for (BiomeDefinition biome : biomes) {
            if (!biomeIds.add(biome.id())) {
                throw new IllegalArgumentException("Duplicate biome in " + key + ": " + biome.id());
            }
            if (biome.placements().isEmpty()) {
                throw new IllegalArgumentException("Biome " + biome.id() + " has no climate placement");
            }
            out.json(Registry.BIOME, biome.id(), biome.toJson());
            for (CarverDefinition carver : biome.carvers()) {
                out.json(Registry.CONFIGURED_CARVER, carver.id(), carver.toJson());
            }
            biome.features().values().forEach(list -> list.forEach(feature -> emitPlaced(feature, out, refs)));
            for (Climate.ParameterPoint point : biome.placements()) {
                biomeSource.add(Json.object().put("biome", biome.id()).put("parameters", point).build());
            }
        }
        refs.declareBiomes(biomeIds);

        // Structures.
        for (StructureDefinition structure : structures) {
            out.json(Registry.STRUCTURE, structure.id(), structure.toJson());
            out.json(Registry.STRUCTURE_SET, structure.id(), structure.structureSetJson());
            out.tag("worldgen/biome", structure.biomeTag(),
                structure.biomes().stream().map(NamespacedId::asString).toList());
            refs.biomes(structure.biomes());
            List<StructureDefinition.TemplatePool> pools = new ArrayList<>();
            pools.add(structure.startPool());
            pools.addAll(structure.extraPools());
            for (StructureDefinition.TemplatePool pool : pools) {
                out.json(Registry.TEMPLATE_POOL, pool.id(), pool.toJson());
                for (StructureDefinition.PoolElement element : pool.elements()) {
                    out.structureTemplate(element.template().id(), element.template().toBytes());
                    if (element.processors() != null) {
                        out.json(Registry.PROCESSOR_LIST, element.processors().id(), element.processors().toJson());
                    }
                }
            }
        }

        // Dimension type, clock, timelines.
        out.json(Registry.DIMENSION_TYPE, key, definition.type().toJson(definition.clockId(), definition.timelineTagId()));
        if (definition.type().ownClock()) {
            out.json(Registry.WORLD_CLOCK, definition.clockId(), new JsonObject());
            List<String> timelines = new ArrayList<>();
            timelines.add("#minecraft:universal");
            for (EnvironmentCycle cycle : definition.cycles()) {
                NamespacedId timeline = definition.timelineId(cycle);
                out.json(Registry.TIMELINE, timeline, cycle.timelineJson(definition.clockId()));
                timelines.add(timeline.asString());
            }
            out.tag("timeline", definition.timelineTagId(), timelines);
        }

        // Level stem.
        JsonObject stem = Json.object()
            .put("type", key)
            .put("generator", Json.object()
                .put("type", "minecraft:noise")
                .put("biome_source", Json.object()
                    .put("type", "minecraft:multi_noise")
                    .put("biomes", biomeSource)
                    .build())
                .put("settings", key)
                .build())
            .build();
        out.json(Registry.DIMENSION, key, stem);

        for (DatapackContributor contributor : definition.contributors()) {
            contributor.contribute(context, out);
        }
        return new CompiledDimension(key, terrain, biomes, structures, context.noises());
    }

    private static void emitPlaced(PlacedFeatureDefinition placed, DatapackBuilder out, References refs) {
        out.json(Registry.PLACED_FEATURE, placed.id(), placed.toJson());
        FeatureDefinition feature = placed.feature();
        out.json(Registry.CONFIGURED_FEATURE, feature.id(), feature.toJson());
        for (PlacedFeatureDefinition dependency : feature.dependencies()) {
            emitPlaced(dependency, out, refs);
        }
    }

    /** Tracks ids that must exist in the final pack. */
    private static final class References {
        private final Set<NamespacedId> noises = new LinkedHashSet<>();
        private final Set<NamespacedId> densityFunctions = new LinkedHashSet<>();
        private final Set<NamespacedId> biomes = new LinkedHashSet<>();
        private final Set<NamespacedId> declaredBiomes = new LinkedHashSet<>();

        void declareBiomes(Collection<NamespacedId> ids) {
            declaredBiomes.addAll(ids);
        }

        void biomes(Collection<NamespacedId> ids) {
            biomes.addAll(ids);
        }

        void noiseRouter(TerrainSpec terrain) {
            var router = terrain.router();
            for (DensityFunction function : List.of(router.barrier(), router.fluidLevelFloodedness(),
                router.fluidLevelSpread(), router.lava(), router.temperature(), router.vegetation(),
                router.continents(), router.erosion(), router.depth(), router.ridges(),
                router.preliminarySurfaceLevel(), router.veinToggle(), router.veinRidged(), router.veinGap())) {
                densityFunction(function);
            }
        }

        void densityFunction(DensityFunction function) {
            switch (function) {
                case DensityFunction.Constant ignored -> { }
                case DensityFunction.Reference reference -> densityFunctions.add(reference.id());
                case DensityFunction.Noise noise -> noises.add(noise.noise());
                case DensityFunction.ShiftedNoise noise -> {
                    noises.add(noise.noise());
                    densityFunction(noise.shiftX());
                    densityFunction(noise.shiftY());
                    densityFunction(noise.shiftZ());
                }
                case DensityFunction.Unary unary -> densityFunction(unary.argument());
                case DensityFunction.Binary binary -> {
                    densityFunction(binary.argument1());
                    densityFunction(binary.argument2());
                }
                case DensityFunction.Clamp clamp -> densityFunction(clamp.input());
                case DensityFunction.YClampedGradient ignored -> { }
                case DensityFunction.RangeChoice choice -> {
                    densityFunction(choice.input());
                    densityFunction(choice.whenInRange());
                    densityFunction(choice.whenOutOfRange());
                }
                case DensityFunction.Spline spline -> spline(spline.spline());
                case DensityFunction.Beardifier ignored -> { }
                case DensityFunction.FindTopSurface surface -> {
                    densityFunction(surface.density());
                    densityFunction(surface.upperBound());
                }
            }
        }

        private void spline(dev.linqfy.bigCasares.dimension.worldgen.CubicSpline spline) {
            if (spline instanceof dev.linqfy.bigCasares.dimension.worldgen.CubicSpline.Multipoint multipoint) {
                densityFunction(multipoint.coordinate());
                multipoint.points().forEach(point -> spline(point.value()));
            }
        }

        void surfaceRule(SurfaceRule rule) {
            switch (rule) {
                case SurfaceRule.Block ignored -> { }
                case SurfaceRule.Bandlands ignored -> { }
                case SurfaceRule.Sequence sequence -> sequence.rules().forEach(this::surfaceRule);
                case SurfaceRule.Conditional conditional -> {
                    surfaceCondition(conditional.condition());
                    surfaceRule(conditional.then());
                }
            }
        }

        private void surfaceCondition(SurfaceRule.SurfaceCondition condition) {
            if (condition instanceof SurfaceRule.BiomeIs biomeIs) {
                biomes.addAll(biomeIs.biomes());
            } else if (condition instanceof SurfaceRule.NoiseThreshold threshold) {
                noises.add(threshold.noise());
            } else if (condition instanceof SurfaceRule.Not not) {
                surfaceCondition(not.invert());
            }
        }

        void verify(GeneratedDatapack datapack) {
            List<String> missing = new ArrayList<>();
            for (NamespacedId noise : noises) {
                if (!isVanilla(noise) && datapack.file(path(Registry.NOISE, noise)) == null) {
                    missing.add("noise " + noise);
                }
            }
            for (NamespacedId function : densityFunctions) {
                if (!isVanilla(function) && datapack.file(path(Registry.DENSITY_FUNCTION, function)) == null) {
                    missing.add("density function " + function);
                }
            }
            for (NamespacedId biome : biomes) {
                if (!isVanilla(biome) && datapack.file(path(Registry.BIOME, biome)) == null) {
                    missing.add("biome " + biome);
                }
            }
            if (!missing.isEmpty()) {
                throw new IllegalArgumentException("Unresolved datapack references: " + missing);
            }
        }

        private static boolean isVanilla(NamespacedId id) {
            return "minecraft".equals(id.namespace());
        }

        private static String path(Registry registry, NamespacedId id) {
            return "data/" + id.namespace() + "/" + registry.folder() + "/" + id.path() + ".json";
        }
    }
}
