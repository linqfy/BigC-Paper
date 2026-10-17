package dev.linqfy.bigCasares.modules.sift;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.linqfy.bigCasares.modules.model.BetterModelAssetInstaller;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
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

/** The converted Sifter model and the assets the creature depends on. */
class SifterModelTest {

    private static final String MODEL_RESOURCE = "bettermodel/models/the_sift_sifter.bbmodel";

    private static JsonObject model() throws IOException {
        try (InputStream stream = SifterModelTest.class.getClassLoader().getResourceAsStream(MODEL_RESOURCE)) {
            assertNotNull(stream, "missing " + MODEL_RESOURCE);
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static JsonObject animation(JsonObject model, String name) {
        for (JsonElement element : model.getAsJsonArray("animations")) {
            if (element.getAsJsonObject().get("name").getAsString().equals(name)) {
                return element.getAsJsonObject();
            }
        }
        throw new AssertionError("no animation " + name);
    }

    private static double lastKeyframe(JsonObject animation) {
        double last = 0.0;
        for (Map.Entry<String, JsonElement> animator : animation.getAsJsonObject("animators").entrySet()) {
            for (JsonElement frame : animator.getValue().getAsJsonObject().getAsJsonArray("keyframes")) {
                last = Math.max(last, frame.getAsJsonObject().get("time").getAsDouble());
            }
        }
        return last;
    }

    @Test
    void theModelNamesItsAnimationsSoBetterModelPlaysWalkAndTheSifterPlaysAttack() throws IOException {
        JsonObject model = model();
        Set<String> names = new TreeSet<>();
        for (JsonElement element : model.getAsJsonArray("animations")) {
            names.add(element.getAsJsonObject().get("name").getAsString());
        }
        // The mod has a running loop and a one-shot attack, and no idle animation.
        assertEquals(Set.of("attack", "walk"), names);
        assertEquals("the_sift_sifter", model.get("model_identifier").getAsString());
    }

    @Test
    void walkIsTheModsRunningLoopAtItsTwoAndAHalfTimesSpeed() throws IOException {
        JsonObject walk = animation(model(), "walk");
        assertEquals("loop", walk.get("loop").getAsString());
        // 5.5 seconds in the mod, played at 2.5x.
        assertEquals(2.2, walk.get("length").getAsDouble(), 1.0E-9);
        assertEquals(2.2, lastKeyframe(walk), 1.0E-9);
        Set<String> bones = new TreeSet<>();
        for (Map.Entry<String, JsonElement> animator : walk.getAsJsonObject("animators").entrySet()) {
            bones.add(animator.getValue().getAsJsonObject().get("name").getAsString());
        }
        assertEquals(Set.of("head", "head_adds", "front_right_leg", "front_left_leg", "back_right_leg", "back_left_leg", "body"), bones);
    }

    @Test
    void attackIsTheModsBiteAtNormalSpeed() throws IOException {
        JsonObject attack = animation(model(), "attack");
        assertEquals("once", attack.get("loop").getAsString());
        assertEquals(0.6667, attack.get("length").getAsDouble(), 1.0E-9);
        assertEquals(0.6667, lastKeyframe(attack), 1.0E-9);
        Set<String> bones = new TreeSet<>();
        for (Map.Entry<String, JsonElement> animator : attack.getAsJsonObject("animators").entrySet()) {
            bones.add(animator.getValue().getAsJsonObject().get("name").getAsString());
        }
        assertEquals(Set.of("head", "head_adds"), bones);
    }

    @Test
    void everyAnimatedBoneExistsAndTheModelHasTheModsGeometryAndTexture() throws IOException {
        JsonObject model = model();
        Set<String> groups = new TreeSet<>();
        Set<String> uuids = new TreeSet<>();
        for (JsonElement group : model.getAsJsonArray("groups")) {
            groups.add(group.getAsJsonObject().get("name").getAsString());
            uuids.add(group.getAsJsonObject().get("uuid").getAsString());
        }
        assertEquals(Set.of("root", "body", "head", "head_adds", "tooth", "front_right_leg", "front_left_leg",
            "back_right_leg", "back_left_leg", "torso", "teeth"), groups);
        for (JsonElement animation : model.getAsJsonArray("animations")) {
            for (String animator : animation.getAsJsonObject().getAsJsonObject("animators").keySet()) {
                assertTrue(uuids.contains(animator), "animator " + animator + " is not a bone");
            }
        }
        assertEquals(10, model.getAsJsonArray("elements").size());
        JsonArray textures = model.getAsJsonArray("textures");
        assertEquals(1, textures.size());
        String source = textures.get(0).getAsJsonObject().get("source").getAsString();
        assertTrue(source.startsWith("data:image/png;base64,"));
        byte[] png = Base64.getDecoder().decode(source.substring("data:image/png;base64,".length()));
        assertEquals((byte) 0x89, png[0]);
        assertEquals('P', png[1]);
    }

    @SuppressWarnings("unchecked")
    private static List<String> installerList(String field) throws ReflectiveOperationException {
        Field list = BetterModelAssetInstaller.class.getDeclaredField(field);
        list.setAccessible(true);
        return new ArrayList<>((List<String>) list.get(null));
    }

    @Test
    void theInstallerRegistersTheSifterFileAndKey() throws ReflectiveOperationException {
        List<String> assets = installerList("SIFT_ASSETS");
        List<String> keys = installerList("SIFT_MODEL_KEYS");
        assertTrue(assets.contains("the_sift_sifter.bbmodel"), assets.toString());
        assertTrue(keys.contains(SiftSifters.MODEL), keys.toString());
        assertEquals("the_sift_sifter", SiftSifters.MODEL);
        assertEquals(assets.size(), keys.size(), "one key per packaged file");
        for (String asset : assets) {
            assertNotNull(SifterModelTest.class.getClassLoader().getResource("bettermodel/models/" + asset), asset);
        }
    }

    @Test
    void theSoundsTheSifterPlaysExistInTheResourcePack() throws IOException {
        Path sounds = Path.of("resourcepack", "java", "assets", "the_sift", "sounds.json");
        try (Reader reader = Files.newBufferedReader(sounds, StandardCharsets.UTF_8)) {
            JsonObject events = JsonParser.parseReader(reader).getAsJsonObject();
            for (String event : List.of("ambient", "hurt", "death", "step", "attack")) {
                assertTrue(events.has("entity.sifter." + event), "entity.sifter." + event);
            }
        }
        Path lang = Path.of("resourcepack", "java", "assets", "the_sift", "lang", "en_us.json");
        try (Reader reader = Files.newBufferedReader(lang, StandardCharsets.UTF_8)) {
            assertEquals("Sifter", JsonParser.parseReader(reader).getAsJsonObject().get("entity.the_sift.sifter").getAsString());
        }
    }
}
