package dev.linqfy.bigCasares.modules.celular;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CelularBedrockAssetsTest {

    private static final Path BEDROCK = Path.of("resourcepack/bedrock");

    @Test
    void everyGeyserItemHasAnAttachableWithThePhoneModel() throws IOException {
        List<String> models = new CelularService(packagedVideos(), CelularSettings.defaults()).itemModels();
        assertEquals(packagedVideos().size() + 1, models.size());
        for (String model : models) {
            Path file = BEDROCK.resolve("attachables/celular_" + model.substring("celular:".length()) + ".json");
            JsonObject description = json(file).getAsJsonObject("minecraft:attachable").getAsJsonObject("description");
            assertEquals(model, description.get("identifier").getAsString());
            assertEquals("geometry.celular", description.getAsJsonObject("geometry").get("default").getAsString());
            assertEquals("textures/entity/celular_bedrock",
                description.getAsJsonObject("textures").get("default").getAsString());
        }
    }

    @Test
    void geometryHasEveryPieceOfTheJavaPhone() throws IOException {
        JsonObject geometry = json(BEDROCK.resolve("models/entity/celular.geo.json"))
            .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        assertEquals("geometry.celular", geometry.getAsJsonObject("description").get("identifier").getAsString());
        int javaPieces = json(Path.of("resourcepack/java/assets/celular/models/item/celular.json"))
            .getAsJsonArray("elements").size();
        int bedrockPieces = 0;
        for (var bone : geometry.getAsJsonArray("bones")) {
            if (bone.getAsJsonObject().has("cubes")) {
                bedrockPieces += bone.getAsJsonObject().getAsJsonArray("cubes").size();
            }
        }
        assertEquals(javaPieces, bedrockPieces);
        JsonObject animations = json(BEDROCK.resolve("animations/celular.animation.json")).getAsJsonObject("animations");
        assertTrue(animations.has("animation.celular.firstperson_main_hand"));
        assertTrue(animations.has("animation.celular.thirdperson_main_hand"));
    }

    @Test
    void screenTextureAndInventoryIconExistAndTheIconIsRegistered() throws IOException {
        BufferedImage texture = ImageIO.read(BEDROCK.resolve("textures/entity/celular_bedrock.png").toFile());
        assertNotNull(texture);
        assertEquals(64, texture.getWidth());
        assertEquals(64, texture.getHeight());
        assertNotNull(ImageIO.read(Path.of("resourcepack/shared/textures/item/celular_bedrock.png").toFile()));
        String registry = Files.readString(Path.of("resourcepack/shared/registry.yml"));
        assertTrue(registry.contains("  celular:"));
        assertTrue(registry.contains("texture: item/celular_bedrock"));
        assertEquals("bigcasares.celular", CelularModule.BEDROCK_ICON);
    }

    @Test
    void geyserModuleRegistersThePhoneItems() throws IOException {
        String geyser = Files.readString(
            Path.of("src/main/java/dev/linqfy/bigCasares/modules/geyser/GeyserIntegrationModule.java"));
        assertTrue(geyser.contains("celularItemDefinitions().forEach(runtime::registerCustomItem)"));
        assertTrue(geyser.contains("\"minecraft:clock\""));
    }

    private static List<CelularVideo> packagedVideos() throws IOException {
        try (InputStream stream = CelularBedrockAssetsTest.class.getClassLoader()
            .getResourceAsStream(CelularVideosLoader.RESOURCE)) {
            return CelularVideosLoader.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }

    private static JsonObject json(Path path) throws IOException {
        assertTrue(Files.isRegularFile(path), path + " is missing");
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
