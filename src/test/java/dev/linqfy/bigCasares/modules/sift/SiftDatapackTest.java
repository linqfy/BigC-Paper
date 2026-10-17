package dev.linqfy.bigCasares.modules.sift;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.linqfy.bigCasares.dimension.DimensionDefinition;
import dev.linqfy.bigCasares.dimension.datapack.DatapackCompiler;
import dev.linqfy.bigCasares.dimension.datapack.GeneratedDatapack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiftDatapackTest {

    private static DimensionDefinition sift(SiftSettings settings) {
        SiftWorldgen worldgen = new SiftWorldgen(SiftData.fromClasspath(), Logger.getLogger("test"));
        return SiftDimension.create(settings, worldgen, List.of());
    }

    private static GeneratedDatapack compile(DimensionDefinition definition) {
        return new DatapackCompiler().compile(List.of(definition)).datapack();
    }

    @Test
    void compilesTheImportedSiftIntoA262Datapack() {
        GeneratedDatapack pack = compile(sift(SiftSettings.defaults()));

        assertNotNull(pack.file("data/the_sift/dimension/the_sift.json"));
        assertNotNull(pack.file("data/the_sift/dimension_type/the_sift.json"));
        assertNotNull(pack.file("data/the_sift/worldgen/noise_settings/the_sift.json"));
        assertNotNull(pack.file("data/the_sift/world_clock/the_sift.json"));
        assertNotNull(pack.file("data/the_sift/timeline/the_sift/day_night.json"));
        assertNotNull(pack.file("data/the_sift/timeline/the_sift/tides.json"));
        for (String biome : SiftData.BIOMES) {
            assertNotNull(pack.file("data/the_sift/worldgen/biome/" + biome + ".json"), biome);
        }
        JsonObject settings = JsonParser.parseString(pack.text("data/the_sift/worldgen/noise_settings/the_sift.json")).getAsJsonObject();
        assertTrue(settings.has("surface_rule"), "26.2 uses surface_rule, not material_rule");
        assertFalse(pack.text("data/the_sift/worldgen/noise_settings/the_sift.json").contains("the_sift:siftslate\""),
            "mod block ids must be remapped to backing states");
    }

    @Test
    void everyJavaPlacedFeatureTypeHasAPort() {
        SiftWorldgen worldgen = new SiftWorldgen(SiftData.fromClasspath(), Logger.getLogger("test"));
        compile(SiftDimension.create(SiftSettings.defaults(), worldgen, List.of()));
        var features = dev.linqfy.bigCasares.modules.sift.feature.SiftFeatures.create(SiftData.fromClasspath());
        assertFalse(worldgen.customPlacements().isEmpty());
        for (SiftWorldgen.CustomPlacement placement : worldgen.customPlacements()) {
            assertTrue(features.find(placement.featureType()).isPresent(),
                placement.placedId() + " uses unported feature type " + placement.featureType());
        }
    }

    @Test
    void identicalInputsProduceIdenticalBytes() {
        assertEquals(compile(sift(SiftSettings.defaults())).contentHash(), compile(sift(SiftSettings.defaults())).contentHash());
    }

    @Test
    void seedSaltChangesTerrainButNotBiomes() {
        SiftSettings defaults = SiftSettings.defaults();
        SiftSettings salted = new SiftSettings(42L, defaults.worldBorderDiameter(), defaults.entryPermission(),
            defaults.travelCooldownSeconds(), defaults.craftedPortal(), defaults.ancientCityRitual(), defaults.tides(),
            defaults.ichor(), defaults.blubs(), defaults.rifts(), List.of());
        GeneratedDatapack base = compile(sift(defaults));
        GeneratedDatapack other = compile(sift(salted));
        assertNotEquals(base.contentHash(), other.contentHash());
        assertEquals(base.text("data/the_sift/worldgen/biome/overgrown_forest.json"),
            other.text("data/the_sift/worldgen/biome/overgrown_forest.json"));
    }
}
