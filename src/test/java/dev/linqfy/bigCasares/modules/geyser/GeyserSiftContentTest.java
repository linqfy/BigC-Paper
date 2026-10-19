package dev.linqfy.bigCasares.modules.geyser;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Sift's Bedrock side is generated (tools/export_sift_bedrock.py) and registered from Java code; these checks
 * tie the two together, so a renamed item, model or sound cannot leave a Bedrock player with a missing texture.
 */
class GeyserSiftContentTest {

    private static final Path PACK = Path.of("resourcepack/bedrock");
    private static final Path JAVA_PACK = Path.of("resourcepack/java/assets");

    @Test
    void everySiftBlockStateHasABedrockBlockWithTexturesInThePack() throws IOException {
        List<GeyserCustomBlockDefinition> blocks = blocks();
        JsonObject terrain = json(PACK.resolve("textures/terrain_texture.json")).getAsJsonObject("texture_data");
        Set<String> missing = new TreeSet<>();
        for (GeyserCustomBlockDefinition block : blocks) {
            for (String texture : block.faces().values()) {
                if (!terrain.has(texture) || !Files.isRegularFile(PACK.resolve(
                    terrain.getAsJsonObject(texture).get("textures").getAsString() + ".png"))) {
                    missing.add(block.name() + " -> " + texture);
                }
            }
        }
        assertEquals(Set.of(), missing);
        assertEquals(blocks.size(), blocks.stream().map(GeyserCustomBlockDefinition::javaState).distinct().count(),
            "one Bedrock block per Java state");
        // Geyser refuses a second block under a name it already has, and stops registering the rest.
        assertEquals(blocks.size(), blocks.stream().map(GeyserCustomBlockDefinition::name).distinct().count(),
            "Bedrock block names are unique");
        assertEquals(siftStatesInTheJavaPack(), blocks.size(), "every mushroom-block state drawing a Sift block");
    }

    @Test
    void everySiftItemHasAnIconInThePack() throws IOException {
        JsonObject icons = json(PACK.resolve("textures/item_texture.json")).getAsJsonObject("texture_data");
        Set<String> missing = new TreeSet<>();
        for (GeyserCustomItemDefinition item : GeyserSiftContent.items()) {
            if (!icons.has(item.icon()) || !Files.isRegularFile(PACK.resolve(
                icons.getAsJsonObject(item.icon()).get("textures").getAsString() + ".png"))) {
                missing.add(item.bedrockIdentifier());
            }
        }
        assertEquals(Set.of(), missing);
        assertTrue(GeyserSiftContent.items().size() > 80, "the Sift's content items are all mapped");
    }

    @Test
    void everySiftEntityIsAClientEntityWhosePartsExist() throws IOException {
        Map<String, JsonObject> clientEntities = new java.util.HashMap<>();
        for (Path file : files("entity", ".entity.json")) {
            JsonObject description = json(file).getAsJsonObject("minecraft:client_entity").getAsJsonObject("description");
            clientEntities.put(description.get("identifier").getAsString(), description);
        }
        Set<String> geometries = declared("models/entity", ".geo.json", root -> {
            Set<String> ids = new HashSet<>();
            root.getAsJsonArray("minecraft:geometry").forEach(geometry -> ids.add(
                geometry.getAsJsonObject().getAsJsonObject("description").get("identifier").getAsString()));
            return ids;
        });
        Set<String> animations = declared("animations", ".animation.json",
            root -> root.getAsJsonObject("animations").keySet());
        animations.addAll(declared("animation_controllers", ".json",
            root -> root.getAsJsonObject("animation_controllers").keySet()));
        Set<String> renderControllers = declared("render_controllers", ".json",
            root -> root.getAsJsonObject("render_controllers").keySet());
        String lang = Files.readString(PACK.resolve("texts/en_US.lang"), StandardCharsets.UTF_8);

        Set<String> problems = new TreeSet<>();
        for (GeyserCustomEntityDefinition entity : GeyserSiftContent.entities()) {
            String id = entity.bedrockIdentifier();
            JsonObject description = clientEntities.get(id);
            if (description == null) {
                problems.add(id + ": no client entity");
                continue;
            }
            description.getAsJsonObject("geometry").entrySet().forEach(geometry -> {
                if (!geometries.contains(geometry.getValue().getAsString())) {
                    problems.add(id + ": geometry " + geometry.getValue());
                }
            });
            description.getAsJsonObject("animations").entrySet().forEach(animation -> {
                if (!animations.contains(animation.getValue().getAsString())) {
                    problems.add(id + ": animation " + animation.getValue());
                }
            });
            description.getAsJsonObject("textures").entrySet().forEach(texture -> {
                if (!Files.isRegularFile(PACK.resolve(texture.getValue().getAsString() + ".png"))) {
                    problems.add(id + ": texture " + texture.getValue());
                }
            });
            description.getAsJsonArray("render_controllers").forEach(controller -> {
                if (!renderControllers.contains(controller.getAsString())) {
                    problems.add(id + ": render controller " + controller);
                }
            });
            if (!lang.contains("entity." + id + ".name=")) {
                problems.add(id + ": no name");
            }
        }
        assertEquals(Set.of(), problems);
    }

    @Test
    void everySiftSoundHasABedrockDefinitionWithItsFiles() throws IOException {
        JsonObject java = json(JAVA_PACK.resolve("the_sift/sounds.json"));
        JsonObject bedrock = json(PACK.resolve("sounds/sound_definitions.json")).getAsJsonObject("sound_definitions");
        Set<String> problems = new TreeSet<>();
        for (String event : java.keySet()) {
            JsonObject definition = bedrock.getAsJsonObject("the_sift:" + event);
            if (definition == null) {
                problems.add(event + ": no Bedrock definition");
                continue;
            }
            for (JsonElement sound : definition.getAsJsonArray("sounds")) {
                String name = sound.isJsonObject() ? sound.getAsJsonObject().get("name").getAsString() : sound.getAsString();
                if (name.startsWith("sounds/the_sift/") && !Files.isRegularFile(PACK.resolve(name + ".ogg"))) {
                    problems.add(event + ": missing " + name);
                }
            }
        }
        assertEquals(Set.of(), problems);
    }

    @Test
    void blockDefinitionsRefuseIncompleteBlocks() {
        Map<String, String> faces = Map.of("up", "a", "down", "a", "north", "a", "south", "a", "east", "a", "west", "a");
        assertThrows(IllegalArgumentException.class, () -> new GeyserCustomBlockDefinition(
            "minecraft:mushroom_stem[down=true]", "x", "X", Map.of("up", "a"), "opaque", List.of(0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> new GeyserCustomBlockDefinition(
            "mushroom_stem", "x", "X", faces, "opaque", List.of(0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> new GeyserCustomBlockDefinition(
            "minecraft:mushroom_stem[down=true]", "x", "X", faces, "opaque", List.of(45, 0, 0)));
    }

    private static List<GeyserCustomBlockDefinition> blocks() throws IOException {
        try (InputStream input = GeyserSiftContentTest.class.getClassLoader().getResourceAsStream(GeyserSiftContent.BLOCKS)) {
            return GeyserSiftContent.blocks(input);
        }
    }

    private static long siftStatesInTheJavaPack() throws IOException {
        long states = 0;
        for (String block : List.of("brown_mushroom_block", "red_mushroom_block", "mushroom_stem")) {
            for (JsonElement part : json(JAVA_PACK.resolve("minecraft/blockstates/" + block + ".json")).getAsJsonArray("multipart")) {
                if (part.getAsJsonObject().get("apply").toString().contains("the_sift:")) {
                    states++;
                }
            }
        }
        return states;
    }

    private interface Declarations {
        Set<String> of(JsonObject root);
    }

    private static Set<String> declared(String directory, String suffix, Declarations declarations) throws IOException {
        Set<String> names = new HashSet<>();
        for (Path file : files(directory, suffix)) {
            names.addAll(declarations.of(json(file)));
        }
        return names;
    }

    private static List<Path> files(String directory, String suffix) throws IOException {
        try (Stream<Path> files = Files.list(PACK.resolve(directory))) {
            return files.filter(file -> file.getFileName().toString().endsWith(suffix)).sorted().toList();
        }
    }

    private static JsonObject json(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
