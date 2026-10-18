package dev.linqfy.bigCasares.modules.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * BetterModel 3.2.0 reads every cube's six faces and fails the whole model reload (every
 * plugin's models, not only the broken one) when one is missing; found on a live server.
 * It also drops faces whose UVs leave the texture, and a 26.2 client refuses a model element
 * left without faces, so the bone is invisible; found with a real client.
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

    @Test
    void everyTexturedCubeFaceStaysInsideItsTexture() throws IOException {
        List<String> outside = new ArrayList<>();
        try (Stream<Path> files = Files.list(Path.of("src/main/resources/bettermodel/models"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".bbmodel")).sorted().toList()) {
                JsonObject model = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonObject resolution = model.getAsJsonObject("resolution");
                Map<String, double[]> sizes = new HashMap<>();
                JsonArray textures = model.getAsJsonArray("textures");
                for (int index = 0; index < textures.size(); index++) {
                    JsonObject texture = textures.get(index).getAsJsonObject();
                    double[] size = {
                        texture.has("uv_width") ? texture.get("uv_width").getAsDouble() : resolution.get("width").getAsDouble(),
                        texture.has("uv_height") ? texture.get("uv_height").getAsDouble() : resolution.get("height").getAsDouble()};
                    sizes.put(String.valueOf(index), size);
                    if (texture.has("id")) {
                        sizes.put(texture.get("id").getAsString(), size);
                    }
                }
                for (JsonElement element : model.getAsJsonArray("elements")) {
                    JsonObject cube = element.getAsJsonObject();
                    if (cube.has("type") && !cube.get("type").getAsString().equals("cube")) {
                        continue;
                    }
                    for (Map.Entry<String, JsonElement> face : cube.getAsJsonObject("faces").entrySet()) {
                        JsonObject data = face.getValue().getAsJsonObject();
                        if (!data.has("texture") || data.get("texture").isJsonNull()) {
                            continue;
                        }
                        double[] size = sizes.get(data.get("texture").getAsString());
                        JsonArray uv = data.getAsJsonArray("uv");
                        for (int corner = 0; corner < 4; corner++) {
                            double value = uv.get(corner).getAsDouble();
                            double limit = corner % 2 == 0 ? size[0] : size[1];
                            if (value < 0 || value > limit) {
                                outside.add(file.getFileName() + ": " + cube.get("name").getAsString() + " " + face.getKey());
                                break;
                            }
                        }
                    }
                }
            }
        }
        assertEquals(List.of(), outside);
    }
}
