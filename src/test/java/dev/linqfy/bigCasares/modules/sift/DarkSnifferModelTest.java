package dev.linqfy.bigCasares.modules.sift;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.linqfy.bigCasares.modules.model.BetterModelAssetInstaller;
import org.bukkit.entity.Sniffer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The generated Dark Sniffer model: the vanilla sniffer's parts with the mod's texture, and the files around it. */
class DarkSnifferModelTest {

    private static final String MODEL_RESOURCE = "bettermodel/models/the_sift_dark_sniffer.bbmodel";
    private static final Set<String> BONES = Set.of("bone", "body", "right_front_leg", "right_mid_leg", "right_hind_leg",
        "left_front_leg", "left_mid_leg", "left_hind_leg", "head", "left_ear", "right_ear", "nose", "lower_beak");

    private static JsonObject model() throws IOException {
        try (InputStream stream = DarkSnifferModelTest.class.getClassLoader().getResourceAsStream(MODEL_RESOURCE)) {
            assertNotNull(stream, "missing " + MODEL_RESOURCE);
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static JsonObject parts() throws IOException {
        return JsonParser.parseString(Files.readString(Path.of("tools/sift/sniffer_parts.json"), StandardCharsets.UTF_8))
            .getAsJsonObject();
    }

    @Test
    void theModelHasTheSnifferPartsAndTheDescriptionItWasGeneratedFrom() throws IOException {
        JsonObject model = model();
        assertEquals("the_sift_dark_sniffer", model.get("model_identifier").getAsString());
        Set<String> groups = new TreeSet<>();
        for (JsonElement group : model.getAsJsonArray("groups")) {
            groups.add(group.getAsJsonObject().get("name").getAsString());
        }
        assertEquals(new TreeSet<>(BONES), groups);
        int cubes = 0;
        Set<String> described = new TreeSet<>();
        for (JsonElement part : parts().getAsJsonArray("parts")) {
            described.add(part.getAsJsonObject().get("name").getAsString());
            JsonArray partCubes = part.getAsJsonObject().getAsJsonArray("cubes");
            cubes += partCubes == null ? 0 : partCubes.size();
        }
        assertEquals(groups, described);
        assertEquals(cubes, model.getAsJsonArray("elements").size());
        assertEquals(15, cubes);
    }

    @Test
    void theTextureIsTheModsOneHundredNinetyTwoSquareImageAndEveryFaceIsInsideIt() throws IOException {
        JsonObject model = model();
        JsonObject texture = model.getAsJsonArray("textures").get(0).getAsJsonObject();
        String source = texture.get("source").getAsString();
        assertTrue(source.startsWith("data:image/png;base64,"));
        byte[] png = Base64.getDecoder().decode(source.substring("data:image/png;base64,".length()));
        assertEquals((byte) 0x89, png[0]);
        assertEquals(192, texture.get("width").getAsInt());
        assertEquals(192, texture.get("height").getAsInt());
        assertEquals(192, model.getAsJsonObject("resolution").get("width").getAsInt());
        for (JsonElement element : model.getAsJsonArray("elements")) {
            for (Map.Entry<String, JsonElement> face : element.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                for (JsonElement coordinate : face.getValue().getAsJsonObject().getAsJsonArray("uv")) {
                    double value = coordinate.getAsDouble();
                    assertTrue(value >= 0 && value <= 192, element.getAsJsonObject().get("name") + " " + face.getKey() + " " + value);
                }
            }
        }
    }

    @Test
    void theLegsStandOnTheGroundAndTheBodySitsAboveThem() throws IOException {
        double lowest = Double.MAX_VALUE;
        double bodyBottom = Double.MAX_VALUE;
        for (JsonElement element : model().getAsJsonArray("elements")) {
            JsonObject cube = element.getAsJsonObject();
            double bottom = cube.getAsJsonArray("from").get(1).getAsDouble();
            lowest = Math.min(lowest, bottom);
            if (cube.get("name").getAsString().startsWith("body_0")) {
                bodyBottom = Math.min(bodyBottom, bottom);
            }
        }
        assertEquals(0.0, lowest, 1.0E-9, "the feet are at the entity's feet");
        assertEquals(4.0, bodyBottom, 1.0E-9, "the 29 pixel body box starts four pixels up");
    }

    @Test
    void itAnimatesWalkAndIdleByItselfAndTheSnifferStatesByName() throws IOException {
        JsonObject model = model();
        Set<String> names = new TreeSet<>();
        Set<String> uuids = new TreeSet<>();
        for (JsonElement group : model.getAsJsonArray("groups")) {
            uuids.add(group.getAsJsonObject().get("uuid").getAsString());
        }
        for (JsonElement element : model.getAsJsonArray("animations")) {
            JsonObject animation = element.getAsJsonObject();
            names.add(animation.get("name").getAsString());
            double last = 0.0;
            for (Map.Entry<String, JsonElement> animator : animation.getAsJsonObject("animators").entrySet()) {
                assertTrue(uuids.contains(animator.getKey()), "animator is not a bone");
                for (JsonElement frame : animator.getValue().getAsJsonObject().getAsJsonArray("keyframes")) {
                    last = Math.max(last, frame.getAsJsonObject().get("time").getAsDouble());
                }
            }
            assertEquals(last, animation.get("length").getAsDouble(), 1.0E-9, animation.get("name").getAsString());
        }
        assertEquals(Set.of("walk", "idle", "sniff", "search", "dig", "rise"), names);
        for (Sniffer.State state : List.of(Sniffer.State.DIGGING, Sniffer.State.SNIFFING, Sniffer.State.SCENTING,
            Sniffer.State.SEARCHING, Sniffer.State.RISING)) {
            assertTrue(names.contains(SiftDarkSniffers.animationFor(state)), state.name());
        }
        assertEquals(null, SiftDarkSniffers.animationFor(Sniffer.State.IDLING));
        assertEquals(null, SiftDarkSniffers.animationFor(Sniffer.State.FEELING_HAPPY));
    }

    @Test
    void theLoopsAreLoopsAndTheRiseIsOnce() throws IOException {
        Map<String, String> loops = new java.util.HashMap<>();
        for (JsonElement element : model().getAsJsonArray("animations")) {
            loops.put(element.getAsJsonObject().get("name").getAsString(), element.getAsJsonObject().get("loop").getAsString());
        }
        assertEquals("once", loops.get("rise"));
        for (String name : List.of("walk", "idle", "sniff", "search", "dig")) {
            assertEquals("loop", loops.get(name), name);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> installerList(String field) throws ReflectiveOperationException {
        Field list = BetterModelAssetInstaller.class.getDeclaredField(field);
        list.setAccessible(true);
        return new ArrayList<>((List<String>) list.get(null));
    }

    @Test
    void theInstallerRegistersTheModelFileAndKey() throws ReflectiveOperationException {
        List<String> assets = installerList("SIFT_ASSETS");
        List<String> keys = installerList("SIFT_MODEL_KEYS");
        assertTrue(assets.contains("the_sift_dark_sniffer.bbmodel"), assets.toString());
        assertTrue(keys.contains(SiftDarkSniffers.MODEL), keys.toString());
        assertEquals(assets.size(), keys.size(), "one key per packaged file");
    }

    @Test
    void theSoundsAndNameTheSnifferUsesExistInTheResourcePack() throws IOException {
        JsonObject sounds = JsonParser.parseString(Files.readString(Path.of("resourcepack/java/assets/the_sift/sounds.json"),
            StandardCharsets.UTF_8)).getAsJsonObject();
        assertTrue(sounds.has("entity.dark_sniffer.sniff"));
        assertTrue(sounds.has("block.dark_sniffer.bloom"));
        assertEquals("the_sift:" + "entity.dark_sniffer.sniff", SiftDarkSniffers.SNIFF_SOUND);
        assertEquals("the_sift:" + "block.dark_sniffer.bloom", SiftDarkSniffers.BLOOM_SOUND);
        JsonObject lang = JsonParser.parseString(Files.readString(Path.of("resourcepack/java/assets/the_sift/lang/en_us.json"),
            StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("Dark Sniffer", lang.get("entity.the_sift.dark_sniffer").getAsString());
    }

    @Test
    void theHomeIsSavedAsThreeNumbers() {
        SiftGeometry.Vec home = new SiftGeometry.Vec(-12.5, 70.0, 300.25);
        assertEquals(java.util.Optional.of(home), SiftDarkSniffers.parseHome(SiftDarkSniffers.formatHome(home)));
        assertEquals(java.util.Optional.empty(), SiftDarkSniffers.parseHome("1,2"));
        assertEquals(java.util.Optional.empty(), SiftDarkSniffers.parseHome("a,b,c"));
    }
}
