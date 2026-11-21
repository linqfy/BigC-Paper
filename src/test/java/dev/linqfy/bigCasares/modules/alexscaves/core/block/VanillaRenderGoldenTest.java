package dev.linqfy.bigCasares.modules.alexscaves.core.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pack layer replaces vanilla's {@code tripwire.json} and {@code chorus_plant.json}. Every state that is not a
 * module block must still draw exactly what the vanilla 26.2 definitions draw (the rules below were checked against
 * {@code assets/minecraft/blockstates} of the 26.2 client): string with its powered and disarmed flags ignored by
 * the model, chorus plant with a side arm or a randomly rotated "no side" cap per face.
 */
class VanillaRenderGoldenTest {
    private static final Path LAYER = Path.of("src/main/resources/alexscaves/pack-layer/assets/minecraft/blockstates");

    /** Vanilla tripwire.json: north,east,south,west -> model suffix and y rotation. */
    private static final Map<String, String> TRIPWIRE = Map.ofEntries(
        Map.entry("0000", "ns 0"), Map.entry("0001", "n 270"), Map.entry("0010", "n 180"), Map.entry("0011", "ne 180"),
        Map.entry("0100", "n 90"), Map.entry("0101", "ns 90"), Map.entry("0110", "ne 90"), Map.entry("0111", "nse 90"),
        Map.entry("1000", "n 0"), Map.entry("1001", "ne 270"), Map.entry("1010", "ns 0"), Map.entry("1011", "nse 180"),
        Map.entry("1100", "ne 0"), Map.entry("1101", "nse 270"), Map.entry("1110", "nse 0"), Map.entry("1111", "nsew 0"));

    /** Vanilla chorus_plant.json multipart: {@code [when property, value, apply]}. */
    private static final String[][] CHORUS = {
        {"north", "true", "{\"model\":\"minecraft:block/chorus_plant_side\"}"},
        {"east", "true", "{\"model\":\"minecraft:block/chorus_plant_side\",\"uvlock\":true,\"y\":90}"},
        {"south", "true", "{\"model\":\"minecraft:block/chorus_plant_side\",\"uvlock\":true,\"y\":180}"},
        {"west", "true", "{\"model\":\"minecraft:block/chorus_plant_side\",\"uvlock\":true,\"y\":270}"},
        {"up", "true", "{\"model\":\"minecraft:block/chorus_plant_side\",\"uvlock\":true,\"x\":270}"},
        {"down", "true", "{\"model\":\"minecraft:block/chorus_plant_side\",\"uvlock\":true,\"x\":90}"},
        {"north", "false", "[{\"model\":\"minecraft:block/chorus_plant_noside\",\"weight\":2},"
            + "{\"model\":\"minecraft:block/chorus_plant_noside1\"},{\"model\":\"minecraft:block/chorus_plant_noside2\"},"
            + "{\"model\":\"minecraft:block/chorus_plant_noside3\"}]"},
        {"east", "false", "[{\"model\":\"minecraft:block/chorus_plant_noside1\",\"uvlock\":true,\"y\":90},"
            + "{\"model\":\"minecraft:block/chorus_plant_noside2\",\"uvlock\":true,\"y\":90},"
            + "{\"model\":\"minecraft:block/chorus_plant_noside3\",\"uvlock\":true,\"y\":90},"
            + "{\"model\":\"minecraft:block/chorus_plant_noside\",\"uvlock\":true,\"weight\":2,\"y\":90}]"},
        {"south", "false", "[{\"model\":\"minecraft:block/chorus_plant_noside2\",\"uvlock\":true,\"y\":180},"
            + "{\"model\":\"minecraft:block/chorus_plant_noside3\",\"uvlock\":true,\"y\":180},"
            + "{\"model\":\"minecraft:block/chorus_plant_noside\",\"uvlock\":true,\"weight\":2,\"y\":180},"
            + "{\"model\":\"minecraft:block/chorus_plant_noside1\",\"uvlock\":true,\"y\":180}]"},
        {"west", "false", "[{\"model\":\"minecraft:block/chorus_plant_noside3\",\"uvlock\":true,\"y\":270},"
            + "{\"model\":\"minecraft:block/chorus_plant_noside\",\"uvlock\":true,\"weight\":2,\"y\":270},"
            + "{\"model\":\"minecraft:block/chorus_plant_noside1\",\"uvlock\":true,\"y\":270},"
            + "{\"model\":\"minecraft:block/chorus_plant_noside2\",\"uvlock\":true,\"y\":270}]"},
        {"up", "false", "[{\"model\":\"minecraft:block/chorus_plant_noside\",\"uvlock\":true,\"weight\":2,\"x\":270},"
            + "{\"model\":\"minecraft:block/chorus_plant_noside3\",\"uvlock\":true,\"x\":270},"
            + "{\"model\":\"minecraft:block/chorus_plant_noside1\",\"uvlock\":true,\"x\":270},"
            + "{\"model\":\"minecraft:block/chorus_plant_noside2\",\"uvlock\":true,\"x\":270}]"},
        {"down", "false", "[{\"model\":\"minecraft:block/chorus_plant_noside3\",\"uvlock\":true,\"x\":90},"
            + "{\"model\":\"minecraft:block/chorus_plant_noside2\",\"uvlock\":true,\"x\":90},"
            + "{\"model\":\"minecraft:block/chorus_plant_noside1\",\"uvlock\":true,\"x\":90},"
            + "{\"model\":\"minecraft:block/chorus_plant_noside\",\"uvlock\":true,\"weight\":2,\"x\":90}]"}};

    private static ModBlockRegistry registry() throws IOException {
        return ModBlockRegistry.parse(Files.readString(Path.of("src/main/resources/alexscaves/blocks.yml")));
    }

    @Test
    void everyNonModuleTripwireStateDrawsAsVanillaDoes() throws IOException {
        ModBlockRegistry registry = registry();
        JsonObject variants = JsonParser.parseString(Files.readString(LAYER.resolve("tripwire.json")))
            .getAsJsonObject().getAsJsonObject("variants");
        int checked = 0;
        for (int index = 0; index < BlockBase.TRIPWIRE.count(); index++) {
            if (registry.at(new CustomBlockState(BlockBase.TRIPWIRE, index)).isPresent()) {
                continue;
            }
            Map<String, String> state = BlockBase.TRIPWIRE.properties(index);
            String key = String.join(",", state.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList());
            JsonObject variant = variants.getAsJsonObject(key);
            assertTrue(variant != null, "pack layer lacks " + key);
            String sides = bit(state, "north") + bit(state, "east") + bit(state, "south") + bit(state, "west");
            String[] expected = TRIPWIRE.get(sides).split(" ");
            String prefix = state.get("attached").equals("true") ? "minecraft:block/tripwire_attached_" : "minecraft:block/tripwire_";
            assertEquals(prefix + expected[0], variant.get("model").getAsString(), key);
            assertEquals(Integer.parseInt(expected[1]), variant.has("y") ? variant.get("y").getAsInt() : 0, key);
            assertFalse(variant.has("x") || variant.has("uvlock") || variant.has("weight"), key);
            checked++;
        }
        assertEquals(128 - 63, checked, "every state that is not a module block");
    }

    @Test
    void everyNonModuleChorusStateDrawsAsVanillaDoes() throws IOException {
        ModBlockRegistry registry = registry();
        JsonArray parts = JsonParser.parseString(Files.readString(LAYER.resolve("chorus_plant.json")))
            .getAsJsonObject().getAsJsonArray("multipart");
        int checked = 0;
        for (int index = 0; index < BlockBase.CHORUS_PLANT.count(); index++) {
            if (registry.at(new CustomBlockState(BlockBase.CHORUS_PLANT, index)).isPresent()) {
                continue;
            }
            Map<String, String> state = BlockBase.CHORUS_PLANT.properties(index);
            List<JsonElement> drawn = new ArrayList<>();
            for (JsonElement part : parts) {
                if (matches(part.getAsJsonObject().get("when").getAsJsonObject(), state)) {
                    drawn.add(part.getAsJsonObject().get("apply"));
                }
            }
            List<JsonElement> vanilla = new ArrayList<>();
            for (String[] part : CHORUS) {
                if (part[1].equals(state.get(part[0]))) {
                    vanilla.add(JsonParser.parseString(part[2]));
                }
            }
            assertEquals(6, vanilla.size());
            assertEquals(vanilla, drawn, "chorus plant " + state);
            checked++;
        }
        assertEquals(64 - 22, checked);
    }

    private static String bit(Map<String, String> state, String name) {
        return state.get(name).equals("true") ? "1" : "0";
    }

    private static boolean matches(JsonObject when, Map<String, String> state) {
        if (when.has("OR")) {
            for (JsonElement option : when.getAsJsonArray("OR")) {
                if (matches(option.getAsJsonObject(), state)) {
                    return true;
                }
            }
            return false;
        }
        for (Map.Entry<String, JsonElement> entry : when.entrySet()) {
            if (!entry.getValue().getAsString().equals(state.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }
}
