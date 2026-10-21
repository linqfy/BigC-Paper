package dev.linqfy.bigCasares.modules.mutango;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bedrock como en Don Pollos (DonPollosBedrockTest de BigC): quien es de Bedrock, cuando ve los modelos y que los
 * modelos convertidos para GeyserModelEngine esten todos y sean los mismos que los de Java.
 */
class MutangoBedrockTest {

    private static final Logger LOGGER = Logger.getLogger("MutangoBedrockTest");
    /** MutangoModels.MODELS.get(0) (las clases que usan BetterModel no se pueden cargar en los tests). */
    private static final String LEF = "bigcasares_boss_mutango";

    // ---- quien es de Bedrock y que ve ----

    @Test
    void losDeFloodgateSeReconocenPorSuUuid() {
        assertTrue(MutangoBedrock.looksLikeFloodgate(new UUID(0, 2535412345678901L)));
        assertFalse(MutangoBedrock.looksLikeFloodgate(UUID.fromString("b10452d4-c294-478b-ac08-f58a22dad43e")));
        assertFalse(MutangoBedrock.looksLikeFloodgate(new UUID(0, 0)));
    }

    @Test
    void sinGeyserNadieEsDeBedrockSalvoPorLaUuid() {
        // en los tests no estan ni Floodgate ni Geyser: tiene que andar igual (sin explotar) y usar la UUID
        MutangoBedrock bedrock = new MutangoBedrock();
        assertFalse(bedrock.isBedrock(UUID.fromString("b10452d4-c294-478b-ac08-f58a22dad43e")));
        assertTrue(bedrock.isBedrock(new UUID(0, 42L)));
    }

    @Test
    void losModelosEnBedrockSePrendenConGeyserModelEngine() {
        assertTrue(MutangoBedrock.bedrockModels("auto", true));
        assertFalse(MutangoBedrock.bedrockModels("auto", false));
        assertFalse(MutangoBedrock.bedrockModels(null, false));
        assertTrue(MutangoBedrock.bedrockModels(" AUTO ", true));
        assertTrue(MutangoBedrock.bedrockModels("true", false));
        assertTrue(MutangoBedrock.bedrockModels("si", false));
        assertFalse(MutangoBedrock.bedrockModels("false", true));
        assertFalse(MutangoBedrock.bedrockModels("no", true));
        assertTrue(MutangoBedrock.bedrockModels("cualquier cosa", true), "lo que no entiende es auto");
    }

    @Test
    void losDeJavaSiempreVenElModeloYLosDeBedrockSoloConGeyserModelEngine() {
        assertTrue(MutangoBedrock.showModel(false, false));
        assertTrue(MutangoBedrock.showModel(false, true));
        assertTrue(MutangoBedrock.showModel(true, true));
        // sin modelo BetterModel no le esconde la vaca: el de Bedrock ve la vaca en vez de nada
        assertFalse(MutangoBedrock.showModel(true, false));
    }

    @Test
    void laConfigVieneEnAuto() throws IOException {
        YamlConfiguration config = yaml("mutango.yml");
        assertEquals("auto", config.getString(MutangoBedrock.MODELS_SETTING));
    }

    @Test
    void geyserYGeyserModelEngineCarganAntesQueMutango() throws IOException {
        // si cargan despues, al prender Mutango no los encuentra y no instala los modelos
        YamlConfiguration plugin = yaml("plugin.yml");
        assertTrue(plugin.getStringList("depend").contains("BetterModel"));
        List<String> softdepend = plugin.getStringList("softdepend");
        assertTrue(softdepend.contains("floodgate"));
        assertTrue(softdepend.contains(MutangoBedrock.MODEL_ENGINE_PLUGIN));
        assertTrue(softdepend.contains(MutangoBedrock.GEYSER_PLUGINS[0]));
    }

    // ---- los modelos para GeyserModelEngine ----

    @Test
    void cadaModeloDelJarEstaConvertidoParaGeyserModelEngine() throws IOException, URISyntaxException {
        Map<String, byte[]> zip = modelEngineZip();
        List<String> models = jarModels();
        assertEquals(28, models.size(),
            "Mutango (x3), estatuas (x3), Neguechen, sus manos, Sol, Luna, el cubo y 16 con armadura");
        for (String model : models) {
            assertTrue(zip.containsKey(model + "/" + model + ".geo.json"), model);
            assertTrue(zip.containsKey(model + "/" + model + ".animation.json"), model);
            assertTrue(zip.containsKey(model + "/config.json"), model);
            assertTrue(zip.keySet().stream().anyMatch(name -> name.startsWith(model + "/") && name.endsWith(".png")),
                model + " sin textura");
        }
        // y nada de mas: cada carpeta del zip es un modelo del jar
        for (String name : zip.keySet()) {
            assertTrue(models.contains(name.substring(0, name.indexOf('/'))), "sobra " + name);
        }
    }

    @Test
    void losModelosConvertidosEstanAlDiaConLosDeJava() throws IOException, URISyntaxException {
        // si se cambia un modelo y no se vuelve a convertir el zip de Bedrock, Bedrock ve el viejo
        Map<String, byte[]> zip = modelEngineZip();
        for (String model : jarModels()) {
            JsonObject java = bbmodel(model);
            JsonObject geometry = json(zip, model + "/" + model + ".geo.json")
                .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
            assertEquals("geometry." + model, geometry.getAsJsonObject("description").get("identifier").getAsString());

            Set<String> javaBones = new HashSet<>();
            java.getAsJsonArray("groups").forEach(g -> javaBones.add(g.getAsJsonObject().get("name").getAsString()));
            Set<String> bedrockBones = new HashSet<>();
            int cubes = 0;
            for (JsonElement bone : geometry.getAsJsonArray("bones")) {
                bedrockBones.add(bone.getAsJsonObject().get("name").getAsString());
                if (bone.getAsJsonObject().has("cubes")) {
                    cubes += bone.getAsJsonObject().getAsJsonArray("cubes").size();
                }
            }
            assertEquals(javaBones, bedrockBones, model + ": otros huesos");
            assertEquals(java.getAsJsonArray("elements").size(), cubes, model + ": otra cantidad de cubos");

            Set<String> javaAnimations = new HashSet<>();
            java.getAsJsonArray("animations")
                .forEach(a -> javaAnimations.add(a.getAsJsonObject().get("name").getAsString()));
            Set<String> bedrockAnimations = json(zip, model + "/" + model + ".animation.json")
                .getAsJsonObject("animations").keySet();
            assertEquals(javaAnimations, bedrockAnimations, model + ": otras animaciones");
        }
    }

    @Test
    void laTexturaEsLaMismaQueEnJava() throws IOException, URISyntaxException {
        Map<String, byte[]> zip = modelEngineZip();
        for (String model : jarModels()) {
            JsonObject texture = bbmodel(model).getAsJsonArray("textures").get(0).getAsJsonObject();
            byte[] png = java.util.Base64.getDecoder()
                .decode(texture.get("source").getAsString().split(",", 2)[1]);
            byte[] bedrock = zip.entrySet().stream()
                .filter(e -> e.getKey().startsWith(model + "/") && e.getKey().endsWith(".png"))
                .findFirst().orElseThrow().getValue();
            assertTrue(java.util.Arrays.equals(png, bedrock), model + ": la textura de Bedrock es otra");

            // GeyserModelEngine necesita el tamano de cada textura
            JsonObject sizes = json(zip, model + "/config.json").getAsJsonObject("per_texture_uv_size");
            assertEquals(1, sizes.size(), model);
            JsonArray size = sizes.entrySet().iterator().next().getValue().getAsJsonArray();
            JsonObject description = json(zip, model + "/" + model + ".geo.json")
                .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonObject("description");
            assertEquals(description.get("texture_width").getAsInt(), size.get(0).getAsInt(), model);
            assertEquals(description.get("texture_height").getAsInt(), size.get(1).getAsInt(), model);
        }
    }

    @Test
    void elEjeXSeEspejaComoEnBlockbench() throws IOException, URISyntaxException {
        // Bedrock tiene la X al reves: cada cubo empieza en -to.x (como el exportador de Blockbench)
        Map<String, byte[]> zip = modelEngineZip();
        JsonObject java = bbmodel(MutangoShrine.CUBE);
        JsonObject element = java.getAsJsonArray("elements").get(0).getAsJsonObject();
        JsonObject cube = json(zip, MutangoShrine.CUBE + "/" + MutangoShrine.CUBE + ".geo.json")
            .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones").asList().stream()
            .map(JsonElement::getAsJsonObject).filter(bone -> bone.has("cubes")).findFirst().orElseThrow()
            .getAsJsonArray("cubes").get(0).getAsJsonObject();
        assertEquals(-element.getAsJsonArray("to").get(0).getAsDouble(),
            cube.getAsJsonArray("origin").get(0).getAsDouble(), 1e-4);
        assertEquals(element.getAsJsonArray("from").get(1).getAsDouble(),
            cube.getAsJsonArray("origin").get(1).getAsDouble(), 1e-4);
        assertEquals(16.0, cube.getAsJsonArray("size").get(0).getAsDouble(), 1e-4);
    }

    @Test
    void lasAnimacionesQueSeRepitenSiguenRepitiendose() throws IOException, URISyntaxException {
        Map<String, byte[]> zip = modelEngineZip();
        String model = LEF;
        JsonObject animations = json(zip, model + "/" + model + ".animation.json").getAsJsonObject("animations");
        for (JsonElement animation : bbmodel(model).getAsJsonArray("animations")) {
            JsonObject java = animation.getAsJsonObject();
            JsonObject bedrock = animations.getAsJsonObject(java.get("name").getAsString());
            boolean loops = "loop".equals(java.get("loop").getAsString());
            assertEquals(loops, bedrock.has("loop") && bedrock.get("loop").getAsBoolean(),
                java.get("name").getAsString());
            assertEquals(java.get("length").getAsDouble(), bedrock.get("animation_length").getAsDouble(), 1e-4);
        }
    }

    // ---- la instalacion en Geyser ----

    @Test
    void seDescomprimeEnLaCarpetaDeGeyserModelEngine(@TempDir Path input) throws IOException {
        byte[] zip = resource(MutangoBedrock.MODEL_ENGINE_RESOURCE);
        assertTrue(MutangoBedrock.unzip(zip, input, LOGGER));
        String model = LEF;
        assertTrue(Files.isRegularFile(input.resolve(model).resolve(model + ".geo.json")));
        assertTrue(Files.isRegularFile(input.resolve(model).resolve("config.json")));
        // la segunda vez no cambia nada: no pide reiniciar de nuevo
        assertFalse(MutangoBedrock.unzip(zip, input, LOGGER));
    }

    @Test
    void siCambiaUnArchivoLoVuelveAEscribir(@TempDir Path input) throws IOException {
        byte[] zip = resource(MutangoBedrock.MODEL_ENGINE_RESOURCE);
        MutangoBedrock.unzip(zip, input, LOGGER);
        String model = LEF;
        Path config = input.resolve(model).resolve("config.json");
        Files.writeString(config, "{}");
        assertTrue(MutangoBedrock.unzip(zip, input, LOGGER));
        assertFalse(Files.readString(config).equals("{}"));
    }

    @Test
    void siLaExtensionLeAgregoCosasAlConfigNoSeLoPisa(@TempDir Path input) throws IOException {
        byte[] zip = resource(MutangoBedrock.MODEL_ENGINE_RESOURCE);
        MutangoBedrock.unzip(zip, input, LOGGER);
        Path config = input.resolve(LEF).resolve("config.json");
        // la extension de GeyserModelEngine modifica el config al cargarlo: conserva lo nuestro y agrega lo suyo
        com.google.gson.JsonObject extended = com.google.gson.JsonParser.parseString(Files.readString(config))
            .getAsJsonObject();
        extended.addProperty("lo_que_agrega_la_extension", true);
        // y llena lo que nosotros dejamos vacio (binding_bones), como hace de verdad al cargar
        com.google.gson.JsonObject bones = new com.google.gson.JsonObject();
        com.google.gson.JsonArray all = new com.google.gson.JsonArray();
        all.add("*");
        bones.add(LEF, all);
        extended.add("binding_bones", bones);
        Files.writeString(config, extended.toString());
        assertFalse(MutangoBedrock.unzip(zip, input, LOGGER), "no pide reiniciar por algo que ya tiene todo lo nuestro");
        assertTrue(Files.readString(config).contains("lo_que_agrega_la_extension"), "no se lo pisa");
    }

    @Test
    void siFaltaAlgoDeLoNuestroEnElConfigSiSeLoCompleta(@TempDir Path input) throws IOException {
        byte[] zip = resource(MutangoBedrock.MODEL_ENGINE_RESOURCE);
        MutangoBedrock.unzip(zip, input, LOGGER);
        Path config = input.resolve(LEF).resolve("config.json");
        Files.writeString(config, "{\"material\":\"otro\"}");
        assertTrue(MutangoBedrock.unzip(zip, input, LOGGER));
        assertTrue(Files.readString(config).contains("entity_alphatest_change_color_one_sided"));
    }

    @Test
    void unZipNoPuedeEscribirAfueraDeLaCarpeta(@TempDir Path folder) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("../afuera.txt"));
            zip.write("no".getBytes(StandardCharsets.UTF_8));
            zip.putNextEntry(new ZipEntry("modelo/adentro.txt"));
            zip.write("si".getBytes(StandardCharsets.UTF_8));
        }
        Path input = folder.resolve("input");
        assertTrue(MutangoBedrock.unzip(out.toByteArray(), input, LOGGER));
        assertTrue(Files.isRegularFile(input.resolve("modelo").resolve("adentro.txt")));
        assertFalse(Files.exists(folder.resolve("afuera.txt")));
    }

    // ---- ayudas ----

    private static byte[] resource(String name) throws IOException {
        try (InputStream stream = MutangoBedrockTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(stream, "falta " + name + " (mutango/bedrock/geysermodelengine.zip)");
            return stream.readAllBytes();
        }
    }

    private static YamlConfiguration yaml(String name) throws IOException {
        return YamlConfiguration.loadConfiguration(
            new InputStreamReader(new java.io.ByteArrayInputStream(resource(name)), StandardCharsets.UTF_8));
    }

    private static Map<String, byte[]> modelEngineZip() throws IOException {
        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(
            new java.io.ByteArrayInputStream(resource(MutangoBedrock.MODEL_ENGINE_RESOURCE)))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                files.put(entry.getName(), zip.readAllBytes());
            }
        }
        return files;
    }

    private static JsonObject json(Map<String, byte[]> zip, String name) {
        byte[] content = zip.get(name);
        assertNotNull(content, "falta " + name);
        return JsonParser.parseString(new String(content, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static JsonObject bbmodel(String model) throws IOException {
        return JsonParser.parseString(new String(resource("bettermodel/models/" + model + ".bbmodel"), StandardCharsets.UTF_8))
            .getAsJsonObject();
    }

    /** Los modelos de Mutango que van en el jar (bettermodel/models), los mismos que instala MutangoModelInstaller. */
    private static List<String> jarModels() {
        return MutangoModelInstaller.allModels().stream().sorted().toList();
    }
}
