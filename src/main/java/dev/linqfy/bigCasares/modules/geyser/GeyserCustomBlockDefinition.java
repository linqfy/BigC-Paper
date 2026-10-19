package dev.linqfy.bigCasares.modules.geyser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A Bedrock custom block that Geyser shows in place of one Java block state: a full cube with a texture per face
 * (keys of the Bedrock pack's terrain_texture.json) and a turn in steps of 90 degrees. Content blocks are
 * mushroom-block states on Java, which a Bedrock client would draw as mushroom blocks.
 *
 * @param javaState   the Java block state, e.g. {@code minecraft:brown_mushroom_block[down=true,east=false,...]}
 * @param name        the Bedrock block name; Geyser puts it in its own namespace
 * @param faces       texture per face: up, down, north, south, east, west
 * @param rotation    turn around x, y and z, as the Java blockstate turns the model
 */
public record GeyserCustomBlockDefinition(
    String javaState,
    String name,
    String displayName,
    Map<String, String> faces,
    String renderMethod,
    List<Integer> rotation
) {
    static final Set<String> FACES = Set.of("up", "down", "north", "south", "east", "west");

    public GeyserCustomBlockDefinition {
        javaState = requireText(javaState, "javaState");
        if (!javaState.matches("minecraft:[a-z_]+\\[[a-z_]+=[a-z0-9_]+(,[a-z_]+=[a-z0-9_]+)*]")) {
            throw new IllegalArgumentException("Not a block state: " + javaState);
        }
        name = requireText(name, "name");
        if (!name.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Bedrock block names are lower-case words: " + name);
        }
        displayName = requireText(displayName, "displayName");
        faces = Map.copyOf(faces);
        if (!faces.keySet().equals(FACES)) {
            throw new IllegalArgumentException(name + " needs a texture for each face: " + faces.keySet());
        }
        renderMethod = requireText(renderMethod, "renderMethod");
        rotation = List.copyOf(rotation);
        if (rotation.size() != 3 || rotation.stream().anyMatch(angle -> angle % 90 != 0)) {
            throw new IllegalArgumentException(name + " turns by right angles only: " + rotation);
        }
    }

    /** Reads the {@code blocks} list that tools/export_sift_bedrock.py writes. */
    public static List<GeyserCustomBlockDefinition> read(InputStream input) throws IOException {
        try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            List<GeyserCustomBlockDefinition> blocks = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray("blocks")) {
                JsonObject block = element.getAsJsonObject();
                Map<String, String> faces = new LinkedHashMap<>();
                block.getAsJsonObject("faces").entrySet()
                    .forEach(face -> faces.put(face.getKey(), face.getValue().getAsString()));
                List<Integer> rotation = new ArrayList<>();
                JsonArray angles = block.getAsJsonArray("rotation");
                angles.forEach(angle -> rotation.add(angle.getAsInt()));
                blocks.add(new GeyserCustomBlockDefinition(
                    block.get("state").getAsString(), block.get("name").getAsString(),
                    block.get("display_name").getAsString(), faces,
                    block.get("render_method").getAsString(), rotation));
            }
            return List.copyOf(blocks);
        } catch (RuntimeException malformed) {
            throw new IOException("Malformed custom block list", malformed);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }
}
