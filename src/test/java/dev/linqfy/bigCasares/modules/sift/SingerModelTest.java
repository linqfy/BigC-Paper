package dev.linqfy.bigCasares.modules.sift;

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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The converted Singer and Echo Golem models, the poses the plugin plays on them and the assets the creatures use. */
class SingerModelTest {

    private static JsonObject model(String name) throws IOException {
        String resource = "bettermodel/models/" + name + ".bbmodel";
        try (InputStream stream = SingerModelTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, "missing " + resource);
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

    private static Set<String> animations(JsonObject model) {
        Set<String> names = new TreeSet<>();
        for (JsonElement element : model.getAsJsonArray("animations")) {
            names.add(element.getAsJsonObject().get("name").getAsString());
        }
        return names;
    }

    private static Set<String> animatedBones(JsonObject animation) {
        Set<String> bones = new TreeSet<>();
        for (Map.Entry<String, JsonElement> animator : animation.getAsJsonObject("animators").entrySet()) {
            bones.add(animator.getValue().getAsJsonObject().get("name").getAsString());
        }
        return bones;
    }

    private static Set<String> groups(JsonObject model) {
        Set<String> groups = new TreeSet<>();
        for (JsonElement group : model.getAsJsonArray("groups")) {
            groups.add(group.getAsJsonObject().get("name").getAsString());
        }
        return groups;
    }

    @Test
    void theSingerKeepsTheRitualsAnimationsAndTheLengthsMatchTheTimings() throws IOException {
        JsonObject singer = model("the_sift_singer");
        assertEquals("the_sift_singer", singer.get("model_identifier").getAsString());
        // The ritual plays appear, sing and disappear; the soul event adds the rest. Idle and walk are
        // renamed so BetterModel does not start them by itself.
        assertEquals(Set.of("appear", "sing", "disappear", "stand", "stride", "receive_soul", "barter_hold"), animations(singer));
        assertEquals(SingerTimings.MOD.appearTicks(), Math.ceil(animation(singer, "appear").get("length").getAsDouble() * 20));
        assertEquals(SingerTimings.MOD.singTicks(), Math.ceil(animation(singer, "sing").get("length").getAsDouble() * 20));
        assertEquals(SingerTimings.MOD.disappearTicks(), Math.ceil(animation(singer, "disappear").get("length").getAsDouble() * 20));
        assertEquals("hold", animation(singer, "appear").get("loop").getAsString());
        assertEquals("loop", animation(singer, "stand").get("loop").getAsString());
        assertEquals("loop", animation(singer, "stride").get("loop").getAsString());
        assertEquals("loop", animation(singer, "barter_hold").get("loop").getAsString());
        assertEquals("once", animation(singer, "receive_soul").get("loop").getAsString());
        assertEquals(2.4, animation(singer, "receive_soul").get("length").getAsDouble(), 1.0E-9);
        assertEquals(SingerEvent.EXCHANGE_TICKS, Math.round(animation(singer, "receive_soul").get("length").getAsDouble() * 20));
        assertTrue(groups(singer).containsAll(Set.of("held_soul", "head", "body")));
    }

    @Test
    void theEchoGolemHasOnlyTheAnimationsThatMoveSomething() throws IOException {
        JsonObject golem = model("the_sift_echo_golem");
        assertEquals("the_sift_echo_golem", golem.get("model_identifier").getAsString());
        assertEquals(Set.of("search_walk", "pickup", "carry_walk", "bow", "sit", "soul_hidden"), animations(golem));
        for (String pose : List.of("search_walk", "carry_walk")) {
            assertEquals("loop", animation(golem, pose).get("loop").getAsString(), pose);
            assertEquals(Set.of("left_leg", "right_leg", "left_arm", "right_arm"), animatedBones(animation(golem, pose)), pose);
        }
        assertEquals(1.0, animation(golem, "search_walk").get("length").getAsDouble(), 1.0E-9);
        assertEquals(0.9, animation(golem, "carry_walk").get("length").getAsDouble(), 1.0E-9);
        // The pickup lasts as long as the mission's PICKUP state, the bow as long as the player bow.
        assertEquals(EchoGolemBrain.PICKUP_TICKS, Math.round(animation(golem, "pickup").get("length").getAsDouble() * 20));
        assertEquals("once", animation(golem, "pickup").get("loop").getAsString());
        assertEquals(EchoGolemBrain.PLAYER_BOW_TICKS, Math.round(animation(golem, "bow").get("length").getAsDouble() * 20));
        assertEquals("once", animation(golem, "bow").get("loop").getAsString());
        assertEquals("loop", animation(golem, "sit").get("loop").getAsString());
        assertEquals(Set.of("body", "left_leg", "right_leg"), animatedBones(animation(golem, "sit")));
    }

    @Test
    void theSoulBlockBoneIsHiddenByAOneTickLoopWithScaleZero() throws IOException {
        JsonObject golem = model("the_sift_echo_golem");
        JsonObject hidden = animation(golem, "soul_hidden");
        assertEquals("loop", hidden.get("loop").getAsString());
        assertEquals(0.05, hidden.get("length").getAsDouble(), 1.0E-9);
        assertEquals(Set.of("soul_block"), animatedBones(hidden));
        JsonObject keyframe = hidden.getAsJsonObject("animators").entrySet().iterator().next().getValue().getAsJsonObject()
            .getAsJsonArray("keyframes").get(0).getAsJsonObject();
        assertEquals("scale", keyframe.get("channel").getAsString());
        JsonObject point = keyframe.getAsJsonArray("data_points").get(0).getAsJsonObject();
        assertEquals("0", point.get("x").getAsString());
        assertEquals("0", point.get("y").getAsString());
        assertEquals("0", point.get("z").getAsString());
    }

    @Test
    void everyAnimatedBoneExistsAndTheGolemHasTheModsGeometryAndTexture() throws IOException {
        JsonObject golem = model("the_sift_echo_golem");
        assertEquals(Set.of("root", "body", "right_arm", "right_hand", "left_arm", "left_hand", "torso", "head", "top",
            "soul_block", "left_leg", "right_leg"), groups(golem));
        Set<String> uuids = new TreeSet<>();
        for (JsonElement group : golem.getAsJsonArray("groups")) {
            uuids.add(group.getAsJsonObject().get("uuid").getAsString());
        }
        for (JsonElement animation : golem.getAsJsonArray("animations")) {
            for (String animator : animation.getAsJsonObject().getAsJsonObject("animators").keySet()) {
                assertTrue(uuids.contains(animator), "animator " + animator + " is not a bone");
            }
        }
        assertEquals(13, golem.getAsJsonArray("elements").size());
        String source = golem.getAsJsonArray("textures").get(0).getAsJsonObject().get("source").getAsString();
        assertTrue(source.startsWith("data:image/png;base64,"));
    }

    @SuppressWarnings("unchecked")
    private static List<String> installerList(String field) throws ReflectiveOperationException {
        Field list = BetterModelAssetInstaller.class.getDeclaredField(field);
        list.setAccessible(true);
        return new ArrayList<>((List<String>) list.get(null));
    }

    @Test
    void theInstallerRegistersTheGolemFileAndKey() throws ReflectiveOperationException {
        List<String> assets = installerList("SIFT_ASSETS");
        List<String> keys = installerList("SIFT_MODEL_KEYS");
        assertTrue(assets.contains("the_sift_echo_golem.bbmodel"), assets.toString());
        assertTrue(keys.contains(SiftEchoGolems.MODEL), keys.toString());
        assertTrue(keys.contains(SiftSingers.MODEL), keys.toString());
        assertEquals(assets.size(), keys.size(), "one key per packaged file");
        for (String asset : assets) {
            assertNotNull(SingerModelTest.class.getClassLoader().getResource("bettermodel/models/" + asset), asset);
        }
    }

    @Test
    void theSoundsAndNamesTheCreaturesUseExistInTheResourcePack() throws IOException {
        Path assets = Path.of("resourcepack", "java", "assets", "the_sift");
        JsonObject events;
        try (Reader reader = Files.newBufferedReader(assets.resolve("sounds.json"), StandardCharsets.UTF_8)) {
            events = JsonParser.parseReader(reader).getAsJsonObject();
        }
        for (String event : List.of("idle", "hurt", "death", "happy", "emerge", "dig")) {
            assertTrue(events.has("entity.singer." + event), "entity.singer." + event);
        }
        for (String event : List.of("spawn", "item_drop", "item_get", "hurt", "death", "step")) {
            assertTrue(events.has("entity.echo_golem." + event), "entity.echo_golem." + event);
        }
        assertTrue(events.has("the_sift_portal_open") && events.has("the_sift_portal_close"));
        try (Reader reader = Files.newBufferedReader(assets.resolve("lang/en_us.json"), StandardCharsets.UTF_8)) {
            JsonObject lang = JsonParser.parseReader(reader).getAsJsonObject();
            assertEquals("Echo Golem", lang.get("entity.the_sift.echo_golem").getAsString());
            assertEquals("Singer", lang.get("entity.the_sift.singer").getAsString());
        }
        assertTrue(Files.isRegularFile(assets.resolve("items/singer_spawn_egg.json")));
        assertTrue(Files.isRegularFile(assets.resolve("items/echo_golem_spawn_egg.json")));
    }

    @Test
    void theSpawnEggsAreContentItems() {
        List<String> ids = SiftItems.items().stream().map(item -> item.id().asString()).toList();
        assertTrue(ids.contains("the_sift:singer_spawn_egg"));
        assertTrue(ids.contains("the_sift:echo_golem_spawn_egg"));
    }
}
