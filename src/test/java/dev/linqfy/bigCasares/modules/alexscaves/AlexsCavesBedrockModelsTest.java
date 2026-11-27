package dev.linqfy.bigCasares.modules.alexscaves;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlexsCavesBedrockModelsTest {
    private static final Path MODELS = Path.of("src/main/resources/bettermodel/models");

    @Test
    void everyPackagedMobModelConvertsToGeyserModelEngineInput() throws IOException {
        int models = 0;
        try (var files = Files.list(MODELS)) {
            for (Path file : files.filter(p -> p.getFileName().toString().startsWith("alexscaves_")).toList()) {
                String name = file.getFileName().toString().replace(".bbmodel", "");
                Map<String, byte[]> converted = AlexsCavesBedrockModels.convert(Files.readString(file, StandardCharsets.UTF_8));
                assertTrue(converted.containsKey(name + "/config.json"), name);
                assertTrue(converted.containsKey(name + "/" + name + ".animation.json"), name);
                JsonObject geometry = JsonParser.parseString(new String(converted.get(name + "/" + name + ".geo.json"),
                    StandardCharsets.UTF_8)).getAsJsonObject();
                JsonObject geo = geometry.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
                assertEquals("geometry." + name, geo.getAsJsonObject("description").get("identifier").getAsString());
                assertFalse(geo.getAsJsonArray("bones").isEmpty(), name);
                assertTrue(converted.keySet().stream().anyMatch(key -> key.endsWith(".png")), name);
                models++;
            }
        }
        assertEquals(68, models, "the 47 mob and part models, the mushroom cloud, the waste drum and the 19 egg models");
    }

    @Test
    void configIsOnlyWrittenTheFirstTime() {
        assertTrue(AlexsCavesBedrockModels.keepsExisting("alexscaves_teletor/config.json"));
        assertFalse(AlexsCavesBedrockModels.keepsExisting("alexscaves_teletor/alexscaves_teletor.geo.json"));
    }
}
