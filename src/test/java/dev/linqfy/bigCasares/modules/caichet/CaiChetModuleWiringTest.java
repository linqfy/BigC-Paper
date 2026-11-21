package dev.linqfy.bigCasares.modules.caichet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Que el modulo este declarado donde BigC lo busca: config, comando, permiso y archivos del pack. */
class CaiChetModuleWiringTest {

    private static final String[] ITEM_IDS = {"reloj_suenos", "despertar", "arma", "botella", "jarra", "llave"};
    private static final String MODEL_PREFIX = "caichet:block/";

    private static YamlConfiguration yaml(String resource) throws IOException {
        try (InputStream stream = CaiChetModuleWiringTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, "falta " + resource);
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }

    @Test
    void moduleIdIsStable() {
        assertEquals("caichet", CaiChetModule.MODULE_ID);
        assertEquals("caichet", new CaiChetModule(null).getId());
    }

    @Test
    void enablingWithoutAPluginIsANoOp() {
        CaiChetModule module = new CaiChetModule(null);
        module.onEnable();
        module.onDisable();
    }

    @Test
    void theModuleSwitchIsDeclaredAndOffByDefault() throws IOException {
        YamlConfiguration config = yaml("config.yml");
        String path = "modules." + CaiChetModule.MODULE_ID + ".enabled";
        assertTrue(config.contains(path), "falta " + path);
        assertFalse(config.getBoolean(path));
    }

    @Test
    void theCommandAndItsAdminPermissionAreDeclared() throws IOException {
        YamlConfiguration plugin = yaml("plugin.yml");
        assertEquals("bigcasares.caichet.admin", plugin.getString("commands.caichet.permission"));
        assertEquals("op", plugin.getString("permissions.bigcasares.caichet.admin.default"));
        assertTrue(plugin.getBoolean("permissions.bigcasares.*.children.bigcasares.caichet.admin"));
    }

    @Test
    void everyItemShipsItsDefinitionModelAndTexture() {
        Path assets = Path.of("resourcepack/java/assets/caichet");
        for (String id : ITEM_IDS) {
            assertTrue(Files.isRegularFile(assets.resolve("items/" + id + ".json")), id + " item definition");
            assertTrue(Files.isRegularFile(assets.resolve("models/item/" + id + ".json")), id + " model");
            assertTrue(Files.isRegularFile(assets.resolve("textures/item/" + id + ".png")), id + " texture");
        }
    }

    @Test
    void everyWaxedBlockstateReferencesAnExistingModel() throws IOException {
        Path blockstates = Path.of("resourcepack/java/assets/minecraft/blockstates");
        Path models = Path.of("resourcepack/java/assets/caichet/models/block");
        List<Path> states;
        try (Stream<Path> list = Files.list(blockstates)) {
            states = list.filter(path -> path.getFileName().toString().startsWith("waxed_")
                && path.getFileName().toString().endsWith(".json")).toList();
        }
        assertFalse(states.isEmpty(), "no hay blockstates waxed_*");
        for (Path state : states) {
            List<String> referenced = new ArrayList<>();
            try (Reader reader = Files.newBufferedReader(state, StandardCharsets.UTF_8)) {
                collectModels(JsonParser.parseReader(reader), referenced);
            }
            assertFalse(referenced.isEmpty(), state + " no referencia modelos de caichet");
            for (String model : referenced) {
                String name = model.substring(MODEL_PREFIX.length());
                assertTrue(Files.isRegularFile(models.resolve(name + ".json")), state.getFileName() + " -> " + model);
            }
        }
    }

    /** Junta los "model": "caichet:block/x" de un blockstate (variants o multipart, uno o varios). */
    private static void collectModels(JsonElement element, List<String> out) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (var entry : object.entrySet()) {
                JsonElement value = entry.getValue();
                if (entry.getKey().equals("model") && value.isJsonPrimitive()
                    && value.getAsString().startsWith(MODEL_PREFIX)) {
                    out.add(value.getAsString());
                } else {
                    collectModels(value, out);
                }
            }
        } else if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            array.forEach(item -> collectModels(item, out));
        }
    }

    @Test
    void everyOwnedResourceHasItsOwnId() {
        // el scope rechaza un id repetido y eso tira abajo el encendido del modulo
        Set<String> ids = new HashSet<>();
        for (CaiChetModule.Resource resource : CaiChetModule.Resource.values()) {
            assertTrue(resource.id.startsWith("caichet-"), resource + " lleva el prefijo del modulo");
            assertTrue(ids.add(resource.id), "id repetido: " + resource.id);
        }
    }

    @Test
    void aRealScopeAcceptsEveryOwnedResource() {
        RuntimeRegistrationScope scope = new RuntimeRegistrationScope();
        for (CaiChetModule.Resource resource : CaiChetModule.Resource.values()) {
            assertDoesNotThrow(() -> scope.register(resource.id, () -> { }), resource.id);
        }
        scope.close();
    }
}
