package dev.linqfy.bigCasares.modules.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The committed mushroom blockstate overrides must match the block registry, and
 * every model and texture they reference must ship in the Java resource pack.
 */
class CustomBlockstateContractTest {

    private static final Path PACK = Path.of("resourcepack");

    @Test
    void committedBlockstatesMatchTheRegistry() throws IOException {
        for (Map.Entry<String, String> file : CustomBlockstateExportMain.generate().entrySet()) {
            Path committed = PACK.resolve(CustomBlockstateExportMain.BLOCKSTATE_DIRECTORY).resolve(file.getKey());
            assertTrue(Files.isRegularFile(committed), committed + " is missing; run gradlew generateCustomBlockstates");
            assertEquals(file.getValue(), Files.readString(committed, StandardCharsets.UTF_8),
                committed + " is stale; run gradlew generateCustomBlockstates");
        }
    }

    @Test
    void everyReferencedModelAndTextureShips() throws IOException {
        Set<String> models = new LinkedHashSet<>();
        for (String json : CustomBlockstateExportMain.generate().values()) {
            collect(JsonParser.parseString(json), "model", models);
        }
        Deque<String> pending = new ArrayDeque<>(models);
        Set<String> visited = new LinkedHashSet<>();
        while (!pending.isEmpty()) {
            String model = pending.pop();
            if (model.startsWith("minecraft:") || !visited.add(model)) {
                continue;
            }
            Path file = asset(model, "models", ".json");
            assertTrue(Files.isRegularFile(file), "missing model " + model + " (" + file + ")");
            JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (json.has("parent")) {
                pending.push(json.get("parent").getAsString());
            }
            if (json.has("textures")) {
                for (Map.Entry<String, JsonElement> texture : json.getAsJsonObject("textures").entrySet()) {
                    String value = texture.getValue().getAsString();
                    if (value.startsWith("#") || value.startsWith("minecraft:")) {
                        continue;
                    }
                    Path png = asset(value, "textures", ".png");
                    assertTrue(Files.isRegularFile(png), "missing texture " + value + " for " + model);
                }
            }
        }
        assertTrue(visited.size() > 10, "expected the Sift block models to be referenced");
    }

    private static Path asset(String id, String kind, String extension) {
        int colon = id.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
        String path = colon < 0 ? id : id.substring(colon + 1);
        return PACK.resolve("java/assets").resolve(namespace).resolve(kind).resolve(path + extension);
    }

    private static void collect(JsonElement element, String key, Set<String> out) {
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                if (entry.getKey().equals(key) && entry.getValue().isJsonPrimitive()) {
                    out.add(entry.getValue().getAsString());
                } else {
                    collect(entry.getValue(), key, out);
                }
            }
        } else if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> collect(child, key, out));
        }
    }
}
