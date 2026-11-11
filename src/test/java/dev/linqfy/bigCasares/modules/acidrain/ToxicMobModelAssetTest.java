package dev.linqfy.bigCasares.modules.acidrain;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.linqfy.bigCasares.modules.model.JavaModelKeys;
import dev.linqfy.bigCasares.testsupport.CompiledClass;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Set;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract for the BetterModel asset shared by the Acid Rain toxic mobs:
 * the packaged model parses as animated geometry, references the packaged
 * texture, and the installer + model key wiring is present.
 */
class ToxicMobModelAssetTest {

    private static final Path PACKAGED = Path.of(
        "src", "main", "resources", "bettermodel", "models"
    );
    private static final Path INSTALLED = Path.of(
        "build", "run-server", "plugins", "BetterModel", "models"
    );

    @Test
    void packagedModelHasStableAnimatedCubeGeometry() throws Exception {
        JsonObject model = readModel(PACKAGED.resolve("toxic_mob.bbmodel"));

        assertEquals("toxic_mob", model.get("name").getAsString());
        assertEquals(128, model.getAsJsonObject("resolution").get("width").getAsInt());
        assertEquals(128, model.getAsJsonObject("resolution").get("height").getAsInt());

        JsonArray elements = model.getAsJsonArray("elements");
        assertTrue(elements.size() >= 30, "toxic mutant needs a distinctive detailed silhouette");
        assertTrue(StreamSupport.stream(elements.spliterator(), false)
            .allMatch(element -> element.getAsJsonObject().get("type").getAsString().equals("cube")),
            "every element must be a box cube");
        Set<String> cubeNames = StreamSupport.stream(elements.spliterator(), false)
            .map(element -> element.getAsJsonObject().get("name").getAsString())
            .collect(Collectors.toSet());
        assertTrue(cubeNames.containsAll(Set.of(
            "MutatedBody", "Jaw", "EyeLeft", "EyeRight",
            "AcidSacCenter", "FrontClawLeft", "FrontClawRight", "DorsalSpikeCenter"
        )));

        assertValidAnimations(model);
    }

    @Test
    void packagedTextureMatchesThePngFile() throws Exception {
        JsonObject model = readModel(PACKAGED.resolve("toxic_mob.bbmodel"));
        JsonObject texture = model.getAsJsonArray("textures").get(0).getAsJsonObject();

        assertEquals("toxic_mob.png", texture.get("relative_path").getAsString());
        assertEquals(128, texture.get("width").getAsInt());
        assertEquals(128, texture.get("height").getAsInt());

        byte[] fileBytes = Files.readAllBytes(PACKAGED.resolve("toxic_mob.png"));
        assertArrayEquals(fileBytes, Base64.getDecoder().decode(
            texture.get("source").getAsString().substring("data:image/png;base64,".length())));

        assertEquals((byte) 0x89, fileBytes[0]);
        assertEquals((byte) 0x50, fileBytes[1]);
        assertEquals(128, pngDimension(fileBytes, 16));
    }

    @Test
    void installerWiresTheToxicMobModelAndKey() throws Exception {
        // BetterModel classes cannot be loaded in unit tests, so the installer is read from its bytecode.
        CompiledClass installer = CompiledClass.of(getClass(),
            "dev.linqfy.bigCasares.modules.model.BetterModelAssetInstaller");
        assertTrue(installer.hasConstant("installToxicMobModel"));
        for (String asset : List.of("toxic_mob.bbmodel", "toxic_mob.png", "toxic_brute.bbmodel", "toxic_brute.png",
            "toxic_spitter.bbmodel", "toxic_spitter.png")) {
            assertTrue(installer.hasConstant(asset), asset);
        }
        assertEquals("toxic_mob", JavaModelKeys.TOXIC_MOB);
        assertEquals("toxic_brute", JavaModelKeys.TOXIC_BRUTE);
        assertEquals("toxic_spitter", JavaModelKeys.TOXIC_SPITTER);
    }

    @Test
    void preparedRunServerCopyIsInstalledByTheTestTask() throws Exception {
        assertTrue(Files.isRegularFile(INSTALLED.resolve("toxic_mob.bbmodel")),
            "prepareBetterModelTestAssets must copy toxic_mob.bbmodel to the run server");
        assertTrue(Files.isRegularFile(INSTALLED.resolve("toxic_mob.png")),
            "prepareBetterModelTestAssets must copy toxic_mob.png to the run server");
        assertTrue(Files.isRegularFile(INSTALLED.resolve("toxic_brute.bbmodel")));
        assertTrue(Files.isRegularFile(INSTALLED.resolve("toxic_brute.png")));
        assertTrue(Files.isRegularFile(INSTALLED.resolve("toxic_spitter.bbmodel")));
        assertTrue(Files.isRegularFile(INSTALLED.resolve("toxic_spitter.png")));
    }

    @Test
    void bruteAndSpitterHaveIndependentDetailedModelsAndTextures() throws Exception {
        for (String key : List.of("toxic_brute", "toxic_spitter")) {
            JsonObject model = readModel(PACKAGED.resolve(key + ".bbmodel"));
            assertEquals(key, model.get("name").getAsString());
            assertTrue(model.getAsJsonArray("elements").size() >= 20,
                key + " needs its own detailed silhouette");
            assertValidAnimations(model);

            JsonObject texture = model.getAsJsonArray("textures").get(0).getAsJsonObject();
            assertEquals(key + ".png", texture.get("relative_path").getAsString());
            byte[] fileBytes = Files.readAllBytes(PACKAGED.resolve(key + ".png"));
            assertArrayEquals(fileBytes, Base64.getDecoder().decode(
                texture.get("source").getAsString().substring("data:image/png;base64,".length())));
        }
    }

    private static JsonObject readModel(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static int pngDimension(byte[] bytes, int offset) {
        return (bytes[offset] & 255) << 24 | (bytes[offset + 1] & 255) << 16
            | (bytes[offset + 2] & 255) << 8 | bytes[offset + 3] & 255;
    }

    private static void assertValidAnimations(JsonObject model) {
        Set<String> groupIds = StreamSupport.stream(
            model.getAsJsonArray("groups").spliterator(), false)
            .map(group -> group.getAsJsonObject().get("uuid").getAsString())
            .collect(Collectors.toSet());
        JsonArray animations = model.getAsJsonArray("animations");
        Set<String> names = StreamSupport.stream(animations.spliterator(), false)
            .map(animation -> animation.getAsJsonObject().get("name").getAsString())
            .collect(Collectors.toSet());

        assertEquals(Set.of("idle", "walk", "attack", "hurt"), names);
        for (var animationElement : animations) {
            JsonObject animation = animationElement.getAsJsonObject();
            assertTrue(animation.get("length").getAsDouble() > 0.0);
            JsonObject animators = animation.getAsJsonObject("animators");
            assertTrue(animators.size() > 0);
            for (String animatorId : animators.keySet()) {
                assertTrue(groupIds.contains(animatorId),
                    () -> "animation references missing bone " + animatorId);
                JsonArray keyframes = animators.getAsJsonObject(animatorId).getAsJsonArray("keyframes");
                assertTrue(keyframes.size() >= 2);
            }
        }
    }
}
