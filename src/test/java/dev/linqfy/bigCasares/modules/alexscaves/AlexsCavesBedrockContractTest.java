package dev.linqfy.bigCasares.modules.alexscaves;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Bedrock side of the module: Geyser block/item mappings and the textures, geometries and sounds they name. */
class AlexsCavesBedrockContractTest {
    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Path BEDROCK = Path.of("resourcepack/bedrock");
    private static final List<String> INSTRUMENTS = List.of("harp", "basedrum", "snare", "hat", "bass", "flute", "bell",
        "guitar", "chime", "xylophone", "iron_xylophone", "cow_bell", "didgeridoo", "bit", "banjo", "pling");
    /** Vanilla atlas keys the module's models borrow (Bedrock has them already). */
    private static final Set<String> VANILLA_TEXTURES = Set.of("bone_block_side", "bone_block_top");
    /** blocks.yml slots that no longer draw a block (kept reserved for old worlds). */
    private static final Set<String> RESERVED = Set.of("chiseled_candy_cane_block default");

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static Map<String, JsonObject> overridesOf(String base) throws IOException {
        JsonObject blocks = json(RESOURCES.resolve("alexscaves/geyser/alexscaves-blocks.json")).getAsJsonObject("blocks");
        JsonObject definition = blocks.getAsJsonObject("minecraft:" + base);
        assertNotNull(definition, base);
        assertTrue(definition.get("only_override_states").getAsBoolean(), base);
        Map<String, JsonObject> result = new java.util.LinkedHashMap<>();
        definition.getAsJsonObject("state_overrides").entrySet()
            .forEach(entry -> result.put(entry.getKey(), entry.getValue().getAsJsonObject()));
        return result;
    }

    private static String bit(int index, int shift) {
        return (index >> shift & 1) == 1 ? "true" : "false";
    }

    private static String stateKey(String base, int index) {
        return switch (base) {
            case "note_block" -> "instrument=" + INSTRUMENTS.get(index / 50) + ",note=" + (index % 50) / 2
                + ",powered=" + (index % 2 == 1);
            case "tripwire" -> "attached=" + bit(index, 6) + ",disarmed=" + bit(index, 5) + ",east=" + bit(index, 2)
                + ",north=" + bit(index, 3) + ",powered=" + bit(index, 4) + ",south=" + bit(index, 1)
                + ",west=" + bit(index, 0);
            default -> "down=" + bit(index, 5) + ",east=" + bit(index, 2) + ",north=" + bit(index, 3)
                + ",south=" + bit(index, 1) + ",up=" + bit(index, 4) + ",west=" + bit(index, 0);
        };
    }

    @Test
    void everyAllocatedStateHasABedrockOverride() throws IOException {
        Map<String, Map<String, JsonObject>> overrides = Map.of(
            "note_block", overridesOf("note_block"), "tripwire", overridesOf("tripwire"),
            "chorus_plant", overridesOf("chorus_plant"));
        String block = null;
        int checked = 0;
        for (String line : Files.readAllLines(RESOURCES.resolve("alexscaves/blocks.yml"), StandardCharsets.UTF_8)) {
            if (line.startsWith("#") || line.isBlank()) {
                continue;
            }
            if (!line.startsWith(" ")) {
                block = line.trim().replace(":", "");
                continue;
            }
            String[] split = line.trim().split(":\\s*");
            String[] tokens = split[1].split(" ");
            String base = tokens.length == 1 ? "note_block" : tokens[0];
            int index = Integer.parseInt(tokens.length == 1 ? tokens[0] : tokens[1]);
            if (RESERVED.contains(block + " " + split[0])) {
                continue;
            }
            assertNotNull(overrides.get(base).get(stateKey(base, index)), block + " " + split[0]);
            checked++;
        }
        assertTrue(checked > 400, "states checked: " + checked);
    }

    @Test
    void overridesNameExistingTexturesAndGeometries() throws IOException {
        JsonObject terrain = json(BEDROCK.resolve("textures/terrain_texture.json")).getAsJsonObject("texture_data");
        Set<String> identifiers = new java.util.HashSet<>();
        try (var files = Files.list(BEDROCK.resolve("models/blocks"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".geo.json")).toList()) {
                JsonObject geometry = json(file).getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
                identifiers.add(geometry.getAsJsonObject("description").get("identifier").getAsString());
                assertFalse(geometry.getAsJsonArray("bones").get(0).getAsJsonObject().getAsJsonArray("cubes").isEmpty(),
                    file.toString());
            }
        }
        JsonObject all = json(RESOURCES.resolve("alexscaves/geyser/alexscaves-blocks.json")).getAsJsonObject("blocks");
        int geometries = 0;
        for (Map.Entry<String, JsonElement> block : all.entrySet()) {
            for (Map.Entry<String, JsonElement> state : block.getValue().getAsJsonObject().getAsJsonObject("state_overrides")
                .entrySet()) {
                JsonObject override = state.getValue().getAsJsonObject();
                if (override.has("geometry")) {
                    geometries++;
                    assertTrue(identifiers.contains(override.get("geometry").getAsString()), override.toString());
                }
                for (Map.Entry<String, JsonElement> material : override.getAsJsonObject("material_instances").entrySet()) {
                    String texture = material.getValue().getAsJsonObject().get("texture").getAsString();
                    if (VANILLA_TEXTURES.contains(texture)) {
                        continue;
                    }
                    assertTrue(terrain.has(texture), "terrain_texture.json: " + texture);
                    String path = terrain.getAsJsonObject(texture).get("textures").getAsString();
                    assertTrue(Files.isRegularFile(BEDROCK.resolve(path + ".png")), path);
                }
            }
        }
        assertTrue(geometries > 100, "non-cube blocks need a geometry: " + geometries);
    }

    @Test
    void plantsAndSlabsAreNotDrawnAsCubes() throws IOException {
        JsonObject flytrap = overridesOf("tripwire").values().stream()
            .filter(o -> o.get("name").getAsString().equals("alexscaves_flytrap_default")).findFirst().orElseThrow();
        assertTrue(flytrap.has("geometry"));
        Map<String, JsonObject> slab = overridesOf("petrified_oak_slab");
        assertEquals(6, slab.size());
        assertTrue(slab.get("type=bottom,waterlogged=false").has("geometry"));
        assertTrue(slab.get("type=top,waterlogged=true").has("geometry"));
        assertFalse(slab.get("type=double,waterlogged=false").has("geometry"));
    }

    @Test
    void everyItemMappingHasItsIconAndTheDisplayModelsAreMapped() throws IOException {
        JsonObject icons = json(BEDROCK.resolve("textures/item_texture.alexscaves.json")).getAsJsonObject("texture_data");
        JsonObject items = json(RESOURCES.resolve("alexscaves/geyser/alexscaves-items.json")).getAsJsonObject("items");
        Set<String> models = new java.util.HashSet<>();
        for (Map.Entry<String, JsonElement> material : items.entrySet()) {
            for (JsonElement element : material.getValue().getAsJsonArray()) {
                JsonObject definition = element.getAsJsonObject();
                models.add(definition.get("model").getAsString());
                String icon = definition.getAsJsonObject("bedrock_options").get("icon").getAsString();
                assertTrue(icons.has(icon), icon);
                assertTrue(Files.isRegularFile(BEDROCK.resolve(icons.getAsJsonObject(icon).get("textures").getAsString() + ".png")), icon);
            }
        }
        try (var displays = Files.list(Path.of("resourcepack/java/assets/alexscaves/items/display"))) {
            for (Path display : displays.toList()) {
                String name = display.getFileName().toString().replace(".json", "");
                assertTrue(models.contains("alexscaves:display/" + name), name);
            }
        }
    }

    @Test
    void soundDefinitionsPointToShippedFiles() throws IOException {
        JsonObject definitions = json(BEDROCK.resolve("sounds/sound_definitions.json")).getAsJsonObject("sound_definitions");
        int own = 0;
        for (Map.Entry<String, JsonElement> definition : definitions.entrySet()) {
            if (!definition.getKey().startsWith("alexscaves:")) {
                continue;
            }
            own++;
            JsonArray sounds = definition.getValue().getAsJsonObject().getAsJsonArray("sounds");
            for (JsonElement sound : sounds) {
                String name = sound.getAsJsonObject().get("name").getAsString();
                assertTrue(Files.isRegularFile(BEDROCK.resolve(name + ".ogg")), name);
            }
        }
        assertTrue(own > 200, "mob and block sounds: " + own);
    }
}
