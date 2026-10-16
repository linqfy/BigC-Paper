package dev.linqfy.bigCasares.dimension;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.linqfy.bigCasares.dimension.datapack.DatapackCompiler;
import dev.linqfy.bigCasares.dimension.datapack.DatapackInstaller;
import dev.linqfy.bigCasares.dimension.datapack.GeneratedDatapack;
import dev.linqfy.bigCasares.dimension.worldgen.DensityFunctions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatapackCompilationTest {

    @TempDir
    Path temp;

    private static GeneratedDatapack compile(DimensionDefinition... definitions) {
        return new DatapackCompiler().compile(List.of(definitions)).datapack();
    }

    @Test
    void theDocumentedExampleCompilesToEveryRegistryFile() {
        GeneratedDatapack pack = compile(ExampleDimension.create(0L));
        assertNotNull(pack.file("pack.mcmeta"));
        assertNotNull(pack.file("data/example/dimension/basalt_sea.json"));
        assertNotNull(pack.file("data/example/dimension_type/basalt_sea.json"));
        assertNotNull(pack.file("data/example/worldgen/noise_settings/basalt_sea.json"));
        assertNotNull(pack.file("data/example/worldgen/biome/basalt_sea/ash_shore.json"));
        assertTrue(pack.paths().stream().anyMatch(path -> path.startsWith("data/example/worldgen/noise/")
            && path.endsWith("hills.json")), String.join("\n", pack.paths()));
        JsonObject dimension = JsonParser.parseString(pack.text("data/example/dimension/basalt_sea.json")).getAsJsonObject();
        assertEquals("example:basalt_sea", dimension.get("type").getAsString());
    }

    @Test
    void compilationIsDeterministic() {
        assertEquals(compile(ExampleDimension.create(7L)).contentHash(), compile(ExampleDimension.create(7L)).contentHash());
    }

    @Test
    void theSeedSaltChangesTheTerrainOnly() {
        GeneratedDatapack a = compile(ExampleDimension.create(1L));
        GeneratedDatapack b = compile(ExampleDimension.create(2L));
        assertNotEquals(a.text("data/example/worldgen/noise_settings/basalt_sea.json"),
            b.text("data/example/worldgen/noise_settings/basalt_sea.json"));
        assertEquals(a.text("data/example/worldgen/biome/basalt_sea/ash_shore.json"), b.text("data/example/worldgen/biome/basalt_sea/ash_shore.json"));
    }

    @Test
    void duplicateDimensionKeysAreRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> compile(ExampleDimension.create(0L), ExampleDimension.create(1L)));
    }

    @Test
    void unresolvedReferencesFailAtCompileTimeNotOnTheServer() {
        DimensionDefinition broken = DimensionDefinition.builder(DimensionKey.of("example", "broken"))
            .type(ExampleDimension.create(0L).type())
            .terrain(context -> {
                var terrain = ExampleDimension.create(0L).terrain().create(context);
                return new dev.linqfy.bigCasares.dimension.worldgen.TerrainSpec(terrain.minY(), terrain.height(),
                    terrain.sizeHorizontal(), terrain.sizeVertical(), terrain.seaLevel(), terrain.defaultBlock(),
                    terrain.defaultFluid(), false, false, false,
                    dev.linqfy.bigCasares.dimension.worldgen.NoiseRouter.builder()
                        .finalDensity(DensityFunctions.ref("example:missing_function")).build(),
                    terrain.surfaceRule(), List.of(), java.util.Map.of());
            })
            .biomes(ExampleDimension.create(0L).biomes())
            .build();
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> compile(broken));
        assertTrue(failure.getMessage().contains("example:missing_function"), failure.getMessage());
    }

    @Test
    void installerWritesOnceBacksUpOnChangeAndKeepsThreeBackups() throws IOException {
        Path datapacks = temp.resolve("world/datapacks");
        Path backups = temp.resolve("backups");
        Clock clock = Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC);
        DatapackInstaller installer = new DatapackInstaller(clock);

        GeneratedDatapack first = compile(ExampleDimension.create(0L));
        assertEquals(DatapackInstaller.Outcome.INSTALLED, installer.install(first, datapacks, "bigcasares", backups).outcome());
        assertEquals(DatapackInstaller.Outcome.UNCHANGED, installer.install(first, datapacks, "bigcasares", backups).outcome());
        assertEquals(first.contentHash(), DatapackInstaller.installedHash(datapacks.resolve("bigcasares")).orElseThrow());

        for (long salt = 1; salt <= 5; salt++) {
            DatapackInstaller later = new DatapackInstaller(Clock.offset(clock, java.time.Duration.ofSeconds(salt)));
            assertEquals(DatapackInstaller.Outcome.UPDATED,
                later.install(compile(ExampleDimension.create(salt)), datapacks, "bigcasares", backups).outcome());
        }
        try (Stream<Path> kept = Files.list(backups)) {
            assertEquals(3, kept.count());
        }
        assertThrows(IllegalArgumentException.class, () -> installer.install(first, datapacks, "../escape", backups));
    }
}
