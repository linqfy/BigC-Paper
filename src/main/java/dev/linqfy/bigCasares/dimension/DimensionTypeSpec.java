package dev.linqfy.bigCasares.dimension;

import com.google.gson.JsonObject;
import dev.linqfy.bigCasares.dimension.json.Json;
import dev.linqfy.bigCasares.dimension.worldgen.EnvironmentAttributes;

import java.util.Objects;

/**
 * A native {@code dimension_type}: world height, sky/ceiling, base lighting,
 * skybox, clock and the dimension-wide environment attributes.
 *
 * <p>Dimension types are synced to vanilla clients during configuration. They
 * are loaded only at server start; changes take effect after a restart.</p>
 */
public final class DimensionTypeSpec {

    public enum Skybox {
        OVERWORLD, END, NONE
    }

    private final int minY;
    private final int height;
    private final int logicalHeight;
    private final float ambientLight;
    private final double coordinateScale;
    private final boolean hasSkylight;
    private final boolean hasCeiling;
    private final boolean hasFixedTime;
    private final Skybox skybox;
    private final String infiniburn;
    private final int monsterSpawnBlockLightLimit;
    private final int monsterSpawnLightMin;
    private final int monsterSpawnLightMax;
    private final EnvironmentAttributes attributes;
    private final boolean ownClock;

    private DimensionTypeSpec(Builder builder) {
        this.minY = builder.minY;
        this.height = builder.height;
        this.logicalHeight = builder.logicalHeight == null ? builder.height : builder.logicalHeight;
        this.ambientLight = builder.ambientLight;
        this.coordinateScale = builder.coordinateScale;
        this.hasSkylight = builder.hasSkylight;
        this.hasCeiling = builder.hasCeiling;
        this.hasFixedTime = builder.hasFixedTime;
        this.skybox = builder.skybox;
        this.infiniburn = builder.infiniburn;
        this.monsterSpawnBlockLightLimit = builder.monsterSpawnBlockLightLimit;
        this.monsterSpawnLightMin = builder.monsterSpawnLightMin;
        this.monsterSpawnLightMax = builder.monsterSpawnLightMax;
        this.attributes = builder.attributes;
        this.ownClock = builder.ownClock;
        if (minY % 16 != 0 || height % 16 != 0 || height < 16 || minY < -2032 || minY + height > 2032) {
            throw new IllegalArgumentException("Dimension height must be a multiple of 16 within vanilla limits");
        }
        if (logicalHeight < 0 || logicalHeight > height) {
            throw new IllegalArgumentException("logical_height must be within [0, height]");
        }
        if (ambientLight < 0.0F || ambientLight > 1.0F) {
            throw new IllegalArgumentException("ambient_light must be within [0, 1]");
        }
        if (coordinateScale < 1.0E-5 || coordinateScale > 3.0E7) {
            throw new IllegalArgumentException("coordinate_scale out of range");
        }
        if (monsterSpawnLightMin < 0 || monsterSpawnLightMax > 15 || monsterSpawnLightMin > monsterSpawnLightMax
            || monsterSpawnBlockLightLimit < 0 || monsterSpawnBlockLightLimit > 15) {
            throw new IllegalArgumentException("Monster spawn light settings must be within [0, 15]");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public int minY() {
        return minY;
    }

    public int height() {
        return height;
    }

    public boolean ownClock() {
        return ownClock;
    }

    /**
     * Serializes the type. {@code clock} and {@code timelinesTag} are the dimension's
     * own world clock and timeline tag when {@link #ownClock()} is true.
     */
    public JsonObject toJson(NamespacedId clock, NamespacedId timelinesTag) {
        Json.ObjectBuilder builder = Json.object()
            .put("ambient_light", ambientLight)
            .put("attributes", attributes)
            .put("coordinate_scale", coordinateScale);
        if (ownClock) {
            builder.put("default_clock", Objects.requireNonNull(clock, "clock"));
        }
        builder.put("has_ceiling", hasCeiling)
            .put("has_ender_dragon_fight", false);
        if (hasFixedTime) {
            builder.put("has_fixed_time", true);
        }
        builder.put("has_skylight", hasSkylight)
            .put("height", height)
            .put("infiniburn", infiniburn)
            .put("logical_height", logicalHeight)
            .put("min_y", minY)
            .put("monster_spawn_block_light_limit", monsterSpawnBlockLightLimit);
        if (monsterSpawnLightMin == monsterSpawnLightMax) {
            builder.put("monster_spawn_light_level", monsterSpawnLightMin);
        } else {
            builder.put("monster_spawn_light_level", Json.object()
                .put("type", "minecraft:uniform")
                .put("max_inclusive", monsterSpawnLightMax)
                .put("min_inclusive", monsterSpawnLightMin)
                .build());
        }
        if (skybox != Skybox.OVERWORLD) {
            builder.put("skybox", skybox);
        }
        if (ownClock) {
            builder.put("timelines", "#" + Objects.requireNonNull(timelinesTag, "timelinesTag").asString());
        }
        return builder.build();
    }

    public static final class Builder {
        private int minY = 0;
        private int height = 256;
        private Integer logicalHeight;
        private float ambientLight;
        private double coordinateScale = 1.0;
        private boolean hasSkylight = true;
        private boolean hasCeiling;
        private boolean hasFixedTime;
        private Skybox skybox = Skybox.OVERWORLD;
        private String infiniburn = "#minecraft:infiniburn_overworld";
        private int monsterSpawnBlockLightLimit;
        private int monsterSpawnLightMin;
        private int monsterSpawnLightMax = 7;
        private EnvironmentAttributes attributes = EnvironmentAttributes.create();
        private boolean ownClock = true;

        private Builder() {
        }

        public Builder heightRange(int minY, int height) {
            this.minY = minY;
            this.height = height;
            return this;
        }

        public Builder logicalHeight(int logicalHeight) { this.logicalHeight = logicalHeight; return this; }
        public Builder ambientLight(float ambientLight) { this.ambientLight = ambientLight; return this; }
        public Builder coordinateScale(double coordinateScale) { this.coordinateScale = coordinateScale; return this; }
        public Builder skylight(boolean hasSkylight) { this.hasSkylight = hasSkylight; return this; }
        public Builder ceiling(boolean hasCeiling) { this.hasCeiling = hasCeiling; return this; }
        public Builder fixedTime(boolean hasFixedTime) { this.hasFixedTime = hasFixedTime; return this; }
        public Builder skybox(Skybox skybox) { this.skybox = Objects.requireNonNull(skybox, "skybox"); return this; }
        public Builder infiniburn(String blockTag) { this.infiniburn = Objects.requireNonNull(blockTag, "blockTag"); return this; }

        public Builder monsterSpawnLight(int min, int max, int blockLightLimit) {
            this.monsterSpawnLightMin = min;
            this.monsterSpawnLightMax = max;
            this.monsterSpawnBlockLightLimit = blockLightLimit;
            return this;
        }

        public Builder attributes(EnvironmentAttributes attributes) {
            this.attributes = Objects.requireNonNull(attributes, "attributes");
            return this;
        }

        /**
         * Whether the dimension gets its own world clock and timeline tag. Without one,
         * {@code World#getFullTime()} is 0 and timelines/environment cycles cannot run.
         */
        public Builder ownClock(boolean ownClock) { this.ownClock = ownClock; return this; }

        public DimensionTypeSpec build() {
            return new DimensionTypeSpec(this);
        }
    }
}
