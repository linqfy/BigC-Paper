package dev.linqfy.bigCasares.dimension;

import dev.linqfy.bigCasares.dimension.environment.EnvironmentCycle;
import dev.linqfy.bigCasares.dimension.travel.CoordinateMapping;
import dev.linqfy.bigCasares.dimension.travel.PortalDefinition;
import dev.linqfy.bigCasares.dimension.travel.TravelRules;
import dev.linqfy.bigCasares.dimension.worldgen.BiomeDefinition;
import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;
import dev.linqfy.bigCasares.dimension.worldgen.Climate;
import dev.linqfy.bigCasares.dimension.worldgen.DensityFunction;
import dev.linqfy.bigCasares.dimension.worldgen.DensityFunctions;
import dev.linqfy.bigCasares.dimension.worldgen.NoiseRouter;
import dev.linqfy.bigCasares.dimension.worldgen.SurfaceRule;
import dev.linqfy.bigCasares.dimension.worldgen.SurfaceRules;
import dev.linqfy.bigCasares.dimension.worldgen.TerrainSpec;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A small, complete dimension built only with the framework API: the worked
 * example of {@code docs/dimensions/README.md}. Tests compile it so the example
 * cannot drift from the API.
 */
public final class ExampleDimension {

    public static final DimensionKey KEY = DimensionKey.of("example", "basalt_sea");

    private ExampleDimension() {
    }

    public static DimensionDefinition create(long seedSalt) {
        return DimensionDefinition.builder(KEY)
            .displayName("Basalt Sea")
            .seedSalt(seedSalt)
            .type(DimensionTypeSpec.builder()
                .heightRange(0, 128)
                .logicalHeight(128)
                .skylight(true)
                .build())
            .terrain(context -> {
                // Rolling hills: density falls with height, offset by a salted noise.
                var hills = context.defineNoise("hills", -6, 1.0, 1.0, 0.5);
                DensityFunction density = DensityFunctions.add(
                    DensityFunctions.yGradient(32, 96, 1.0, -1.0),
                    DensityFunctions.mul(context.noise(hills, 1.0, 0.0), 0.45));
                NoiseRouter router = NoiseRouter.builder()
                    .finalDensity(DensityFunctions.interpolated(density))
                    .build();
                SurfaceRule surface = new SurfaceRule.Sequence(List.of(
                    new SurfaceRule.Conditional(SurfaceRules.onFloor(0),
                        new SurfaceRule.Block(BlockStateSpec.of("minecraft:basalt", Map.of("axis", "y")))),
                    new SurfaceRule.Block(BlockStateSpec.of("minecraft:blackstone"))));
                return new TerrainSpec(0, 128, 1, 2, 48,
                    BlockStateSpec.of("minecraft:blackstone"),
                    BlockStateSpec.of("minecraft:water", Map.of("level", "0")),
                    false, false, false, router, surface, List.of(), Map.of());
            })
            .biomes(context -> List.of(BiomeDefinition.builder(context.id("ash_shore"))
                .climate(0.9F, 0.0F, false)
                .waterColor(0x3F76E4)
                .placement(Climate.ParameterPoint.builder().build())
                .build()))
            .cycle(EnvironmentCycle.builder("embers")
                .phase("calm", 12_000)
                .phase("flare", 2_000)
                .build())
            .travel(new TravelRules(CoordinateMapping.IDENTITY, "", Duration.ofSeconds(5), true, 32, List.of(
                new PortalDefinition("basalt_gate", Set.of("minecraft:polished_basalt"), "minecraft:fire_charge", true,
                    2, 8, 3, 8, true, 9, null, "minecraft:ash"))))
            .build();
    }
}
