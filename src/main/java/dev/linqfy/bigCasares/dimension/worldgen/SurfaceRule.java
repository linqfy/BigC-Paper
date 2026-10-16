package dev.linqfy.bigCasares.dimension.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.dimension.json.Json;
import dev.linqfy.bigCasares.dimension.json.JsonSerializable;

import java.util.List;
import java.util.Objects;

/**
 * Vanilla surface rules ({@code worldgen/material_rule}). Rules run top-down per
 * column after the noise pass and replace the default block; the first matching
 * branch of a {@link Sequence} wins.
 */
public sealed interface SurfaceRule extends JsonSerializable permits
    SurfaceRule.Block, SurfaceRule.Sequence, SurfaceRule.Conditional, SurfaceRule.Bandlands {

    static SurfaceRule block(BlockStateSpec state) {
        return new Block(state);
    }

    static SurfaceRule block(String state) {
        return new Block(BlockStateSpec.of(state));
    }

    static SurfaceRule sequence(SurfaceRule... rules) {
        return new Sequence(List.of(rules));
    }

    static SurfaceRule sequence(List<SurfaceRule> rules) {
        return new Sequence(rules);
    }

    static SurfaceRule when(SurfaceCondition condition, SurfaceRule then) {
        return new Conditional(condition, then);
    }

    record Block(BlockStateSpec state) implements SurfaceRule {
        public Block {
            Objects.requireNonNull(state, "state");
        }

        @Override
        public JsonElement toJson() {
            return Json.object().put("type", "minecraft:block").put("result_state", state).build();
        }
    }

    record Sequence(List<SurfaceRule> rules) implements SurfaceRule {
        public Sequence {
            rules = List.copyOf(rules);
            if (rules.isEmpty()) {
                throw new IllegalArgumentException("Surface sequence must not be empty");
            }
        }

        @Override
        public JsonElement toJson() {
            JsonArray array = new JsonArray();
            rules.forEach(rule -> array.add(rule.toJson()));
            return Json.object().put("type", "minecraft:sequence").put("sequence", array).build();
        }
    }

    record Conditional(SurfaceCondition condition, SurfaceRule then) implements SurfaceRule {
        public Conditional {
            Objects.requireNonNull(condition, "condition");
            Objects.requireNonNull(then, "then");
        }

        @Override
        public JsonElement toJson() {
            return Json.object()
                .put("type", "minecraft:condition")
                .put("if_true", condition)
                .put("then_run", then)
                .build();
        }
    }

    /** Vanilla badlands terracotta banding. */
    record Bandlands() implements SurfaceRule {
        @Override
        public JsonElement toJson() {
            return Json.object().put("type", "minecraft:bandlands").build();
        }
    }

    /** Conditions ({@code worldgen/material_condition}). */
    sealed interface SurfaceCondition extends JsonSerializable permits
        BiomeIs, NoiseThreshold, VerticalGradient, YAbove, Water, StoneDepth,
        Not, Hole, Steep, AbovePreliminarySurface, Temperature {
    }

    record BiomeIs(List<NamespacedId> biomes) implements SurfaceCondition {
        public BiomeIs {
            biomes = List.copyOf(biomes);
            if (biomes.isEmpty()) {
                throw new IllegalArgumentException("biome condition needs at least one biome");
            }
        }

        @Override
        public JsonElement toJson() {
            return Json.object().put("type", "minecraft:biome").put("biome_is", biomes).build();
        }
    }

    record NoiseThreshold(NamespacedId noise, double minThreshold, double maxThreshold) implements SurfaceCondition {
        public NoiseThreshold {
            Objects.requireNonNull(noise, "noise");
            if (minThreshold > maxThreshold) {
                throw new IllegalArgumentException("noise threshold min > max");
            }
        }

        @Override
        public JsonElement toJson() {
            return Json.object()
                .put("type", "minecraft:noise_threshold")
                .put("noise", noise)
                .put("min_threshold", minThreshold)
                .put("max_threshold", maxThreshold)
                .build();
        }
    }

    record VerticalGradient(String randomName, VerticalAnchor trueAtAndBelow, VerticalAnchor falseAtAndAbove)
        implements SurfaceCondition {
        public VerticalGradient {
            Objects.requireNonNull(randomName, "randomName");
            Objects.requireNonNull(trueAtAndBelow, "trueAtAndBelow");
            Objects.requireNonNull(falseAtAndAbove, "falseAtAndAbove");
        }

        @Override
        public JsonElement toJson() {
            return Json.object()
                .put("type", "minecraft:vertical_gradient")
                .put("random_name", randomName)
                .put("true_at_and_below", trueAtAndBelow)
                .put("false_at_and_above", falseAtAndAbove)
                .build();
        }
    }

    record YAbove(VerticalAnchor anchor, int surfaceDepthMultiplier, boolean addStoneDepth) implements SurfaceCondition {
        public YAbove {
            Objects.requireNonNull(anchor, "anchor");
            if (surfaceDepthMultiplier < -20 || surfaceDepthMultiplier > 20) {
                throw new IllegalArgumentException("surface_depth_multiplier must be within [-20, 20]");
            }
        }

        @Override
        public JsonElement toJson() {
            return Json.object()
                .put("type", "minecraft:y_above")
                .put("add_stone_depth", addStoneDepth)
                .put("anchor", anchor)
                .put("surface_depth_multiplier", surfaceDepthMultiplier)
                .build();
        }
    }

    record Water(int offset, int surfaceDepthMultiplier, boolean addStoneDepth) implements SurfaceCondition {
        public Water {
            if (surfaceDepthMultiplier < -20 || surfaceDepthMultiplier > 20) {
                throw new IllegalArgumentException("surface_depth_multiplier must be within [-20, 20]");
            }
        }

        @Override
        public JsonElement toJson() {
            return Json.object()
                .put("type", "minecraft:water")
                .put("add_stone_depth", addStoneDepth)
                .put("offset", offset)
                .put("surface_depth_multiplier", surfaceDepthMultiplier)
                .build();
        }
    }

    enum CaveSurface {
        FLOOR, CEILING
    }

    record StoneDepth(int offset, boolean addSurfaceDepth, int secondaryDepthRange, CaveSurface surfaceType)
        implements SurfaceCondition {
        public StoneDepth {
            Objects.requireNonNull(surfaceType, "surfaceType");
        }

        @Override
        public JsonElement toJson() {
            return Json.object()
                .put("type", "minecraft:stone_depth")
                .put("add_surface_depth", addSurfaceDepth)
                .put("offset", offset)
                .put("secondary_depth_range", secondaryDepthRange)
                .put("surface_type", surfaceType)
                .build();
        }
    }

    record Not(SurfaceCondition invert) implements SurfaceCondition {
        public Not {
            Objects.requireNonNull(invert, "invert");
        }

        @Override
        public JsonElement toJson() {
            return Json.object().put("type", "minecraft:not").put("invert", invert).build();
        }
    }

    record Hole() implements SurfaceCondition {
        @Override
        public JsonElement toJson() {
            return Json.object().put("type", "minecraft:hole").build();
        }
    }

    record Steep() implements SurfaceCondition {
        @Override
        public JsonElement toJson() {
            return Json.object().put("type", "minecraft:steep").build();
        }
    }

    record AbovePreliminarySurface() implements SurfaceCondition {
        @Override
        public JsonElement toJson() {
            return Json.object().put("type", "minecraft:above_preliminary_surface").build();
        }
    }

    record Temperature() implements SurfaceCondition {
        @Override
        public JsonElement toJson() {
            return Json.object().put("type", "minecraft:temperature").build();
        }
    }
}
