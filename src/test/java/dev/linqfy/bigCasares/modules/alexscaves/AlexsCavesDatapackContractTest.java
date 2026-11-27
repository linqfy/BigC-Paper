package dev.linqfy.bigCasares.modules.alexscaves;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the bundled datapack: a broken worldgen datapack can stop the server from loading its world. */
class AlexsCavesDatapackContractTest {
    private static final Path ROOT = Path.of("src/main/resources/alexscaves/datapack");

    @Test
    void manifestListsExactlyTheShippedFiles() throws IOException {
        Set<String> listed = Files.readAllLines(ROOT.resolve("files.txt"), StandardCharsets.UTF_8).stream()
            .map(String::trim)
            .filter(line -> !line.isEmpty() && !line.startsWith("#"))
            .collect(Collectors.toCollection(TreeSet::new));
        Set<String> onDisk;
        try (Stream<Path> walk = Files.walk(ROOT)) {
            onDisk = walk.filter(Files::isRegularFile)
                .map(path -> ROOT.relativize(path).toString().replace('\\', '/'))
                .filter(path -> !path.equals("files.txt"))
                .collect(Collectors.toCollection(TreeSet::new));
        }
        assertEquals(onDisk, listed);
    }

    @Test
    void everyJsonFileParses() throws IOException {
        try (Stream<Path> walk = Files.walk(ROOT)) {
            for (Path path : walk.filter(p -> p.toString().endsWith(".json") || p.toString().endsWith(".mcmeta")).toList()) {
                JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
            }
        }
    }

    @Test
    void packTargetsTheCurrentDataFormat() throws IOException {
        JsonObject pack = JsonParser.parseString(Files.readString(ROOT.resolve("pack.mcmeta"))).getAsJsonObject()
            .getAsJsonObject("pack");
        assertEquals(107, pack.get("max_format").getAsInt());
        assertTrue(pack.get("description").getAsString().contains("AlexModGuy"));
    }

    @Test
    void oneBiomePerCaveBiomeIdWithTheCurrentLayout() throws IOException {
        Path biomes = ROOT.resolve("data/alexscaves/worldgen/biome");
        Set<String> files;
        try (Stream<Path> list = Files.list(biomes)) {
            files = list.map(path -> path.getFileName().toString().replace(".json", "")).collect(Collectors.toSet());
        }
        Set<String> expected = Stream.of(CaveBiomeId.values()).map(CaveBiomeId::path).collect(Collectors.toSet());
        assertEquals(expected, files);
        for (String name : files) {
            JsonObject biome = JsonParser.parseString(Files.readString(biomes.resolve(name + ".json"))).getAsJsonObject();
            for (String key : List.of("attributes", "carvers", "downfall", "effects", "features", "has_precipitation",
                "spawn_costs", "spawners", "temperature")) {
                assertTrue(biome.has(key), name + " lacks " + key);
            }
            assertFalse(biome.getAsJsonObject("effects").has("fog_color"), "1.20 effects layout left in " + name);
            assertTrue(biome.getAsJsonObject("attributes").has("minecraft:visual/fog_color"), name);
            for (String sound : soundReferences(biome)) {
                assertTrue(sound.startsWith("alexscaves:"), name + " sound " + sound);
            }
            biome.getAsJsonObject("spawners").entrySet().forEach(group ->
                group.getValue().getAsJsonArray().forEach(entry -> assertTrue(
                    entry.getAsJsonObject().get("type").getAsString().startsWith("minecraft:"),
                    "module mobs are spawned by the plugin, not the datapack: " + name)));
        }
    }

    @Test
    void damageTypesUseNamespacedMessages() throws IOException {
        try (Stream<Path> list = Files.list(ROOT.resolve("data/alexscaves/damage_type"))) {
            for (Path path : list.toList()) {
                JsonObject damage = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                assertTrue(damage.get("message_id").getAsString().startsWith("alexscaves."), path.toString());
            }
        }
    }

    /** Sound holders must be inline ({@code sound_id}): the sound event registry is not data-driven. */
    private static List<String> soundReferences(JsonObject biome) {
        List<String> sounds = new ArrayList<>();
        collect(biome.getAsJsonObject("attributes"), sounds);
        return sounds;
    }

    private static void collect(JsonElement element, List<String> sounds) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (var entry : object.entrySet()) {
                if (entry.getKey().equals("sound") || entry.getKey().equals("loop")) {
                    assertTrue(entry.getValue().isJsonObject(), "sound must be inline: " + entry);
                    sounds.add(entry.getValue().getAsJsonObject().get("sound_id").getAsString());
                } else {
                    collect(entry.getValue(), sounds);
                }
            }
        } else if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> collect(child, sounds));
        }
    }
}
