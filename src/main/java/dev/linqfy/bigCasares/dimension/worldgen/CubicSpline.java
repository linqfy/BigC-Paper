package dev.linqfy.bigCasares.dimension.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.linqfy.bigCasares.dimension.json.Json;
import dev.linqfy.bigCasares.dimension.json.JsonSerializable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Vanilla cubic spline used by {@code minecraft:spline} density functions.
 * Points are Hermite segments; values may be nested splines on another coordinate.
 */
public sealed interface CubicSpline extends JsonSerializable permits CubicSpline.Fixed, CubicSpline.Multipoint {

    static CubicSpline constant(float value) {
        return new Fixed(value);
    }

    static Builder on(DensityFunction coordinate) {
        return new Builder(coordinate);
    }

    record Fixed(float value) implements CubicSpline {
        public Fixed {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("Spline value must be finite");
            }
        }

        @Override
        public JsonElement toJson() {
            return new JsonPrimitive(value);
        }
    }

    record Point(float location, CubicSpline value, float derivative) {
        public Point {
            Objects.requireNonNull(value, "value");
            if (!Float.isFinite(location) || !Float.isFinite(derivative)) {
                throw new IllegalArgumentException("Spline point must be finite");
            }
        }
    }

    record Multipoint(DensityFunction coordinate, List<Point> points) implements CubicSpline {
        public Multipoint {
            Objects.requireNonNull(coordinate, "coordinate");
            points = List.copyOf(points);
            if (points.isEmpty()) {
                throw new IllegalArgumentException("Spline needs at least one point");
            }
            for (int index = 1; index < points.size(); index++) {
                if (!(points.get(index - 1).location() < points.get(index).location())) {
                    throw new IllegalArgumentException("Spline point locations must be strictly increasing");
                }
            }
        }

        @Override
        public JsonElement toJson() {
            JsonArray array = new JsonArray();
            for (Point point : points) {
                array.add(Json.object()
                    .put("derivative", point.derivative())
                    .put("location", point.location())
                    .put("value", point.value())
                    .build());
            }
            return Json.object().put("coordinate", coordinate).put("points", array).build();
        }
    }

    final class Builder {
        private final DensityFunction coordinate;
        private final List<Point> points = new ArrayList<>();

        private Builder(DensityFunction coordinate) {
            this.coordinate = Objects.requireNonNull(coordinate, "coordinate");
        }

        public Builder point(float location, float value) {
            return point(location, value, 0.0F);
        }

        public Builder point(float location, float value, float derivative) {
            points.add(new Point(location, new Fixed(value), derivative));
            return this;
        }

        public Builder point(float location, CubicSpline value) {
            points.add(new Point(location, value, 0.0F));
            return this;
        }

        public Multipoint build() {
            return new Multipoint(coordinate, points);
        }
    }
}
