package dev.linqfy.bigCasares.modules.donpollos;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DonPollosModelAssetsTest {

    @Test
    void everyVariantModelKeyHasAPackagedModel() {
        for (DonPolloVariant variant : DonPolloVariant.values()) {
            String key = "bigcasares_don_pollo_" + variant.id().replace('-', '_');
            assertTrue(DonPollosModelInstaller.MODELS.contains(key), key);
            assertNotNull(load(key));
        }
    }

    @Test
    void modelsUseBlockbench5FormatWithOneEmbeddedTexture() {
        for (String key : DonPollosModelInstaller.MODELS) {
            JsonObject model = load(key);
            assertEquals("5.0", model.getAsJsonObject("meta").get("format_version").getAsString());
            JsonArray textures = model.getAsJsonArray("textures");
            assertEquals(1, textures.size());
            assertTrue(textures.get(0).getAsJsonObject().get("source").getAsString().startsWith("data:image/png;base64,"));
        }
    }

    @Test
    void modelsHaveHeadBoneAndBuiltInAnimations() {
        for (String key : DonPollosModelInstaller.MODELS) {
            JsonObject model = load(key);
            Set<String> groups = names(model.getAsJsonArray("groups"));
            assertTrue(groups.containsAll(Set.of("root", "hip", "torso", "hi_head", "rightarm", "rightforearm",
                "leftarm", "leftforearm", "rightleg", "rightshin", "leftleg", "leftshin")), key);
            Set<String> animations = names(model.getAsJsonArray("animations"));
            assertTrue(animations.containsAll(Set.of("idle", "walk")), key);
        }
        assertTrue(names(load("bigcasares_don_pollo_salsero").getAsJsonArray("animations"))
            .containsAll(Set.of("dance1", "dance2", "dance3", "dance4", "dance5")));
    }

    @Test
    void extraModelsArePackagedAndInstalled() {
        for (String key : DonPollosModelInstaller.EXTRA_MODELS) {
            assertTrue(DonPollosModelInstaller.allModels().contains(key), key);
            JsonObject model = load(key);
            assertEquals("5.0", model.getAsJsonObject("meta").get("format_version").getAsString());
            assertTrue(names(model.getAsJsonArray("animations")).containsAll(Set.of("idle", "walk")), key);
        }
        assertTrue(names(load("bigcasares_gallina_wsp").getAsJsonArray("groups")).contains("hi_head"));
        for (String color : new String[]{"verde", "rojo", "rosa", "azul"}) {
            assertTrue(DonPollosModelInstaller.EXTRA_MODELS.contains("bigcasares_labubu_" + color), color);
        }
    }

    @Test
    void clothingIsHighlightedWithZeroThicknessLayers() {
        for (String key : DonPollosModelInstaller.MODELS) {
            int planes = 0;
            for (JsonElement element : load(key).getAsJsonArray("elements")) {
                JsonObject cube = element.getAsJsonObject();
                JsonArray from = cube.getAsJsonArray("from");
                JsonArray to = cube.getAsJsonArray("to");
                if (from.get(2).getAsDouble() == to.get(2).getAsDouble()) {
                    planes++;
                    assertTrue(cube.get("name").getAsString().startsWith("ropa_"), key);
                }
            }
            assertTrue(planes >= 3, key + " needs a clothing layer");
        }
    }

    private static Set<String> names(JsonArray array) {
        Set<String> names = new HashSet<>();
        array.forEach(item -> names.add(item.getAsJsonObject().get("name").getAsString()));
        return names;
    }

    private static JsonObject load(String key) {
        String resource = "bettermodel/models/" + key + ".bbmodel";
        try (InputStream stream = DonPollosModelAssetsTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
