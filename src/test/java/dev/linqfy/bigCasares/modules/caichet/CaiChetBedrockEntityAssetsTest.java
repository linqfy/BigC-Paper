package dev.linqfy.bigCasares.modules.caichet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Bedrock pack side of {@link CaiChetBedrockEntities}: every entity points at parts that exist. */
class CaiChetBedrockEntityAssetsTest {

    private static final Path PACK = Path.of("resourcepack/bedrock");
    private static final Path JAVA_ITEM_MODELS = Path.of("resourcepack/java/assets/caichet/models/item");

    @Test
    void everyEntityHasAClientEntityWhosePartsExist() throws IOException {
        Map<String, JsonObject> geometries = geometries();
        Set<String> animations = declared("animations", ".animation.json", "animations");
        Set<String> renderControllers = declared("render_controllers", ".json", "render_controllers");
        String lang = Files.readString(PACK.resolve("texts/en_US.lang"), StandardCharsets.UTF_8);
        Map<String, JsonObject> clientEntities = clientEntities();

        Set<String> problems = new TreeSet<>();
        for (CaiChetBedrockEntities.Entry entry : CaiChetBedrockEntities.all()) {
            String id = entry.bedrockIdentifier();
            JsonObject description = clientEntities.get(id);
            if (description == null) {
                problems.add(id + ": no client entity");
                continue;
            }
            String geometry = description.getAsJsonObject("geometry").get("default").getAsString();
            if (!geometries.containsKey(geometry)) {
                problems.add(id + ": geometry " + geometry);
            }
            description.getAsJsonObject("textures").entrySet().forEach(texture -> {
                if (!Files.isRegularFile(PACK.resolve(texture.getValue().getAsString() + ".png"))) {
                    problems.add(id + ": texture " + texture.getValue());
                }
            });
            JsonObject entityAnimations = description.has("animations") ? description.getAsJsonObject("animations") : new JsonObject();
            entityAnimations.entrySet().forEach(animation -> {
                if (!animations.contains(animation.getValue().getAsString())) {
                    problems.add(id + ": animation " + animation.getValue());
                }
            });
            if (description.has("scripts")) {
                description.getAsJsonObject("scripts").getAsJsonArray("animate").forEach(name -> {
                    if (!entityAnimations.has(name.getAsString())) {
                        problems.add(id + ": animate " + name);
                    }
                });
            }
            description.getAsJsonArray("render_controllers").forEach(controller -> {
                if (!renderControllers.contains(controller.getAsString())) {
                    problems.add(id + ": render controller " + controller);
                }
            });
            if (!lang.contains("entity." + id + ".name=")) {
                problems.add(id + ": no name");
            }
        }
        assertEquals(Set.of(), problems);
        assertEquals(CaiChetBedrockEntities.all().size(), clientEntities.keySet().stream()
            .filter(id -> id.startsWith("caichet:")).count(), "no client entity without a registration");
    }

    @Test
    void geometriesAreValidAndAnimatedBonesExist() throws IOException {
        Map<String, JsonObject> geometries = geometries();
        Map<String, JsonObject> animations = animations();
        for (JsonObject geometry : geometries.values()) {
            Set<String> bones = new HashSet<>();
            geometry.getAsJsonArray("bones").forEach(bone -> bones.add(bone.getAsJsonObject().get("name").getAsString()));
            geometry.getAsJsonArray("bones").forEach(bone -> {
                JsonObject object = bone.getAsJsonObject();
                assertTrue(!object.has("parent") || bones.contains(object.get("parent").getAsString()),
                    "parent of " + object.get("name"));
            });
        }
        for (Map.Entry<String, JsonObject> animation : animations.entrySet()) {
            if (!animation.getKey().startsWith("animation.caichet.")) {
                continue;
            }
            for (String bone : animation.getValue().getAsJsonObject("bones").keySet()) {
                boolean found = geometries.entrySet().stream().anyMatch(geometry ->
                    geometry.getKey().startsWith("geometry.caichet.") && boneNames(geometry.getValue()).contains(bone));
                assertTrue(found, animation.getKey() + " animates a bone no geometry has: " + bone);
            }
        }
    }

    @Test
    void bossEyeHasTheEyeAndBothWingsOfTheJavaModels() throws IOException {
        JsonObject geometry = geometries().get("geometry.caichet.boss_eye");
        assertEquals(javaElements("boss_eye"), cubes(geometry, "body"));
        assertEquals(javaElements("boss_wing_l"), cubes(geometry, "wing_l"));
        assertEquals(javaElements("boss_wing_r"), cubes(geometry, "wing_r"));
        Set<String> names = boneNames(geometry);
        assertTrue(names.containsAll(Set.of("wing_l_anim", "wing_r_anim", "body_pitch")));
        JsonObject flap = animations().get("animation.caichet.boss_eye.idle").getAsJsonObject("bones");
        assertTrue(flap.has("wing_l_anim") && flap.has("wing_r_anim"), "the wings flap");
        assertTrue(animations().get("animation.caichet.boss_eye.idle").get("loop").getAsBoolean());
    }

    @Test
    void flatSpritesAreOneDoubleSidedPlaneAndTheWingedEyeHasBothWingFrames() throws IOException {
        Map<String, JsonObject> geometries = geometries();
        for (String name : Set.of("watcher", "dream_square_4", "dream_mushroom", "dream_window", "dream_door", "dream_void", "dream_rainbow")) {
            JsonObject geometry = geometries.get("geometry.caichet." + name);
            assertEquals(1, cubes(geometry, "board"), name);
            JsonObject faces = geometry.getAsJsonArray("bones").asList().stream()
                .map(JsonElement::getAsJsonObject).filter(bone -> bone.has("cubes")).findFirst().orElseThrow()
                .getAsJsonArray("cubes").get(0).getAsJsonObject().getAsJsonObject("uv");
            assertEquals(Set.of("north", "south"), faces.keySet(), name + " shows both faces");
        }
        JsonObject winged = geometries.get("geometry.caichet.dream_winged");
        assertEquals(1, cubes(winged, "wing_up"));
        assertEquals(1, cubes(winged, "wing_down"));
        JsonObject flap = animations().get("animation.caichet.dream_winged.flap");
        assertTrue(flap.get("loop").getAsBoolean());
        assertTrue(flap.getAsJsonObject("bones").has("wing_up") && flap.getAsJsonObject("bones").has("wing_down"));
    }

    @Test
    void rainbowBaseSizeKeepsEveryScaleTheServerNeedsInsideTheAttributeLimits() throws IOException {
        JsonObject geometry = geometries().get("geometry.caichet.dream_rainbow");
        JsonObject cube = geometry.getAsJsonArray("bones").asList().stream().map(JsonElement::getAsJsonObject)
            .filter(bone -> bone.has("cubes")).findFirst().orElseThrow().getAsJsonArray("cubes").get(0).getAsJsonObject();
        double baked = cube.getAsJsonArray("size").get(0).getAsDouble() / 16.0;
        assertEquals(RainbowPlacement.MODEL_BLOCKS, baked, 1.0e-9);
        // sizes 70..110 seen from anywhere between 1 and 90 blocks
        for (double distance : new double[] {1, 20, 48, 90}) {
            for (double size : new double[] {70, 110}) {
                double scale = size * RainbowPlacement.placementDistance(distance) / distance / baked;
                assertTrue(scale >= RainbowPlacement.MIN_SCALE && scale <= RainbowPlacement.MAX_SCALE,
                    size + " at " + distance + " needs " + scale);
            }
        }
        JsonObject description = geometry.getAsJsonObject("description");
        assertTrue(description.get("visible_bounds_width").getAsDouble() >= 256);
        assertTrue(description.get("visible_bounds_height").getAsDouble() >= 256);
        assertTrue(Files.isRegularFile(PACK.resolve("textures/caichet/entity/dream_rainbow.png")));
        assertTrue(Files.isRegularFile(Path.of("resourcepack/java/assets/caichet/textures/item/dream_rainbow.png")));
        assertEquals(1, json(JAVA_ITEM_MODELS.resolve("dream_rainbow.json")).getAsJsonArray("elements").size());
    }

    @Test
    void locomotiveKeepsEveryPieceOfTheDemonTrainExceptItsAnimatedSmoke() throws IOException {
        JsonObject model = json(Path.of("resourcepack/java/assets/demon_train/models/demon_train.json"));
        int pieces = 0;
        for (JsonElement element : model.getAsJsonArray("elements")) {
            boolean smoke = false;
            for (Map.Entry<String, JsonElement> face : element.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                String texture = face.getValue().getAsJsonObject().get("texture").getAsString();
                smoke |= texture.length() == 2 && Character.isDigit(texture.charAt(1));
            }
            if (!smoke) {
                pieces++;
            }
        }
        JsonObject geometry = geometries().get("geometry.caichet.demon_train");
        assertEquals(pieces, cubes(geometry, "locomotive"));
        BufferedImage texture = ImageIO.read(PACK.resolve("textures/caichet/entity/demon_train.png").toFile());
        assertEquals(geometry.getAsJsonObject("description").get("texture_width").getAsInt(), texture.getWidth());
        assertEquals(geometry.getAsJsonObject("description").get("texture_height").getAsInt(), texture.getHeight());
    }

    @Test
    void everyFaceReadsInsideItsTexture() throws IOException {
        Map<String, JsonObject> geometries = geometries();
        Set<String> problems = new TreeSet<>();
        for (JsonObject geometry : geometries.values()) {
            JsonObject description = geometry.getAsJsonObject("description");
            if (!description.get("identifier").getAsString().startsWith("geometry.caichet.")) {
                continue;
            }
            int width = description.get("texture_width").getAsInt();
            int height = description.get("texture_height").getAsInt();
            for (JsonElement bone : geometry.getAsJsonArray("bones")) {
                JsonArray cubes = bone.getAsJsonObject().has("cubes") ? bone.getAsJsonObject().getAsJsonArray("cubes") : new JsonArray();
                for (JsonElement cube : cubes) {
                    for (Map.Entry<String, JsonElement> face : cube.getAsJsonObject().getAsJsonObject("uv").entrySet()) {
                        JsonObject uv = face.getValue().getAsJsonObject();
                        double u0 = uv.getAsJsonArray("uv").get(0).getAsDouble();
                        double v0 = uv.getAsJsonArray("uv").get(1).getAsDouble();
                        double u1 = u0 + uv.getAsJsonArray("uv_size").get(0).getAsDouble();
                        double v1 = v0 + uv.getAsJsonArray("uv_size").get(1).getAsDouble();
                        if (Math.min(u0, u1) < -0.001 || Math.max(u0, u1) > width + 0.001
                            || Math.min(v0, v1) < -0.001 || Math.max(v0, v1) > height + 0.001) {
                            problems.add(description.get("identifier").getAsString() + " " + face.getKey());
                        }
                    }
                }
            }
        }
        assertEquals(Set.of(), problems);
    }

    private static int javaElements(String model) throws IOException {
        return json(JAVA_ITEM_MODELS.resolve(model + ".json")).getAsJsonArray("elements").size();
    }

    private static int cubes(JsonObject geometry, String bone) {
        for (JsonElement element : geometry.getAsJsonArray("bones")) {
            JsonObject object = element.getAsJsonObject();
            if (object.get("name").getAsString().equals(bone)) {
                return object.getAsJsonArray("cubes").size();
            }
        }
        throw new AssertionError("no bone " + bone);
    }

    private static Set<String> boneNames(JsonObject geometry) {
        Set<String> names = new HashSet<>();
        geometry.getAsJsonArray("bones").forEach(bone -> names.add(bone.getAsJsonObject().get("name").getAsString()));
        return names;
    }

    private static Map<String, JsonObject> clientEntities() throws IOException {
        Map<String, JsonObject> out = new HashMap<>();
        for (Path file : files("entity", ".entity.json")) {
            JsonObject description = json(file).getAsJsonObject("minecraft:client_entity").getAsJsonObject("description");
            out.put(description.get("identifier").getAsString(), description);
        }
        return out;
    }

    private static Map<String, JsonObject> geometries() throws IOException {
        Map<String, JsonObject> out = new HashMap<>();
        for (Path file : files("models/entity", ".geo.json")) {
            for (JsonElement geometry : json(file).getAsJsonArray("minecraft:geometry")) {
                JsonObject object = geometry.getAsJsonObject();
                out.put(object.getAsJsonObject("description").get("identifier").getAsString(), object);
            }
        }
        return out;
    }

    private static Map<String, JsonObject> animations() throws IOException {
        Map<String, JsonObject> out = new HashMap<>();
        for (Path file : files("animations", ".animation.json")) {
            json(file).getAsJsonObject("animations").entrySet()
                .forEach(animation -> out.put(animation.getKey(), animation.getValue().getAsJsonObject()));
        }
        return out;
    }

    private static Set<String> declared(String directory, String suffix, String member) throws IOException {
        Set<String> names = new HashSet<>();
        for (Path file : files(directory, suffix)) {
            names.addAll(json(file).getAsJsonObject(member).keySet());
        }
        return names;
    }

    private static List<Path> files(String directory, String suffix) throws IOException {
        try (Stream<Path> files = Files.list(PACK.resolve(directory))) {
            return files.filter(file -> file.getFileName().toString().endsWith(suffix)).sorted().toList();
        }
    }

    private static JsonObject json(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
