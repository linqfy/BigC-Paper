package dev.linqfy.bigCasares.modules.geyser;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.linqfy.bigCasares.modules.caichet.CaiChetBedrockBlocks;
import dev.linqfy.bigCasares.testsupport.CompiledClass;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeyserCaiChetBlocksTest {

    private static final Path BEDROCK = Path.of("resourcepack/bedrock");
    private static final Path JAVA = Path.of("resourcepack/java/assets");
    private static final Path HOTEL = Path.of("src/main/java/dev/linqfy/bigCasares/modules/caichet/HotelLayout.java");

    @Test
    void blockStatesWithoutPropertiesAreAccepted() {
        Map<String, String> faces = Map.of("up", "a", "down", "a", "north", "a", "south", "a", "east", "a", "west", "a");
        GeyserCustomBlockDefinition plain = new GeyserCustomBlockDefinition(
            "minecraft:waxed_copper_block", "x", "X", faces, "opaque", List.of(0, 0, 0));
        assertEquals(List.of("minecraft:waxed_copper_block"), plain.javaStates());
        assertEquals(0, plain.lightEmission());
        GeyserCustomBlockDefinition bulb = new GeyserCustomBlockDefinition(
            "minecraft:waxed_copper_bulb[lit=true,powered=false]", "y", "Y", faces, "opaque", List.of(0, 0, 0),
            List.of("minecraft:waxed_copper_bulb[lit=true,powered=true]"), 15);
        assertEquals(2, bulb.javaStates().size());
        assertThrows(IllegalArgumentException.class, () -> new GeyserCustomBlockDefinition(
            "minecraft:waxed_copper_block[", "x", "X", faces, "opaque", List.of(0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> new GeyserCustomBlockDefinition(
            "waxed_copper_block", "x", "X", faces, "opaque", List.of(0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> new GeyserCustomBlockDefinition(
            "minecraft:waxed_copper_block", "x", "X", faces, "opaque", List.of(0, 0, 0), List.of("bulb"), 0));
        assertThrows(IllegalArgumentException.class, () -> new GeyserCustomBlockDefinition(
            "minecraft:waxed_copper_block", "x", "X", faces, "opaque", List.of(0, 0, 0), List.of(), 16));
    }

    @Test
    void everyBlockOfTheHotelHasADefinition() throws IOException {
        Set<String> hotel = new TreeSet<>();
        Matcher matcher = Pattern.compile("Material\\.(WAXED_[A-Z_]+)").matcher(Files.readString(HOTEL, StandardCharsets.UTF_8));
        while (matcher.find()) {
            hotel.add("minecraft:" + matcher.group(1).toLowerCase());
        }
        assertEquals(15, hotel.size(), "the hotel is built of 15 waxed copper blocks");
        Set<String> covered = new TreeSet<>();
        for (GeyserCustomBlockDefinition definition : definitions()) {
            for (String state : definition.javaStates()) {
                covered.add(state.replaceAll("\\[.*", ""));
            }
        }
        assertEquals(hotel, covered);
    }

    @Test
    void everyStateMapsOnceAndNamesAreUnique() {
        List<GeyserCustomBlockDefinition> definitions = definitions();
        List<String> states = definitions.stream().flatMap(definition -> definition.javaStates().stream()).toList();
        assertEquals(states.size(), new HashSet<>(states).size(), "a Java state has one Bedrock block");
        assertEquals(definitions.size(), definitions.stream().map(GeyserCustomBlockDefinition::name).distinct().count());
        // 12 plain blocks, 3 lit or dirty bulbs and 2 unlit bulbs, each with powered on and off
        assertEquals(12 + 3 * 2 + 2 * 2, states.size());
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
        for (CaiChetBedrockBlocks.Block block : CaiChetBedrockBlocks.all()) {
            for (String state : block.javaStates()) {
                String name = state.replaceAll("\\[.*", "").substring("minecraft:".length());
                JsonObject variants = json(JAVA.resolve("minecraft/blockstates/" + name + ".json")).getAsJsonObject("variants");
                String key = state.contains("[") ? state.substring(state.indexOf('[') + 1, state.length() - 1) : "";
                if (!variants.has(key)) {
                    problems.add(state + " has no variant");
                    continue;
                }
                String model = variants.getAsJsonObject(key).get("model").getAsString();
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
    void panelsKeepTheLightOfTheirJavaBlock() {
        Map<String, Integer> light = new HashMap<>();
        for (GeyserCustomBlockDefinition definition : definitions()) {
            for (String state : definition.javaStates()) {
                light.put(state, definition.lightEmission());
            }
        }
        assertEquals(15, light.get("minecraft:waxed_copper_bulb[lit=true,powered=false]"));
        assertEquals(8, light.get("minecraft:waxed_weathered_copper_bulb[lit=true,powered=true]"));
        assertEquals(0, light.get("minecraft:waxed_copper_bulb[lit=false,powered=true]"));
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
