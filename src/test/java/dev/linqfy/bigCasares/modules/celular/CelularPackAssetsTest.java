package dev.linqfy.bigCasares.modules.celular;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CelularPackAssetsTest {

    private static final Path ASSETS = Path.of("resourcepack/java/assets/celular");

    @Test
    void stillPhoneModelIsThreeDimensionalAndMirrorsTheLeftHandItself() throws IOException {
        JsonObject model = json(ASSETS.resolve("models/item/celular.json"));
        assertEquals("celular:item/celular", model.getAsJsonObject("textures").get("0").getAsString());
        int[] atlas = size(ASSETS.resolve("textures/item/celular.png"));
        assertEquals(64, atlas[0]);
        assertEquals(64, atlas[1]);
        JsonArray elements = model.getAsJsonArray("elements");
        assertTrue(elements.size() >= 5, "body, camera, lenses and buttons");
        for (JsonElement element : elements) {
            for (Map.Entry<String, JsonElement> face : element.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                for (JsonElement value : face.getValue().getAsJsonObject().getAsJsonArray("uv")) {
                    double uv = value.getAsDouble();
                    assertTrue(uv >= 0 && uv <= 16, element.getAsJsonObject().get("name") + " uv out of range");
                }
            }
        }
        JsonObject display = model.getAsJsonObject("display");
        assertEquals(display.get("firstperson_righthand"), display.get("firstperson_lefthand"));
        assertEquals(display.get("thirdperson_righthand"), display.get("thirdperson_lefthand"));
    }

    @Test
    void everyVideoShipsItsAnimatedTextureModelAndItemDefinition() throws IOException {
        for (CelularVideo video : packagedVideos()) {
            Path texture = ASSETS.resolve("textures/item/" + video.id() + ".png");
            int[] size = size(texture);
            assertEquals(72, size[0], video.id());
            assertEquals(128 * video.frames(), size[1], video.id() + ": one 72x128 frame per video frame");

            JsonObject animation = json(Path.of(texture + ".mcmeta")).getAsJsonObject("animation");
            assertEquals(20 / CelularVideo.FPS, animation.get("frametime").getAsInt());
            assertEquals(72, animation.get("width").getAsInt());
            assertEquals(128, animation.get("height").getAsInt());

            String n = video.id().substring("video_".length());
            JsonObject model = json(ASSETS.resolve("models/item/celular_video_" + n + ".json"));
            assertEquals("celular:item/" + video.id(), model.getAsJsonObject("textures").get("1").getAsString());

            JsonObject item = json(ASSETS.resolve("items/" + video.id() + ".json"));
            assertFalse(item.get("hand_animation_on_swap").getAsBoolean(), "changing videos must not bob the phone");
            String definition = item.toString();
            assertTrue(definition.contains("celular:item/celular_video_" + n), "hands play the video");
            assertTrue(definition.contains("\"celular:item/celular\""), "inventory shows the still phone");
        }
    }

    private static List<CelularVideo> packagedVideos() throws IOException {
        try (InputStream stream = CelularPackAssetsTest.class.getClassLoader()
            .getResourceAsStream(CelularVideosLoader.RESOURCE)) {
            return CelularVideosLoader.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }

    private static int[] size(Path png) throws IOException {
        assertTrue(Files.isRegularFile(png), png + " is missing");
        try (ImageInputStream input = ImageIO.createImageInputStream(png.toFile())) {
            ImageReader reader = ImageIO.getImageReaders(input).next();
            reader.setInput(input);
            int[] size = {reader.getWidth(0), reader.getHeight(0)};
            reader.dispose();
            return size;
        }
    }

    private static JsonObject json(Path path) throws IOException {
        assertTrue(Files.isRegularFile(path), path + " is missing");
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
