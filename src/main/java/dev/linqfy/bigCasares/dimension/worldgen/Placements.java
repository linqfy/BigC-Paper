package dev.linqfy.bigCasares.dimension.worldgen;

import com.google.gson.JsonObject;
import dev.linqfy.bigCasares.dimension.json.Json;

/** Placement-modifier JSON for the 26.2 {@code placement_modifier_type} registry. */
public final class Placements {

    private Placements() {
    }

    public static JsonObject count(int count) {
        return Json.object().put("type", "minecraft:count").put("count", count).build();
    }

    public static JsonObject countUniform(int min, int max) {
        return Json.object().put("type", "minecraft:count").put("count", uniformInt(min, max)).build();
    }

    public static JsonObject inSquare() {
        return Json.object().put("type", "minecraft:in_square").build();
    }

    public static JsonObject biome() {
        return Json.object().put("type", "minecraft:biome").build();
    }

    public static JsonObject rarity(int chance) {
        return Json.object().put("type", "minecraft:rarity_filter").put("chance", chance).build();
    }

    /** Heightmap types: WORLD_SURFACE_WG, OCEAN_FLOOR_WG, MOTION_BLOCKING, ... */
    public static JsonObject heightmap(String heightmap) {
        return Json.object().put("type", "minecraft:heightmap").put("heightmap", heightmap).build();
    }

    public static JsonObject heightUniform(VerticalAnchor min, VerticalAnchor max) {
        return Json.object()
            .put("type", "minecraft:height_range")
            .put("height", Json.object()
                .put("type", "minecraft:uniform")
                .put("max_inclusive", max)
                .put("min_inclusive", min)
                .build())
            .build();
    }

    public static JsonObject heightTrapezoid(VerticalAnchor min, VerticalAnchor max) {
        return Json.object()
            .put("type", "minecraft:height_range")
            .put("height", Json.object()
                .put("type", "minecraft:trapezoid")
                .put("max_inclusive", max)
                .put("min_inclusive", min)
                .build())
            .build();
    }

    /** Scatter for patch-style vegetation (26.2 has no {@code random_patch}). */
    public static JsonObject randomOffset(int xzSpread, int ySpread) {
        return Json.object()
            .put("type", "minecraft:random_offset")
            .put("xz_spread", Json.object()
                .put("type", "minecraft:trapezoid").put("max", xzSpread).put("min", -xzSpread).put("plateau", 0).build())
            .put("y_spread", Json.object()
                .put("type", "minecraft:trapezoid").put("max", ySpread).put("min", -ySpread).put("plateau", 0).build())
            .build();
    }

    public static JsonObject blockPredicate(JsonObject predicate) {
        return Json.object().put("type", "minecraft:block_predicate_filter").put("predicate", predicate).build();
    }

    public static JsonObject onlyInAir() {
        return blockPredicate(Json.object().put("type", "minecraft:matching_block_tag").put("tag", "minecraft:air").build());
    }

    public static JsonObject uniformInt(int min, int max) {
        return Json.object().put("type", "minecraft:uniform").put("max_inclusive", max).put("min_inclusive", min).build();
    }
}
