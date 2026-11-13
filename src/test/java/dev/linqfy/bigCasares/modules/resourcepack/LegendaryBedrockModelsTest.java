package dev.linqfy.bigCasares.modules.resourcepack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegendaryBedrockModelsTest {
    @TempDir static Path output;

    @BeforeAll static void buildBothPacksFromTheSameEditableSources() throws Exception {
        new LegendaryItemModels().compile(Path.of("resourcepack"), output.resolve("java"), output.resolve("bedrock"));
    }

    private static JsonObject read(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    @Test void everyWeaponKeepsItsTextureVisibleFacesAndHeldGeometryOnBothPlatforms() throws Exception {
        for (String id : List.of("bloodlust", "shadow_blade", "windweaver", "earthbreaker", "thunder_spear")) {
            Path java = output.resolve("java/assets/bigcasares"), bedrock = output.resolve("bedrock");
            assertArrayEquals(Files.readAllBytes(java.resolve("textures/item/user/" + id + ".png")),
                    Files.readAllBytes(bedrock.resolve("textures/item/legendary/" + id + ".png")));
            var elements = read(java.resolve("models/item/" + id + ".json")).getAsJsonArray("elements");
            var geometry = read(bedrock.resolve("models/entity/legendary/" + id + ".geo.json"))
                    .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
            var cubes = geometry.getAsJsonArray("bones").get(3).getAsJsonObject().getAsJsonArray("cubes");
            assertEquals(elements.size(), cubes.size());
            for (int i = 0; i < elements.size(); i++) {
                var source = elements.get(i).getAsJsonObject();
                var converted = cubes.get(i).getAsJsonObject();
                assertEquals(source.getAsJsonObject("faces").keySet(), converted.getAsJsonObject("uv").keySet());
                for (int axis = 0; axis < 3; axis++) assertEquals(
                        source.getAsJsonArray("to").get(axis).getAsDouble() - source.getAsJsonArray("from").get(axis).getAsDouble(),
                        converted.getAsJsonArray("size").get(axis).getAsDouble(), 1e-8);
            }
            var description = read(bedrock.resolve("attachables/legendary/" + id + ".json"))
                    .getAsJsonObject("minecraft:attachable").getAsJsonObject("description");
            assertEquals("bigcasares:" + id, description.get("identifier").getAsString());
            assertEquals(geometry.getAsJsonObject("description").get("identifier").getAsString(),
                    description.getAsJsonObject("geometry").get("default").getAsString());
            assertEquals(4, description.getAsJsonObject("animations").size());
            var icon = ImageIO.read(bedrock.resolve("textures/item/legendary/" + id + "_icon.png").toFile());
            assertEquals(64, icon.getWidth());
            long visible = 0;
            for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) if ((icon.getRGB(x, y) >>> 24) != 0) visible++;
            assertTrue(visible > 50 && visible < 4096, id + " icon must show the weapon on a transparent background");
            assertTrue(read(bedrock.resolve("textures/item_texture.json")).getAsJsonObject("texture_data")
                    .has("bigcasares.legendary." + id));
        }
    }

    @Test void rotatedCuboidsKeepTheirPivotsAndUseBedrocksHandedness() {
        var cube = JsonParser.parseString("""
            {"from":[1,2,3],"to":[4,6,8],"faces":{"north":{"uv":[0,0,4,4],"texture":"#body"}},
             "rotation":{"origin":[2,3,4],"x":10,"y":20,"z":30}}
            """).getAsJsonObject();
        var converted = LegendaryBedrockModels.cube(cube);
        assertEquals("[4.0,2.0,-5.0]", converted.get("origin").toString());
        assertEquals("[6.0,3.0,-4.0]", converted.get("pivot").toString());
        assertEquals("[-10.0,-20.0,30.0]", converted.get("rotation").toString());
    }
}
