package dev.linqfy.bigCasares.dimension.worldgen;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.dimension.json.Json;
import dev.linqfy.bigCasares.dimension.json.JsonSerializable;

import java.util.Locale;
import java.util.Objects;

/**
 * Typed model of vanilla density functions ({@code worldgen/density_function}).
 *
 * <p>The framework compiles these to datapack JSON, so terrain is evaluated by
 * Minecraft's own multithreaded noise engine. Only the function types available
 * in Minecraft 26.2 are modelled; use {@link DensityFunctions} to build trees.</p>
 */
public sealed interface DensityFunction extends JsonSerializable permits
    DensityFunction.Constant,
    DensityFunction.Reference,
    DensityFunction.Noise,
    DensityFunction.ShiftedNoise,
    DensityFunction.Unary,
    DensityFunction.Binary,
    DensityFunction.Clamp,
    DensityFunction.YClampedGradient,
    DensityFunction.RangeChoice,
    DensityFunction.Spline,
    DensityFunction.Beardifier,
    DensityFunction.FindTopSurface {

    /** Vanilla bounds every constant/clamp argument to this range. */
    double MAX_ABS_VALUE = 1_000_000.0;

    static String type(String name) {
        return "minecraft:" + name;
    }

    record Constant(double value) implements DensityFunction {
        public Constant {
            if (!Double.isFinite(value) || Math.abs(value) > MAX_ABS_VALUE) {
                throw new IllegalArgumentException("Density constant out of range: " + value);
            }
        }

        @Override
        public JsonElement toJson() {
            return new JsonPrimitive(value);
        }
    }

    /** Reference to a density function registered under {@code worldgen/density_function}. */
    record Reference(NamespacedId id) implements DensityFunction {
        public Reference {
            Objects.requireNonNull(id, "id");
        }

        @Override
        public JsonElement toJson() {
            return new JsonPrimitive(id.asString());
        }
    }

    record Noise(NamespacedId noise, double xzScale, double yScale) implements DensityFunction {
        public Noise {
            Objects.requireNonNull(noise, "noise");
            requireScale(xzScale);
            requireScale(yScale);
        }

        @Override
        public JsonElement toJson() {
            return Json.object()
                .put("type", type("noise"))
                .put("noise", noise)
                .put("xz_scale", xzScale)
                .put("y_scale", yScale)
                .build();
        }
    }

    record ShiftedNoise(
        NamespacedId noise,
        double xzScale,
        double yScale,
        DensityFunction shiftX,
        DensityFunction shiftY,
        DensityFunction shiftZ
    ) implements DensityFunction {
        public ShiftedNoise {
            Objects.requireNonNull(noise, "noise");
            requireScale(xzScale);
            requireScale(yScale);
            Objects.requireNonNull(shiftX, "shiftX");
            Objects.requireNonNull(shiftY, "shiftY");
            Objects.requireNonNull(shiftZ, "shiftZ");
        }

        @Override
        public JsonElement toJson() {
            return Json.object()
                .put("type", type("shifted_noise"))
                .put("noise", noise)
                .put("shift_x", shiftX)
                .put("shift_y", shiftY)
                .put("shift_z", shiftZ)
                .put("xz_scale", xzScale)
                .put("y_scale", yScale)
                .build();
        }
    }

    enum UnaryOp {
        ABS, SQUARE, CUBE, HALF_NEGATIVE, QUARTER_NEGATIVE, SQUEEZE,
        INTERPOLATED, FLAT_CACHE, CACHE_2D, CACHE_ONCE, CACHE_ALL_IN_CELL, BLEND_DENSITY, INVERT;

        public String id() {
            return type(name().toLowerCase(Locale.ROOT));
        }

        /** Markers only change caching/interpolation, not the mathematical value. */
        public boolean isMarker() {
            return this == INTERPOLATED || this == FLAT_CACHE || this == CACHE_2D
                || this == CACHE_ONCE || this == CACHE_ALL_IN_CELL || this == BLEND_DENSITY;
        }
    }

    record Unary(UnaryOp op, DensityFunction argument) implements DensityFunction {
        public Unary {
            Objects.requireNonNull(op, "op");
            Objects.requireNonNull(argument, "argument");
        }

        @Override
        public JsonElement toJson() {
            return Json.object().put("type", op.id()).put("argument", argument).build();
        }
    }

    enum BinaryOp {
        ADD, MUL, MIN, MAX;

        public String id() {
            return type(name().toLowerCase(Locale.ROOT));
        }
    }

    record Binary(BinaryOp op, DensityFunction argument1, DensityFunction argument2) implements DensityFunction {
        public Binary {
            Objects.requireNonNull(op, "op");
            Objects.requireNonNull(argument1, "argument1");
            Objects.requireNonNull(argument2, "argument2");
        }

        @Override
        public JsonElement toJson() {
            return Json.object()
                .put("type", op.id())
                .put("argument1", argument1)
                .put("argument2", argument2)
                .build();
        }
    }

    record Clamp(DensityFunction input, double min, double max) implements DensityFunction {
        public Clamp {
            Objects.requireNonNull(input, "input");
            if (!(min <= max) || Math.abs(min) > MAX_ABS_VALUE || Math.abs(max) > MAX_ABS_VALUE) {
                throw new IllegalArgumentException("Invalid clamp range: " + min + ".." + max);
            }
        }

        @Override
        public JsonElement toJson() {
            return Json.object()
                .put("type", type("clamp"))
                .put("input", input)
                .put("min", min)
                .put("max", max)
                .build();
        }
    }

    record YClampedGradient(int fromY, int toY, double fromValue, double toValue) implements DensityFunction {
        public YClampedGradient {
            if (fromY == toY) {
                throw new IllegalArgumentException("Gradient needs distinct y values");
            }
        }

        @Override
        public JsonElement toJson() {
            return Json.object()
                .put("type", type("y_clamped_gradient"))
                .put("from_y", fromY)
                .put("to_y", toY)
                .put("from_value", fromValue)
                .put("to_value", toValue)
                .build();
        }
    }

    record RangeChoice(
        DensityFunction input,
        double minInclusive,
        double maxExclusive,
        DensityFunction whenInRange,
        DensityFunction whenOutOfRange
    ) implements DensityFunction {
        public RangeChoice {
            Objects.requireNonNull(input, "input");
            Objects.requireNonNull(whenInRange, "whenInRange");
            Objects.requireNonNull(whenOutOfRange, "whenOutOfRange");
            if (!(minInclusive < maxExclusive)) {
                throw new IllegalArgumentException("Invalid range choice bounds");
            }
        }

        @Override
        public JsonElement toJson() {
            return Json.object()
                .put("type", type("range_choice"))
                .put("input", input)
                .put("min_inclusive", minInclusive)
                .put("max_exclusive", maxExclusive)
                .put("when_in_range", whenInRange)
                .put("when_out_of_range", whenOutOfRange)
                .build();
        }
    }

    record Spline(CubicSpline spline) implements DensityFunction {
        public Spline {
            Objects.requireNonNull(spline, "spline");
        }

        @Override
        public JsonElement toJson() {
            return Json.object().put("type", type("spline")).put("spline", spline).build();
        }
    }

    /** Terrain adaptation around structures (bury/beard); evaluates to zero without structures. */
    record Beardifier() implements DensityFunction {
        @Override
        public JsonElement toJson() {
            return Json.object().put("type", type("beardifier")).build();
        }
    }

    /** 26.x replacement for {@code initial_density_without_jaggedness}: scans downward for the surface. */
    record FindTopSurface(DensityFunction density, DensityFunction upperBound, int lowerBound, int cellHeight)
        implements DensityFunction {
        public FindTopSurface {
            Objects.requireNonNull(density, "density");
            Objects.requireNonNull(upperBound, "upperBound");
            if (cellHeight <= 0) {
                throw new IllegalArgumentException("cellHeight must be positive");
            }
        }

        @Override
        public JsonElement toJson() {
            return Json.object()
                .put("type", type("find_top_surface"))
                .put("cell_height", cellHeight)
                .put("density", density)
                .put("lower_bound", lowerBound)
                .put("upper_bound", upperBound)
                .build();
        }
    }

    private static void requireScale(double scale) {
        if (!Double.isFinite(scale) || Math.abs(scale) > MAX_ABS_VALUE) {
            throw new IllegalArgumentException("Invalid noise scale: " + scale);
        }
    }
}
