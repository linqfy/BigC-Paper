package dev.linqfy.bigCasares.modules.geyser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.linqfy.bigCasares.modules.caichet.CaiChetBedrockBlocks;
import dev.linqfy.bigCasares.modules.caichet.CaiChetBlocks;
import dev.linqfy.bigCasares.testsupport.CompiledClass;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeyserCaiChetBlocksTest {

    private static final Path BEDROCK = Path.of("resourcepack/bedrock");
    private static final Path JAVA = Path.of("resourcepack/java/assets");

    @Test
    void blockStatesWithoutPropertiesAreAccepted() {
        Map<String, String> faces = Map.of("up", "a", "down", "a", "north", "a", "south", "a", "east", "a", "west", "a");
        GeyserCustomBlockDefinition plain = new GeyserCustomBlockDefinition(
            "minecraft:smooth_stone", "x", "X", faces, "opaque", List.of(0, 0, 0));
        assertEquals(List.of("minecraft:smooth_stone"), plain.javaStates());
        assertEquals(0, plain.lightEmission());
        GeyserCustomBlockDefinition bulb = new GeyserCustomBlockDefinition(
            "minecraft:red_mushroom_block[down=true,east=false]", "y", "Y", faces, "opaque", List.of(0, 0, 0),
            List.of("minecraft:red_mushroom_block[down=true,east=true]"), 15);
        assertEquals(2, bulb.javaStates().size());
        assertThrows(IllegalArgumentException.class, () -> new GeyserCustomBlockDefinition(
            "minecraft:red_mushroom_block[", "x", "X", faces, "opaque", List.of(0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> new GeyserCustomBlockDefinition(
            "red_mushroom_block", "x", "X", faces, "opaque", List.of(0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> new GeyserCustomBlockDefinition(
            "minecraft:smooth_stone", "x", "X", faces, "opaque", List.of(0, 0, 0), List.of("bulb"), 0));
        assertThrows(IllegalArgumentException.class, () -> new GeyserCustomBlockDefinition(
            "minecraft:smooth_stone", "x", "X", faces, "opaque", List.of(0, 0, 0), List.of(), 16));
    }

    @Test
    void everyBlockOfTheHotelHasADefinition() {
        Set<String> hotel = CaiChetBlocks.definitions().stream()
            .map(block -> block.state().asString()).collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        assertEquals(15, hotel.size(), "the hotel is built of 15 custom blocks");
        Set<String> covered = new TreeSet<>();
        for (GeyserCustomBlockDefinition definition : definitions()) {
            covered.addAll(definition.javaStates());
        }
        assertEquals(hotel, covered);
    }

    @Test
    void everyStateMapsOnceAndNamesAreUnique() {
        List<GeyserCustomBlockDefinition> definitions = definitions();
        List<String> states = definitions.stream().flatMap(definition -> definition.javaStates().stream()).toList();
        assertEquals(states.size(), new HashSet<>(states).size(), "a Java state has one Bedrock block");
        assertEquals(definitions.size(), definitions.stream().map(GeyserCustomBlockDefinition::name).distinct().count());
        assertEquals(15, states.size());
        assertTrue(states.stream().allMatch(state -> state.startsWith("minecraft:red_mushroom_block[down=true,")));
    }

    @Test
    void everyTextureIsInTheBedrockPack() throws IOException {
        JsonObject terrain = json(BEDROCK.resolve("textures/terrain_texture.json")).getAsJsonObject("texture_data");
        Set<String> missing = new TreeSet<>();
        for (GeyserCustomBlockDefinition definition : definitions()) {
            for (String key : definition.faces().values()) {
                if (!terrain.has(key)) {
                    missing.add(key);
                    continue;
                }
                String path = terrain.getAsJsonObject(key).get("textures").getAsString();
                if (!Files.isRegularFile(BEDROCK.resolve(path + ".png"))) {
                    missing.add(path);
                }
            }
        }
        assertEquals(Set.of(), missing);
    }

    @Test
    void eachBedrockBlockShowsWhatTheJavaBlockstateShows() throws IOException {
        Set<String> problems = new TreeSet<>();
        JsonArray parts = json(JAVA.resolve("minecraft/blockstates/red_mushroom_block.json")).getAsJsonArray("multipart");
        for (CaiChetBedrockBlocks.Block block : CaiChetBedrockBlocks.all()) {
            for (String state : block.javaStates()) {
                JsonObject properties = new JsonObject();
                for (String property : state.substring(state.indexOf('[') + 1, state.length() - 1).split(",")) {
                    String[] pair = property.split("=");
                    properties.addProperty(pair[0], pair[1]);
                }
                String model = null;
                for (JsonElement part : parts) {
                    JsonObject when = part.getAsJsonObject().getAsJsonObject("when");
                    if (when.equals(properties)) {
                        model = part.getAsJsonObject().getAsJsonObject("apply").get("model").getAsString();
                    }
                }
                if (model == null) {
                    problems.add(state + " has no blockstate part");
                    continue;
                }
                JsonObject textures = json(JAVA.resolve(model.replace(":block/", "/models/block/") + ".json"))
                    .getAsJsonObject("textures");
                if (!("caichet:block/" + block.texture()).equals(textures.get("all").getAsString())) {
                    problems.add(state + " shows " + textures.get("all") + " in Java, not " + block.texture());
                }
                if (!Files.isRegularFile(JAVA.resolve("caichet/textures/block/" + block.texture() + ".png"))) {
                    problems.add("Java texture " + block.texture());
                }
            }
        }
        assertEquals(Set.of(), problems);
    }

    @Test
    void panelsGetTheirLightFromTheLightBlockBelowNotFromTheMushroom() {
        // The mushroom does not glow on Java, so Bedrock must not light by itself either: the light block does.
        assertTrue(definitions().stream().allMatch(definition -> definition.lightEmission() == 0));
    }

    @Test
    void geyserModuleRegistersTheCaiChetBlocksThroughTheFactory() throws IOException {
        CompiledClass module = CompiledClass.of(getClass(), GeyserIntegrationModule.class.getName());
        assertTrue(module.calls("dev/linqfy/bigCasares/modules/geyser/GeyserCaiChetBlocks", "definitions"));
        assertTrue(module.calls("dev/linqfy/bigCasares/modules/caichet/CaiChetBedrockBlocks", "all"));
    }

    private static List<GeyserCustomBlockDefinition> definitions() {
        return GeyserCaiChetBlocks.definitions(CaiChetBedrockBlocks.all());
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
