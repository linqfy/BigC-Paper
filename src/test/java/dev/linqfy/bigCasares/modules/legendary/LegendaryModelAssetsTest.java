package dev.linqfy.bigCasares.modules.legendary;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.linqfy.bigCasares.modules.resourcepack.LegendaryItemModels;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegendaryModelAssetsTest {
    @TempDir static Path output;
    private static final Path SOURCES = Path.of("resourcepack");
    private static Path assets;

    @BeforeAll static void compileEditableInputs() throws Exception {
        new LegendaryItemModels().compile(SOURCES, output);
        assets = output.resolve("assets/bigcasares");
    }

    private static JsonObject json(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    @Test void embeddedTexturesStayByteExactAndAreAddedToTheItemAtlas() throws Exception {
        JsonObject settings = json(SOURCES.resolve("legendary/items.json"));
        Set<String> singles = new HashSet<>();
        for (var entry : json(output.resolve("assets/minecraft/atlases/items.json")).getAsJsonArray("sources")) {
            singles.add(entry.getAsJsonObject().get("resource").getAsString());
        }
        for (var entry : settings.entrySet()) {
            JsonObject project = json(SOURCES.resolve("legendary/" + entry.getValue().getAsJsonObject().get("source").getAsString()));
            byte[] original = Base64.getDecoder().decode(project.getAsJsonArray("textures").get(0)
                    .getAsJsonObject().get("source").getAsString().split(",", 2)[1]);
            assertArrayEquals(original, Files.readAllBytes(assets.resolve("textures/item/user/" + entry.getKey() + ".png")));
            assertTrue(singles.contains("bigcasares:item/user/" + entry.getKey()));
        }
        assertEquals(5, singles.size());
    }

    @Test void allElevenClipsResolveEveryFrameAndGuiUsesTheRestPose() throws Exception {
        LegendaryAnimationCatalog catalog = LegendaryAnimationCatalog.load(new LegendaryItemModels().animationCatalog(SOURCES));
        JsonObject settings = json(SOURCES.resolve("legendary/items.json"));
        int clips = 0;
        for (var weapon : LegendaryWeapon.available()) {
            JsonObject item = json(assets.resolve("items/" + weapon.id + ".json"));
            assertFalse(item.get("hand_animation_on_swap").getAsBoolean());
            JsonObject select = item.getAsJsonObject("model");
            assertEquals("gui", select.getAsJsonArray("cases").get(0).getAsJsonObject().get("when").getAsString());
            assertEquals("bigcasares:item/" + weapon.id, select.getAsJsonArray("cases").get(0)
                    .getAsJsonObject().getAsJsonObject("model").get("model").getAsString());
            Set<Integer> frames = new HashSet<>();
            for (var value : select.getAsJsonObject("fallback").getAsJsonArray("entries")) {
                JsonObject entry = value.getAsJsonObject();
                assertTrue(frames.add(entry.get("threshold").getAsInt()));
                checkGraph(entry.getAsJsonObject("model"));
            }
            JsonObject original = json(SOURCES.resolve("legendary/" + settings.getAsJsonObject(weapon.id).get("source").getAsString()));
            for (var value : original.getAsJsonArray("animations")) {
                JsonObject clip = value.getAsJsonObject();
                var sequence = catalog.clip(weapon, clip.get("name").getAsString());
                assertNotNull(sequence);
                assertEquals((int) Math.ceil(clip.get("length").getAsDouble() * 20), sequence.duration());
                for (int frame : sequence.sequence()) assertTrue(frames.contains(frame));
                clips++;
            }
        }
        assertEquals(11, clips);
    }

    private static void checkGraph(JsonObject graph) throws Exception {
        if ("minecraft:composite".equals(graph.get("type").getAsString())) {
            assertTrue(graph.getAsJsonArray("models").size() <= 4);
            for (var child : graph.getAsJsonArray("models")) checkGraph(child.getAsJsonObject());
        } else {
            assertEquals("minecraft:model", graph.get("type").getAsString());
            assertTrue(Files.isRegularFile(assets.resolve("models/" + graph.get("model").getAsString().split(":", 2)[1] + ".json")));
            if (graph.has("transformation")) {
                var matrix = graph.getAsJsonArray("transformation");
                assertEquals(16, matrix.size());
                for (var v : matrix) assertTrue(Double.isFinite(v.getAsDouble()));
                assertEquals(1, matrix.get(15).getAsDouble());
            }
        }
    }

    @Test void sharedGeometryKeepsThePackSmallAndEveryCuboidWithinNativeBounds() throws Exception {
        long faces = 0;
        int models = 0;
        try (var files = Files.walk(assets.resolve("models"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                models++;
                for (var value : json(file).getAsJsonArray("elements")) {
                    JsonObject part = value.getAsJsonObject();
                    faces += part.getAsJsonObject("faces").size();
                    for (int axis = 0; axis < 3; axis++) {
                        double low = part.getAsJsonArray("from").get(axis).getAsDouble();
                        double high = part.getAsJsonArray("to").get(axis).getAsDouble();
                        assertTrue(Double.isFinite(low) && Double.isFinite(high) && low < high);
                        assertTrue(low >= -16 && high <= 32, file.toString());
                    }
                }
            }
        }
        assertTrue(models < 100, "Animation frames must share rigid geometry");
        assertTrue(faces < 16000, "Regression in stored geometry");
    }

    @Test void compilingAgainIsDeterministicAndPreservesExistingAtlasSources() throws Exception {
        Path definition = assets.resolve("items/shadow_blade.json");
        byte[] before = Files.readAllBytes(definition);
        Path atlas = output.resolve("assets/minecraft/atlases/items.json");
        var atlasJson = json(atlas);
        atlasJson.getAsJsonArray("sources").add(JsonParser.parseString("{\"type\":\"minecraft:single\",\"resource\":\"bigcasares:item/existing\"}"));
        Files.writeString(atlas, atlasJson.toString());
        new LegendaryItemModels().compile(SOURCES, output);
        assertArrayEquals(before, Files.readAllBytes(definition));
        assertEquals(6, json(atlas).getAsJsonArray("sources").size());
    }
}
