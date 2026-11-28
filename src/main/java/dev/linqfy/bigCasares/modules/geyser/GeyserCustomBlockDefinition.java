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
 * A Bedrock custom block that Geyser shows in place of one or more Java block states: a full cube with a texture per
 * face (keys of the Bedrock pack's terrain_texture.json) and a turn in steps of 90 degrees. Content blocks are
 * reserved mushroom-block states on Java (the Sift's and the hotel's), which a Bedrock client would otherwise draw
 * as a vanilla mushroom.
 *
 * @param javaState   the Java block state, e.g. {@code minecraft:brown_mushroom_block[down=true,east=false,...]}, or
 *                    just the block, e.g. {@code minecraft:smooth_stone}, when it has no properties
 * @param name        the Bedrock block name; Geyser puts it in its own namespace
 * @param faces       texture per face: up, down, north, south, east, west
 * @param rotation    turn around x, y and z, as the Java blockstate turns the model
 * @param aliasStates further Java states shown as this same Bedrock block (several states drawn the same way)
 * @param lightEmission light the Bedrock client draws from the block, 0 to 15; Bedrock lights by its own block, not
 *                    by the Java light it is sent
 */
public record GeyserCustomBlockDefinition(
    String javaState,
    String name,
    String displayName,
    Map<String, String> faces,
    String renderMethod,
    List<Integer> rotation,
    List<String> aliasStates,
    int lightEmission
) {
    static final Set<String> FACES = Set.of("up", "down", "north", "south", "east", "west");
    private static final String STATE = "minecraft:[a-z_]+(\\[[a-z_]+=[a-z0-9_]+(,[a-z_]+=[a-z0-9_]+)*])?";

    public GeyserCustomBlockDefinition(String javaState, String name, String displayName,
                                       Map<String, String> faces, String renderMethod, List<Integer> rotation) {
        this(javaState, name, displayName, faces, renderMethod, rotation, List.of(), 0);
    }

    public GeyserCustomBlockDefinition {
        javaState = requireState(javaState);
        aliasStates = aliasStates.stream().map(GeyserCustomBlockDefinition::requireState).toList();
        if (lightEmission < 0 || lightEmission > 15) {
            throw new IllegalArgumentException("Light is 0 to 15: " + lightEmission);
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

    /** Every Java state this block replaces, the first one being {@link #javaState()}. */
    public List<String> javaStates() {
        List<String> states = new ArrayList<>();
        states.add(javaState);
        states.addAll(aliasStates);
        return List.copyOf(states);
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

    private static String requireState(String state) {
        String text = requireText(state, "javaState");
        if (!text.matches(STATE)) {
            throw new IllegalArgumentException("Not a block state: " + text);
        }
        return text;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }
}
