package dev.linqfy.bigCasares.dimension;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Runtime rules applied to the dimension's world each time it activates:
 * game rules, world border and spawn search.
 *
 * <p>Game rules are given by their vanilla id (e.g. {@code water_source_conversion}
 * in 26.x); unknown ids are reported by the runtime instead of failing activation,
 * so a renamed rule does not take the dimension offline.</p>
 */
public record DimensionRules(
    Map<String, String> gameRules,
    double worldBorderDiameter,
    double worldBorderCenterX,
    double worldBorderCenterZ,
    int spawnSearchRadius,
    boolean allowWeatherCycle
) {

    public static final DimensionRules DEFAULT = builder().build();

    public DimensionRules {
        gameRules = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(gameRules, "gameRules")));
        if (!(worldBorderDiameter >= 1.0) || worldBorderDiameter > 5.9999968E7) {
            throw new IllegalArgumentException("World border diameter out of range: " + worldBorderDiameter);
        }
        if (spawnSearchRadius < 0 || spawnSearchRadius > 4096) {
            throw new IllegalArgumentException("Spawn search radius must be within [0, 4096]");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<String, String> gameRules = new LinkedHashMap<>();
        private double worldBorderDiameter = 5.9999968E7;
        private double worldBorderCenterX;
        private double worldBorderCenterZ;
        private int spawnSearchRadius = 256;
        private boolean allowWeatherCycle = true;

        private Builder() {
        }

        public Builder gameRule(String id, Object value) {
            gameRules.put(Objects.requireNonNull(id, "id"), String.valueOf(Objects.requireNonNull(value, "value")));
            return this;
        }

        public Builder worldBorder(double centerX, double centerZ, double diameter) {
            this.worldBorderCenterX = centerX;
            this.worldBorderCenterZ = centerZ;
            this.worldBorderDiameter = diameter;
            return this;
        }

        public Builder spawnSearchRadius(int radius) {
            this.spawnSearchRadius = radius;
            return this;
        }

        public Builder weatherCycle(boolean allowed) {
            this.allowWeatherCycle = allowed;
            return this;
        }

        public DimensionRules build() {
            return new DimensionRules(gameRules, worldBorderDiameter, worldBorderCenterX, worldBorderCenterZ,
                spawnSearchRadius, allowWeatherCycle);
        }
    }
}
