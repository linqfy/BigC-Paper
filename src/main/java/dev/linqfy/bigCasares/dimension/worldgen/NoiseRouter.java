package dev.linqfy.bigCasares.dimension.worldgen;

import com.google.gson.JsonElement;
import dev.linqfy.bigCasares.dimension.json.Json;
import dev.linqfy.bigCasares.dimension.json.JsonSerializable;

import java.util.Objects;

/**
 * The fifteen density functions Minecraft 26.2 reads from {@code noise_settings.noise_router}.
 *
 * <p>{@code finalDensity > 0} is solid. The six climate fields (temperature,
 * vegetation, continents, erosion, depth, ridges) feed the multi-noise biome
 * source, which is how terrain shape and biome choice stay coupled.
 * {@code preliminarySurfaceLevel} is used by surface rules and aquifers.</p>
 */
public record NoiseRouter(
    DensityFunction barrier,
    DensityFunction fluidLevelFloodedness,
    DensityFunction fluidLevelSpread,
    DensityFunction lava,
    DensityFunction temperature,
    DensityFunction vegetation,
    DensityFunction continents,
    DensityFunction erosion,
    DensityFunction depth,
    DensityFunction ridges,
    DensityFunction preliminarySurfaceLevel,
    DensityFunction finalDensity,
    DensityFunction veinToggle,
    DensityFunction veinRidged,
    DensityFunction veinGap
) implements JsonSerializable {

    public NoiseRouter {
        Objects.requireNonNull(barrier, "barrier");
        Objects.requireNonNull(fluidLevelFloodedness, "fluidLevelFloodedness");
        Objects.requireNonNull(fluidLevelSpread, "fluidLevelSpread");
        Objects.requireNonNull(lava, "lava");
        Objects.requireNonNull(temperature, "temperature");
        Objects.requireNonNull(vegetation, "vegetation");
        Objects.requireNonNull(continents, "continents");
        Objects.requireNonNull(erosion, "erosion");
        Objects.requireNonNull(depth, "depth");
        Objects.requireNonNull(ridges, "ridges");
        Objects.requireNonNull(preliminarySurfaceLevel, "preliminarySurfaceLevel");
        Objects.requireNonNull(finalDensity, "finalDensity");
        Objects.requireNonNull(veinToggle, "veinToggle");
        Objects.requireNonNull(veinRidged, "veinRidged");
        Objects.requireNonNull(veinGap, "veinGap");
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public JsonElement toJson() {
        return Json.object()
            .put("barrier", barrier)
            .put("continents", continents)
            .put("depth", depth)
            .put("erosion", erosion)
            .put("final_density", finalDensity)
            .put("fluid_level_floodedness", fluidLevelFloodedness)
            .put("fluid_level_spread", fluidLevelSpread)
            .put("lava", lava)
            .put("preliminary_surface_level", preliminarySurfaceLevel)
            .put("ridges", ridges)
            .put("temperature", temperature)
            .put("vegetation", vegetation)
            .put("vein_gap", veinGap)
            .put("vein_ridged", veinRidged)
            .put("vein_toggle", veinToggle)
            .build();
    }

    /** Unset fields default to the constant zero, matching vanilla's simple presets. */
    public static final class Builder {
        private DensityFunction barrier = DensityFunctions.ZERO;
        private DensityFunction fluidLevelFloodedness = DensityFunctions.ZERO;
        private DensityFunction fluidLevelSpread = DensityFunctions.ZERO;
        private DensityFunction lava = DensityFunctions.ZERO;
        private DensityFunction temperature = DensityFunctions.ZERO;
        private DensityFunction vegetation = DensityFunctions.ZERO;
        private DensityFunction continents = DensityFunctions.ZERO;
        private DensityFunction erosion = DensityFunctions.ZERO;
        private DensityFunction depth = DensityFunctions.ZERO;
        private DensityFunction ridges = DensityFunctions.ZERO;
        private DensityFunction preliminarySurfaceLevel = DensityFunctions.ZERO;
        private DensityFunction finalDensity;
        private DensityFunction veinToggle = DensityFunctions.ZERO;
        private DensityFunction veinRidged = DensityFunctions.ZERO;
        private DensityFunction veinGap = DensityFunctions.ZERO;

        private Builder() {
        }

        public Builder barrier(DensityFunction value) { this.barrier = value; return this; }
        public Builder fluidLevelFloodedness(DensityFunction value) { this.fluidLevelFloodedness = value; return this; }
        public Builder fluidLevelSpread(DensityFunction value) { this.fluidLevelSpread = value; return this; }
        public Builder lava(DensityFunction value) { this.lava = value; return this; }
        public Builder temperature(DensityFunction value) { this.temperature = value; return this; }
        public Builder vegetation(DensityFunction value) { this.vegetation = value; return this; }
        public Builder continents(DensityFunction value) { this.continents = value; return this; }
        public Builder erosion(DensityFunction value) { this.erosion = value; return this; }
        public Builder depth(DensityFunction value) { this.depth = value; return this; }
        public Builder ridges(DensityFunction value) { this.ridges = value; return this; }
        public Builder preliminarySurfaceLevel(DensityFunction value) { this.preliminarySurfaceLevel = value; return this; }
        public Builder finalDensity(DensityFunction value) { this.finalDensity = value; return this; }
        public Builder veinToggle(DensityFunction value) { this.veinToggle = value; return this; }
        public Builder veinRidged(DensityFunction value) { this.veinRidged = value; return this; }
        public Builder veinGap(DensityFunction value) { this.veinGap = value; return this; }

        public NoiseRouter build() {
            return new NoiseRouter(barrier, fluidLevelFloodedness, fluidLevelSpread, lava, temperature,
                vegetation, continents, erosion, depth, ridges, preliminarySurfaceLevel,
                Objects.requireNonNull(finalDensity, "finalDensity"), veinToggle, veinRidged, veinGap);
        }
    }
}
