package dev.linqfy.bigCasares.dimension.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.dimension.json.Json;
import dev.linqfy.bigCasares.dimension.json.JsonSerializable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Compiled terrain for one dimension: becomes a {@code worldgen/noise_settings}
 * entry plus any named density functions it references.
 *
 * @param namedFunctions density functions published under
 *     {@code worldgen/density_function}; reference them with {@link DensityFunctions#ref}
 *     to share expensive sub-trees between the router and surface logic
 */
public record TerrainSpec(
    int minY,
    int height,
    int sizeHorizontal,
    int sizeVertical,
    int seaLevel,
    BlockStateSpec defaultBlock,
    BlockStateSpec defaultFluid,
    boolean aquifersEnabled,
    boolean oreVeinsEnabled,
    boolean disableMobGeneration,
    NoiseRouter router,
    SurfaceRule surfaceRule,
    List<Climate.ParameterPoint> spawnTarget,
    Map<NamespacedId, DensityFunction> namedFunctions
) implements JsonSerializable {

    public TerrainSpec {
        if (minY % 16 != 0 || height % 16 != 0 || height <= 0) {
            throw new IllegalArgumentException("Terrain minY/height must be multiples of 16");
        }
        if (minY < -2032 || minY + height > 2032) {
            throw new IllegalArgumentException("Terrain exceeds vanilla build limits");
        }
        if (sizeHorizontal < 1 || sizeHorizontal > 4 || sizeVertical < 1 || sizeVertical > 4) {
            throw new IllegalArgumentException("Noise cell size must be within [1, 4]");
        }
        Objects.requireNonNull(defaultBlock, "defaultBlock");
        Objects.requireNonNull(defaultFluid, "defaultFluid");
        Objects.requireNonNull(router, "router");
        Objects.requireNonNull(surfaceRule, "surfaceRule");
        spawnTarget = List.copyOf(spawnTarget);
        namedFunctions = Map.copyOf(namedFunctions);
    }

    @Override
    public JsonElement toJson() {
        JsonArray spawn = new JsonArray();
        spawnTarget.forEach(point -> spawn.add(point.toJson()));
        return Json.object()
            .put("aquifers_enabled", aquifersEnabled)
            .put("default_block", defaultBlock)
            .put("default_fluid", defaultFluid)
            .put("disable_mob_generation", disableMobGeneration)
            .put("legacy_random_source", false)
            .put("noise", Json.object()
                .put("height", height)
                .put("min_y", minY)
                .put("size_horizontal", sizeHorizontal)
                .put("size_vertical", sizeVertical)
                .build())
            .put("noise_router", router)
            .put("ore_veins_enabled", oreVeinsEnabled)
            .put("sea_level", seaLevel)
            .put("spawn_target", spawn)
            .put("surface_rule", surfaceRule)
            .build();
    }
}
