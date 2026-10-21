package dev.linqfy.bigCasares.modules.sift;

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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The mod's blockstates for its non-cube blocks (from tools/export_sift_block_models.py), answering which block
 * models draw a placed look-alike. The look-alike is a vanilla block with the same properties as the mod's block,
 * so its state is matched against the mod's variants or multipart cases as the client would. A property the
 * vanilla block does not have (the mod's vines know whether they hang at the bottom) reads as {@code false}.
 */
final class SiftBlockModelStates {

    static final String RESOURCE = "the_sift/block_models.json";

    /** One block model, turned like a blockstate turns it (degrees, x first then y). */
    record Piece(String model, int x, int y) {
    }

    private sealed interface Rule permits Variants, Multipart {
    }

    private record Variants(Map<Map<String, String>, List<Piece>> variants) implements Rule {
    }

    private record Case(JsonObject when, List<Piece> pieces) {
    }

    private record Multipart(List<Case> cases) implements Rule {
    }

    private final Map<String, Rule> blocks;

    private SiftBlockModelStates(Map<String, Rule> blocks) {
        this.blocks = Map.copyOf(blocks);
    }

    static SiftBlockModelStates read(InputStream input) throws IOException {
        try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("blocks");
            Map<String, Rule> blocks = new HashMap<>();
            for (Map.Entry<String, JsonElement> block : root.entrySet()) {
                JsonObject state = block.getValue().getAsJsonObject();
                if (state.has("variants")) {
                    Map<Map<String, String>, List<Piece>> variants = new LinkedHashMap<>();
                    state.getAsJsonObject("variants").entrySet()
                        .forEach(variant -> variants.put(properties(variant.getKey()), pieces(variant.getValue())));
                    blocks.put(block.getKey(), new Variants(variants));
                } else {
                    List<Case> cases = new ArrayList<>();
                    for (JsonElement part : state.getAsJsonArray("multipart")) {
                        JsonObject object = part.getAsJsonObject();
                        cases.add(new Case(object.getAsJsonObject("when"), pieces(object.get("apply"))));
                    }
                    blocks.put(block.getKey(), new Multipart(cases));
                }
            }
            return new SiftBlockModelStates(blocks);
        } catch (RuntimeException malformed) {
            throw new IOException("Malformed Sift block models", malformed);
        }
    }

    /** The models drawing look-alike {@code path} in a state; empty when the mod has none for it (a wall sign). */
    List<Piece> pieces(String path, Map<String, String> state) {
        Rule rule = blocks.get(path);
        if (rule instanceof Variants variants) {
            return variants.variants().entrySet().stream()
                .filter(variant -> variant.getKey().entrySet().stream()
                    .allMatch(property -> property.getValue().equals(value(state, property.getKey()))))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(List.of());
        }
        if (rule instanceof Multipart multipart) {
            List<Piece> pieces = new ArrayList<>();
            multipart.cases().stream().filter(part -> matches(part.when(), state)).forEach(part -> pieces.addAll(part.pieces()));
            return pieces;
        }
        return List.of();
    }

    /**
     * Properties from a Bukkit block data string ({@code minecraft:oak_stairs[facing=east,...]}) or from a variant
     * key ({@code facing=east,half=bottom}; empty for a block without properties).
     */
    static Map<String, String> properties(String state) {
        int open = state.indexOf('[');
        if (open < 0 && state.contains(":")) {
            return Map.of();
        }
        String list = open < 0 ? state : state.substring(open + 1, state.length() - (state.endsWith("]") ? 1 : 0));
        Map<String, String> properties = new HashMap<>();
        for (String pair : list.split(",")) {
            int equals = pair.indexOf('=');
            if (equals > 0) {
                properties.put(pair.substring(0, equals).strip(), pair.substring(equals + 1).strip());
            }
        }
        return properties;
    }

    private static boolean matches(JsonObject when, Map<String, String> state) {
        if (when.has("AND")) {
            return all(when.getAsJsonArray("AND"), state, true);
        }
        if (when.has("OR")) {
            return all(when.getAsJsonArray("OR"), state, false);
        }
        return when.entrySet().stream().allMatch(condition -> List.of(condition.getValue().getAsString().split("\\|"))
            .contains(value(state, condition.getKey())));
    }

    private static boolean all(JsonArray conditions, Map<String, String> state, boolean every) {
        for (JsonElement condition : conditions) {
            if (matches(condition.getAsJsonObject(), state) != every) {
                return !every;
            }
        }
        return every;
    }

    private static String value(Map<String, String> state, String property) {
        return Optional.ofNullable(state.get(property)).orElse("false");
    }

    private static List<Piece> pieces(JsonElement apply) {
        List<Piece> pieces = new ArrayList<>();
        for (JsonElement element : apply.getAsJsonArray()) {
            JsonObject piece = element.getAsJsonObject();
            pieces.add(new Piece(piece.get("model").getAsString(), piece.get("x").getAsInt(), piece.get("y").getAsInt()));
        }
        return List.copyOf(pieces);
    }
}
