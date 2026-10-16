package dev.linqfy.bigCasares.dimension.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.dimension.json.Json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * A custom biome ({@code worldgen/biome}) with its multi-noise placement.
 *
 * <p>Biomes are registered by id; the client receives them in the registry sync,
 * so colours, fog, particles and music render on unmodified clients. The
 * {@link #placements()} decide where the biome appears in its dimension.</p>
 */
public final class BiomeDefinition {

    /** The 11 decoration steps of {@code GenerationStep.Decoration}, in order. */
    public enum Step {
        RAW_GENERATION, LAKES, LOCAL_MODIFICATIONS, UNDERGROUND_STRUCTURES, SURFACE_STRUCTURES,
        STRONGHOLDS, UNDERGROUND_ORES, UNDERGROUND_DECORATION, FLUID_SPRINGS, VEGETAL_DECORATION,
        TOP_LAYER_MODIFICATION
    }

    /** Mob categories as written in {@code spawners}. */
    public enum MobCategory {
        AMBIENT, AXOLOTLS, CREATURE, MISC, MONSTER, UNDERGROUND_WATER_CREATURE, WATER_AMBIENT, WATER_CREATURE;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public record SpawnEntry(NamespacedId entityType, int weight, int minCount, int maxCount) {
        public SpawnEntry {
            Objects.requireNonNull(entityType, "entityType");
            if (weight < 0 || minCount < 1 || maxCount < minCount) {
                throw new IllegalArgumentException("Invalid spawn entry for " + entityType);
            }
        }

        JsonObject toJson() {
            return Json.object()
                .put("type", entityType)
                .put("maxCount", maxCount)
                .put("minCount", minCount)
                .put("weight", weight)
                .build();
        }
    }

    private final NamespacedId id;
    private final float temperature;
    private final float downfall;
    private final boolean hasPrecipitation;
    private final EnvironmentAttributes attributes;
    private final Integer waterColor;
    private final Integer grassColor;
    private final Integer foliageColor;
    private final Integer dryFoliageColor;
    private final Map<MobCategory, List<SpawnEntry>> spawners;
    private final Map<NamespacedId, double[]> spawnCosts;
    private final Float creatureSpawnProbability;
    private final List<CarverDefinition> carvers;
    private final Map<Step, List<PlacedFeatureDefinition>> features;
    private final Map<Step, List<NamespacedId>> featureOrder;
    private final List<Climate.ParameterPoint> placements;

    private BiomeDefinition(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id");
        this.temperature = builder.temperature;
        this.downfall = builder.downfall;
        this.hasPrecipitation = builder.hasPrecipitation;
        this.attributes = builder.attributes;
        this.waterColor = Objects.requireNonNull(builder.waterColor, "waterColor (required by 26.2 biome effects)");
        this.grassColor = builder.grassColor;
        this.foliageColor = builder.foliageColor;
        this.dryFoliageColor = builder.dryFoliageColor;
        EnumMap<MobCategory, List<SpawnEntry>> spawnCopy = new EnumMap<>(MobCategory.class);
        builder.spawners.forEach((category, entries) -> spawnCopy.put(category, List.copyOf(entries)));
        this.spawners = Collections.unmodifiableMap(spawnCopy);
        this.spawnCosts = Collections.unmodifiableMap(new java.util.TreeMap<>(builder.spawnCosts));
        this.creatureSpawnProbability = builder.creatureSpawnProbability;
        if (creatureSpawnProbability != null && (creatureSpawnProbability < 0.0F || creatureSpawnProbability > 0.9999999F)) {
            throw new IllegalArgumentException("creature_spawn_probability must be within [0, 1)");
        }
        this.carvers = List.copyOf(builder.carvers);
        EnumMap<Step, List<PlacedFeatureDefinition>> featureCopy = new EnumMap<>(Step.class);
        builder.features.forEach((step, entries) -> featureCopy.put(step, List.copyOf(entries)));
        this.features = Collections.unmodifiableMap(featureCopy);
        EnumMap<Step, List<NamespacedId>> orderCopy = new EnumMap<>(Step.class);
        builder.featureOrder.forEach((step, entries) -> orderCopy.put(step, List.copyOf(entries)));
        this.featureOrder = Collections.unmodifiableMap(orderCopy);
        this.placements = List.copyOf(builder.placements);
        if (temperature < -2.0F || temperature > 2.0F || downfall < 0.0F || downfall > 1.0F) {
            throw new IllegalArgumentException("Biome " + id + " has out-of-range temperature/downfall");
        }
    }

    public static Builder builder(NamespacedId id) {
        return new Builder(id);
    }

    public NamespacedId id() {
        return id;
    }

    public List<Climate.ParameterPoint> placements() {
        return placements;
    }

    public List<CarverDefinition> carvers() {
        return carvers;
    }

    /** Placed features this biome defines (and the datapack emits), per step. */
    public Map<Step, List<PlacedFeatureDefinition>> features() {
        return features;
    }

    /**
     * Every placed feature id per step, in decoration order, including references
     * to registry entries this pack does not define (for example vanilla
     * {@code minecraft:sculk_vein}).
     */
    public Map<Step, List<NamespacedId>> featureOrder() {
        return featureOrder;
    }

    public Map<MobCategory, List<SpawnEntry>> spawners() {
        return spawners;
    }

    public Integer waterColor() {
        return waterColor;
    }

    public JsonObject toJson() {
        Json.ObjectBuilder effects = Json.object()
            .putIfPresent("dry_foliage_color", dryFoliageColor == null ? null : Json.rgb(dryFoliageColor))
            .putIfPresent("foliage_color", foliageColor == null ? null : Json.rgb(foliageColor))
            .putIfPresent("grass_color", grassColor == null ? null : Json.rgb(grassColor))
            .put("water_color", Json.rgb(waterColor));

        JsonArray featureSteps = new JsonArray();
        for (Step step : Step.values()) {
            JsonArray entries = new JsonArray();
            for (NamespacedId feature : featureOrder.getOrDefault(step, List.of())) {
                entries.add(feature.asString());
            }
            featureSteps.add(entries);
        }

        JsonObject spawnerObject = new JsonObject();
        for (MobCategory category : MobCategory.values()) {
            JsonArray entries = new JsonArray();
            for (SpawnEntry entry : spawners.getOrDefault(category, List.of())) {
                entries.add(entry.toJson());
            }
            spawnerObject.add(category.id(), entries);
        }

        JsonObject costs = new JsonObject();
        spawnCosts.forEach((entity, cost) -> costs.add(entity.asString(), Json.object()
            .put("charge", cost[0])
            .put("energy_budget", cost[1])
            .build()));

        Json.ObjectBuilder builder = Json.object()
            .put("attributes", attributes)
            .put("carvers", carvers.stream().map(CarverDefinition::id).toList());
        if (creatureSpawnProbability != null) {
            builder.put("creature_spawn_probability", creatureSpawnProbability);
        }
        return builder
            .put("downfall", downfall)
            .put("effects", effects.build())
            .put("features", featureSteps)
            .put("has_precipitation", hasPrecipitation)
            .put("spawn_costs", costs)
            .put("spawners", spawnerObject)
            .put("temperature", temperature)
            .build();
    }

    public static final class Builder {
        private final NamespacedId id;
        private float temperature = 0.5F;
        private float downfall = 0.5F;
        private boolean hasPrecipitation;
        private EnvironmentAttributes attributes = EnvironmentAttributes.create();
        private Integer waterColor;
        private Integer grassColor;
        private Integer foliageColor;
        private Integer dryFoliageColor;
        private final Map<MobCategory, List<SpawnEntry>> spawners = new EnumMap<>(MobCategory.class);
        private final Map<NamespacedId, double[]> spawnCosts = new LinkedHashMap<>();
        private Float creatureSpawnProbability;
        private final List<CarverDefinition> carvers = new ArrayList<>();
        private final Map<Step, List<PlacedFeatureDefinition>> features = new LinkedHashMap<>();
        private final Map<Step, List<NamespacedId>> featureOrder = new LinkedHashMap<>();
        private final List<Climate.ParameterPoint> placements = new ArrayList<>();

        private Builder(NamespacedId id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public Builder climate(float temperature, float downfall, boolean hasPrecipitation) {
            this.temperature = temperature;
            this.downfall = downfall;
            this.hasPrecipitation = hasPrecipitation;
            return this;
        }

        public Builder attributes(EnvironmentAttributes attributes) {
            this.attributes = Objects.requireNonNull(attributes, "attributes");
            return this;
        }

        public Builder waterColor(int rgb) { this.waterColor = rgb; return this; }
        public Builder grassColor(int rgb) { this.grassColor = rgb; return this; }
        public Builder foliageColor(int rgb) { this.foliageColor = rgb; return this; }
        public Builder dryFoliageColor(int rgb) { this.dryFoliageColor = rgb; return this; }

        public Builder spawn(MobCategory category, SpawnEntry entry) {
            spawners.computeIfAbsent(category, ignored -> new ArrayList<>()).add(entry);
            return this;
        }

        /** Spawn cost (vanilla "charge"/"energy_budget" spawn-density limiter) for an entity type. */
        public Builder spawnCost(NamespacedId entityType, double charge, double energyBudget) {
            spawnCosts.put(java.util.Objects.requireNonNull(entityType, "entityType"), new double[]{charge, energyBudget});
            return this;
        }

        /** Chance that a creature pack is placed during chunk generation. */
        public Builder creatureSpawnProbability(float probability) {
            this.creatureSpawnProbability = probability;
            return this;
        }

        public Builder carver(CarverDefinition carver) {
            carvers.add(Objects.requireNonNull(carver, "carver"));
            return this;
        }

        public Builder feature(Step step, PlacedFeatureDefinition feature) {
            features.computeIfAbsent(step, ignored -> new ArrayList<>()).add(Objects.requireNonNull(feature, "feature"));
            featureOrder.computeIfAbsent(step, ignored -> new ArrayList<>()).add(feature.id());
            return this;
        }

        /**
         * References a placed feature that already exists in the server registry
         * (vanilla or another pack). The datapack does not emit it, so a missing
         * id fails the server's registry load rather than this compiler.
         */
        public Builder existingFeature(Step step, NamespacedId placedFeature) {
            featureOrder.computeIfAbsent(step, ignored -> new ArrayList<>())
                .add(Objects.requireNonNull(placedFeature, "placedFeature"));
            return this;
        }

        public Builder placement(Climate.ParameterPoint point) {
            placements.add(Objects.requireNonNull(point, "point"));
            return this;
        }

        public BiomeDefinition build() {
            return new BiomeDefinition(this);
        }
    }
}
