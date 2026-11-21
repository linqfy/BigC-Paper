package dev.linqfy.bigCasares.modules.resourcepack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.joml.Matrix4d;
import org.joml.Vector3d;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegendaryItemModelsTest {
    @TempDir Path directory;

    private static JsonObject json(String s) { return JsonParser.parseString(s).getAsJsonObject(); }

    @Test void linearAndStepChannelsHoldCorrectlyAcrossKeyBoundaries() {
        var track = json("""
            {"keyframes":[
              {"time":0,"channel":"position","interpolation":"linear","data_points":[{"x":0,"y":2,"z":0}]},
              {"time":1,"channel":"position","interpolation":"step","data_points":[{"x":4,"y":2,"z":0}]},
              {"time":2,"channel":"position","interpolation":"linear","data_points":[{"x":8,"y":2,"z":0}]}
            ]}
            """);
        assertEquals(2, LegendaryItemModels.sample(track, "position", .5, 0).x);
        assertEquals(4, LegendaryItemModels.sample(track, "position", 1.5, 0).x);
        assertEquals(8, LegendaryItemModels.sample(track, "position", 2, 0).x);
        assertEquals(1, LegendaryItemModels.sample(track, "scale", 2, 1).x);
    }

    @Test void catmullRomUsesLoopNeighborsAtTheSeam() {
        var track = json("""
            {"keyframes":[
              {"time":0,"channel":"position","interpolation":"catmullrom","data_points":[{"x":0}]},
              {"time":1,"channel":"position","interpolation":"catmullrom","data_points":[{"x":4}]},
              {"time":2,"channel":"position","interpolation":"catmullrom","data_points":[{"x":8}]},
              {"time":3,"channel":"position","interpolation":"catmullrom","data_points":[{"x":0}]}
            ]}
            """);
        assertEquals(1.25, LegendaryItemModels.sample(track, "position", .5, 0, true).x);
        assertEquals(1.75, LegendaryItemModels.sample(track, "position", .5, 0, false).x);
    }

    @Test void facesAreCulledOnlyWhenTheOtherBoxCoversTheWholeOutwardSide() {
        var part = json("{\"from\":[0,0,0],\"to\":[1,1,1]}");
        assertTrue(LegendaryItemModels.covered(part, json("{\"from\":[1,0,0],\"to\":[2,1,1]}"), 0, 1));
        assertFalse(LegendaryItemModels.covered(part, json("{\"from\":[1,0,0],\"to\":[2,0.5,1]}"), 0, 1));
        assertFalse(LegendaryItemModels.covered(part, part, 0, 1));
        assertFalse(LegendaryItemModels.covered(part, json("{\"from\":[2,0,0],\"to\":[3,1,1]}"), 0, 1));
    }

    @Test void absentSourcesAreOptionalAndSourcePathsCannotEscapeTheEditableDirectory() throws Exception {
        var compiler = new LegendaryItemModels();
        compiler.compile(directory, directory.resolve("output"));
        assertFalse(Files.exists(directory.resolve("output")));
        Files.createDirectories(directory.resolve("legendary"));
        Files.writeString(directory.resolve("legendary/items.json"), "{\"test\":{\"source\":\"../outside.bbmodel\"}}");
        assertThrows(java.io.IOException.class, () -> compiler.compile(directory, directory.resolve("output")));
    }

    @Test void nestedRotationsUseAbsoluteBlockbenchPivotsAndNativeMatrixUnits() throws Exception {
        var source = json("""
            {"elements":[{"uuid":"cube","from":[5,0,0],"to":[6,1,1],"origin":[0,0,0],
              "faces":{"north":{"texture":0,"uv":[0,0,16,16]}}}],
             "outliner":[{"uuid":"parent","origin":[8,0,0],"children":[
               {"uuid":"child","origin":[4,0,0],"children":["cube"]}]}],
             "animations":[{"name":"turn","length":0.1,"loop":"once","animators":{
               "parent":{"keyframes":[{"channel":"rotation","time":0,"data_points":[{"z":90}]}]},
               "child":{"keyframes":[{"channel":"rotation","time":0,"data_points":[{"z":90}]}]}
             }}]}
            """);
        var original = json(Files.readString(Path.of("resourcepack/legendary/espada_rubi.bbmodel")));
        source.add("textures", original.getAsJsonArray("textures").deepCopy());
        Path inputs = directory.resolve("legendary");
        Files.createDirectories(inputs);
        Files.writeString(inputs.resolve("test.bbmodel"), source.toString());
        Files.writeString(inputs.resolve("items.json"), "{\"test\":{\"source\":\"test.bbmodel\",\"center\":[0,0,0]}}");
        Path output = directory.resolve("output");
        new LegendaryItemModels().compile(directory, output);
        Path assets = output.resolve("assets/bigcasares");
        var definition = json(Files.readString(assets.resolve("items/test.json")));
        var graph = definition.getAsJsonObject("model").getAsJsonObject("fallback").getAsJsonArray("entries")
                .get(1).getAsJsonObject().getAsJsonObject("model").getAsJsonArray("models").get(0).getAsJsonObject();
        var array = graph.getAsJsonArray("transformation");
        var matrix = new Matrix4d();
        for (int row = 0; row < 4; row++) for (int col = 0; col < 4; col++) {
            matrix.set(col, row, array.get(row * 4 + col).getAsDouble());
        }
        // Child: (5.5,.5) -> (3.5,1.5). Parent: -> (6.5,-4.5).
        var center = matrix.transformPosition(new Vector3d(.5 + 5.5 * .45 / 16, .5 + .5 * .45 / 16, .5 + .5 * .45 / 16));
        assertEquals(.5 + 6.5 * .45 / 16, center.x, 1e-6);
        assertEquals(.5 - 4.5 * .45 / 16, center.y, 1e-6);
        assertEquals(.5 + .5 * .45 / 16, center.z, 1e-6);
    }
}
