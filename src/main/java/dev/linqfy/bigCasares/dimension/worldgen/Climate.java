package dev.linqfy.bigCasares.dimension.worldgen;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.linqfy.bigCasares.dimension.json.Json;
import dev.linqfy.bigCasares.dimension.json.JsonSerializable;

/**
 * Multi-noise climate parameters. Minecraft picks, per quart position, the biome
 * whose parameter box is closest to the sampled climate (squared distance, with
 * zero distance inside an interval). Values are in the router's [-1, 1]-ish range.
 */
public final class Climate {

    private Climate() {
    }

    /** A closed interval; a point is an interval with {@code min == max}. */
    public record Range(float min, float max) implements JsonSerializable {
        public Range {
            if (!Float.isFinite(min) || !Float.isFinite(max) || min > max) {
                throw new IllegalArgumentException("Invalid climate range " + min + ".." + max);
            }
            if (min < -2.0F || max > 2.0F) {
                throw new IllegalArgumentException("Climate range must stay within [-2, 2]: " + min + ".." + max);
            }
        }

        public static Range point(float value) {
            return new Range(value, value);
        }

        public static Range span(float min, float max) {
            return new Range(min, max);
        }

        public static Range any() {
            return new Range(-1.0F, 1.0F);
        }

        /** Distance used by vanilla's nearest-parameter search (0 inside the interval). */
        public double distance(double value) {
            if (value < min) {
                return min - value;
            }
            return value > max ? value - max : 0.0;
        }

        @Override
        public JsonElement toJson() {
            return min == max ? new JsonPrimitive(min) : Json.array(min, max);
        }
    }

    /**
     * A biome's target point. {@code depth} is 0 at the surface and grows underground,
     * which is how vertical (cave) biomes are expressed. {@code offset} biases the
     * fitness so a biome only wins when it is clearly closest.
     */
    public record ParameterPoint(
        Range temperature,
        Range humidity,
        Range continentalness,
        Range erosion,
        Range depth,
        Range weirdness,
        float offset
    ) implements JsonSerializable {
        public ParameterPoint {
            java.util.Objects.requireNonNull(temperature, "temperature");
            java.util.Objects.requireNonNull(humidity, "humidity");
            java.util.Objects.requireNonNull(continentalness, "continentalness");
            java.util.Objects.requireNonNull(erosion, "erosion");
            java.util.Objects.requireNonNull(depth, "depth");
            java.util.Objects.requireNonNull(weirdness, "weirdness");
            if (offset < 0.0F || offset > 1.0F) {
                throw new IllegalArgumentException("Climate offset must be within [0, 1]");
            }
        }

        public static Builder builder() {
            return new Builder();
        }

        /** Squared fitness distance as computed by vanilla (lower wins). */
        public double fitness(double t, double h, double c, double e, double d, double w) {
            double sum = sq(temperature.distance(t)) + sq(humidity.distance(h)) + sq(continentalness.distance(c))
                + sq(erosion.distance(e)) + sq(depth.distance(d)) + sq(weirdness.distance(w)) + sq(offset);
            return sum;
        }

        private static double sq(double value) {
            return value * value;
        }

        @Override
        public JsonElement toJson() {
            return Json.object()
                .put("continentalness", continentalness)
                .put("depth", depth)
                .put("erosion", erosion)
                .put("humidity", humidity)
                .put("offset", offset)
                .put("temperature", temperature)
                .put("weirdness", weirdness)
                .build();
        }

        public static final class Builder {
            private Range temperature = Range.any();
            private Range humidity = Range.any();
            private Range continentalness = Range.any();
            private Range erosion = Range.any();
            private Range depth = Range.point(0.0F);
            private Range weirdness = Range.any();
            private float offset;

            private Builder() {
            }

            public Builder temperature(float min, float max) { temperature = Range.span(min, max); return this; }
            public Builder humidity(float min, float max) { humidity = Range.span(min, max); return this; }
            public Builder continentalness(float min, float max) { continentalness = Range.span(min, max); return this; }
            public Builder erosion(float min, float max) { erosion = Range.span(min, max); return this; }
            public Builder depth(float min, float max) { depth = Range.span(min, max); return this; }
            public Builder weirdness(float min, float max) { weirdness = Range.span(min, max); return this; }
            public Builder offset(float value) { offset = value; return this; }

            public ParameterPoint build() {
                return new ParameterPoint(temperature, humidity, continentalness, erosion, depth, weirdness, offset);
            }
        }
    }
}
