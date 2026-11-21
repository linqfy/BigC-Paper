package dev.linqfy.bigCasares.modules.staffevents;

import dev.linqfy.bigCasares.dimension.DimensionDefinition;
import dev.linqfy.bigCasares.dimension.DimensionKey;
import dev.linqfy.bigCasares.dimension.DimensionRules;
import dev.linqfy.bigCasares.dimension.DimensionTypeSpec;
import dev.linqfy.bigCasares.dimension.worldgen.BiomeDefinition;
import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;
import dev.linqfy.bigCasares.dimension.worldgen.Climate;
import dev.linqfy.bigCasares.dimension.worldgen.DensityFunctions;
import dev.linqfy.bigCasares.dimension.worldgen.NoiseRouter;
import dev.linqfy.bigCasares.dimension.worldgen.SurfaceRule;
import dev.linqfy.bigCasares.dimension.worldgen.TerrainSpec;

import java.util.List;
import java.util.Map;

/**
 * The event dimension: an empty void. Arenas are built in it by code each time an
 * event starts (which also sets the time to noon), and nothing generates or spawns on its own.
 */
public final class EventArenaDimension {

    public static final DimensionKey KEY = DimensionKey.of("bigcasares", "eventos");

    private EventArenaDimension() {
    }

    public static DimensionDefinition create() {
        return DimensionDefinition.builder(KEY)
            .displayName("Eventos")
            .type(DimensionTypeSpec.builder()
                .heightRange(0, 256)
                .logicalHeight(256)
                .skylight(true)
                .build())
            .terrain(context -> new TerrainSpec(0, 256, 1, 2, 0,
                BlockStateSpec.of("minecraft:air"),
                BlockStateSpec.of("minecraft:air"),
                false, false, true,
                NoiseRouter.builder().finalDensity(DensityFunctions.constant(-1.0)).build(),
                new SurfaceRule.Block(BlockStateSpec.of("minecraft:air")),
                List.of(), Map.of()))
            .biomes(context -> List.of(BiomeDefinition.builder(context.id("void"))
                .climate(0.8F, 0.0F, false)
                .waterColor(0x3F76E4)
                .placement(Climate.ParameterPoint.builder().build())
                .build()))
            .rules(DimensionRules.builder()
                .weatherCycle(false)
                .spawnSearchRadius(0)
                .build())
            .build();
    }
}
