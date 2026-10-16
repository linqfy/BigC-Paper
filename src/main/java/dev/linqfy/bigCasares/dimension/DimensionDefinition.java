package dev.linqfy.bigCasares.dimension;

import dev.linqfy.bigCasares.dimension.datapack.DatapackContributor;
import dev.linqfy.bigCasares.dimension.environment.EnvironmentCycle;
import dev.linqfy.bigCasares.dimension.runtime.DimensionExtension;
import dev.linqfy.bigCasares.dimension.travel.TravelRules;
import dev.linqfy.bigCasares.dimension.worldgen.BiomeDefinition;
import dev.linqfy.bigCasares.dimension.worldgen.GenerationContext;
import dev.linqfy.bigCasares.dimension.worldgen.StructureDefinition;
import dev.linqfy.bigCasares.dimension.worldgen.TerrainSpec;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Declarative description of a custom dimension.
 *
 * <p>A definition is immutable and Bukkit-free except for optional
 * {@link DimensionExtension runtime extensions}. Worldgen pieces are supplied by
 * small providers that receive a {@link GenerationContext}, so one definition can
 * be compiled for several instances with different seed salts.</p>
 *
 * <pre>{@code
 * DimensionDefinition sift = DimensionDefinition.builder(DimensionKey.of("the_sift", "the_sift"))
 *     .displayName("The Sift")
 *     .type(siftType)
 *     .terrain(SiftTerrain::create)
 *     .biomes(SiftBiomes::create)
 *     .structures(SiftStructures::create)
 *     .cycle(siftDayNight)
 *     .travel(siftTravel)
 *     .extension(siftRuntime)
 *     .build();
 * platform.register(sift);
 * }</pre>
 */
public final class DimensionDefinition {

    /** Produces the terrain (noise settings) for one instance. */
    @FunctionalInterface
    public interface TerrainProvider {
        TerrainSpec create(GenerationContext context);
    }

    /** Produces the biomes, each with its multi-noise placement. */
    @FunctionalInterface
    public interface BiomeProvider {
        List<BiomeDefinition> create(GenerationContext context);
    }

    /** Produces jigsaw structures restricted to this dimension's biomes. */
    @FunctionalInterface
    public interface StructureProvider {
        List<StructureDefinition> create(GenerationContext context);
    }

    private final DimensionKey key;
    private final String displayName;
    private final long seedSalt;
    private final DimensionTypeSpec type;
    private final TerrainProvider terrain;
    private final BiomeProvider biomes;
    private final StructureProvider structures;
    private final List<EnvironmentCycle> cycles;
    private final List<DatapackContributor> contributors;
    private final DimensionRules rules;
    private final TravelRules travel;
    private final List<DimensionExtension> extensions;

    private DimensionDefinition(Builder builder) {
        this.key = builder.key;
        this.displayName = builder.displayName == null ? builder.key.path() : builder.displayName;
        this.seedSalt = builder.seedSalt;
        this.type = Objects.requireNonNull(builder.type, "type");
        this.terrain = Objects.requireNonNull(builder.terrain, "terrain");
        this.biomes = Objects.requireNonNull(builder.biomes, "biomes");
        this.structures = builder.structures == null ? context -> List.of() : builder.structures;
        this.cycles = List.copyOf(builder.cycles);
        this.contributors = List.copyOf(builder.contributors);
        this.rules = builder.rules;
        this.travel = builder.travel;
        this.extensions = List.copyOf(builder.extensions);
        Set<String> cycleIds = new HashSet<>();
        for (EnvironmentCycle cycle : cycles) {
            if (!cycleIds.add(cycle.id())) {
                throw new IllegalArgumentException("Duplicate environment cycle id: " + cycle.id());
            }
        }
        if (!cycles.isEmpty() && !type.ownClock()) {
            throw new IllegalArgumentException("Environment cycles need a dimension type with its own clock");
        }
    }

    public static Builder builder(DimensionKey key) {
        return new Builder(key);
    }

    /** Copy with a different key and seed salt: an independent instance of the same design. */
    public DimensionDefinition instance(DimensionKey instanceKey, long instanceSeedSalt) {
        Builder builder = toBuilder(instanceKey);
        builder.seedSalt = instanceSeedSalt;
        return builder.build();
    }

    private Builder toBuilder(DimensionKey newKey) {
        Builder builder = new Builder(newKey);
        builder.displayName = displayName;
        builder.seedSalt = seedSalt;
        builder.type = type;
        builder.terrain = terrain;
        builder.biomes = biomes;
        builder.structures = structures;
        builder.cycles.addAll(cycles);
        builder.contributors.addAll(contributors);
        builder.rules = rules;
        builder.travel = travel;
        builder.extensions.addAll(extensions);
        return builder;
    }

    public DimensionKey key() { return key; }
    public String displayName() { return displayName; }
    public long seedSalt() { return seedSalt; }
    public DimensionTypeSpec type() { return type; }
    public TerrainProvider terrain() { return terrain; }
    public BiomeProvider biomes() { return biomes; }
    public StructureProvider structures() { return structures; }
    public List<EnvironmentCycle> cycles() { return cycles; }
    public List<DatapackContributor> contributors() { return contributors; }
    public DimensionRules rules() { return rules; }
    public TravelRules travel() { return travel; }
    public List<DimensionExtension> extensions() { return extensions; }

    /** Id of this dimension's world clock (one per dimension with {@code ownClock}). */
    public NamespacedId clockId() {
        return key.id();
    }

    /** Id of the timeline tag referenced by the dimension type. */
    public NamespacedId timelineTagId() {
        return NamespacedId.of(key.namespace(), "in_" + key.path());
    }

    public NamespacedId timelineId(EnvironmentCycle cycle) {
        return key.id().child(cycle.id());
    }

    public static final class Builder {
        private final DimensionKey key;
        private String displayName;
        private long seedSalt;
        private DimensionTypeSpec type;
        private TerrainProvider terrain;
        private BiomeProvider biomes;
        private StructureProvider structures;
        private final List<EnvironmentCycle> cycles = new ArrayList<>();
        private final List<DatapackContributor> contributors = new ArrayList<>();
        private DimensionRules rules = DimensionRules.DEFAULT;
        private TravelRules travel = TravelRules.DEFAULT;
        private final List<DimensionExtension> extensions = new ArrayList<>();

        private Builder(DimensionKey key) {
            this.key = Objects.requireNonNull(key, "key");
        }

        public Builder displayName(String displayName) { this.displayName = displayName; return this; }
        public Builder seedSalt(long seedSalt) { this.seedSalt = seedSalt; return this; }
        public Builder type(DimensionTypeSpec type) { this.type = type; return this; }
        public Builder terrain(TerrainProvider terrain) { this.terrain = terrain; return this; }
        public Builder biomes(BiomeProvider biomes) { this.biomes = biomes; return this; }
        public Builder structures(StructureProvider structures) { this.structures = structures; return this; }
        public Builder cycle(EnvironmentCycle cycle) { this.cycles.add(Objects.requireNonNull(cycle, "cycle")); return this; }
        public Builder contributor(DatapackContributor contributor) { this.contributors.add(Objects.requireNonNull(contributor, "contributor")); return this; }
        public Builder rules(DimensionRules rules) { this.rules = Objects.requireNonNull(rules, "rules"); return this; }
        public Builder travel(TravelRules travel) { this.travel = Objects.requireNonNull(travel, "travel"); return this; }
        public Builder extension(DimensionExtension extension) { this.extensions.add(Objects.requireNonNull(extension, "extension")); return this; }

        public DimensionDefinition build() {
            return new DimensionDefinition(this);
        }
    }
}
