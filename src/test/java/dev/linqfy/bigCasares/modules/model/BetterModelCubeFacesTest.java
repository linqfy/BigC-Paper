package dev.linqfy.bigCasares.modules.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * BetterModel 3.2.0 reads every cube's six faces and fails the whole model reload (every
 * plugin's models, not only the broken one) when one is missing; found on a live server.
 */
class BetterModelCubeFacesTest {

    private static final Set<String> FACES = Set.of("north", "east", "south", "west", "up", "down");

    @Test
    void everyPackagedCubeHasAllSixFaces() throws IOException {
        List<String> broken = new ArrayList<>();
        try (Stream<Path> files = Files.list(Path.of("src/main/resources/bettermodel/models"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".bbmodel")).sorted().toList()) {
                JsonObject model = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                for (JsonElement element : model.getAsJsonArray("elements")) {
                    JsonObject cube = element.getAsJsonObject();
                    String type = cube.has("type") ? cube.get("type").getAsString() : "cube";
                    if (type.equals("cube") && !cube.getAsJsonObject("faces").keySet().equals(FACES)) {
                        broken.add(file.getFileName() + ": " + cube.get("name").getAsString());
                    }
                }
            }
        }
        assertEquals(List.of(), broken);
    }
}
