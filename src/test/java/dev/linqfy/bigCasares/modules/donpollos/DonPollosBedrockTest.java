package dev.linqfy.bigCasares.modules.donpollos;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DonPollosBedrockTest {

    @Test
    void floodgatePlayersAreRecognisedByTheirUuid() {
        assertTrue(DonPollosBedrock.looksLikeFloodgate(new UUID(0, 2535412345678901L)));
        assertFalse(DonPollosBedrock.looksLikeFloodgate(UUID.fromString("b10452d4-c294-478b-ac08-f58a22dad43e")));
        assertFalse(DonPollosBedrock.looksLikeFloodgate(new UUID(0, 0)));
    }

    @Test
    void geyserMappingsCoverEveryCustomItemPlayersCanHold() throws IOException {
        JsonObject root;
        try (InputStream stream = resource(DonPollosBedrock.MAPPINGS_RESOURCE)) {
            root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        assertEquals(2, root.get("format_version").getAsInt());
        Set<String> models = new HashSet<>();
        root.getAsJsonObject("items").entrySet().forEach(entry -> entry.getValue().getAsJsonArray()
            .forEach(definition -> models.add(definition.getAsJsonObject().get("model").getAsString())));
        for (DonPollosProduct product : DonPollosProduct.values()) {
            assertTrue(models.contains("donpollos:" + product.modelKey()), product.name());
        }
        assertTrue(models.contains("donpollos:" + DonPollosKfcBucket.ITEM_MODEL));
        assertTrue(models.contains("donpollos:balde_pollo_frito"));
        assertTrue(models.contains("donpollos:bloque_don_pollo_bueno"));
    }

    @Test
    void bedrockPackHasManifestIconsAndDonPolloSounds() throws IOException {
        Set<String> entries = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(resource(DonPollosBedrock.PACK_RESOURCE))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                entries.add(entry.getName());
            }
        }
        assertTrue(entries.contains("manifest.json"));
        assertTrue(entries.contains("textures/item_texture.json"));
        assertTrue(entries.contains("sounds/sound_definitions.json"));
        assertTrue(entries.contains("textures/items/donpollos_salsa.png"));
        assertTrue(entries.contains("sounds/donpollos/don_pollo_1.ogg"));
    }

    @Test
    void bedrockModelsAreOnWhenGeyserModelEngineIsInstalled() {
        assertTrue(DonPollosBedrock.bedrockModels("auto", true));
        assertFalse(DonPollosBedrock.bedrockModels("auto", false));
        assertFalse(DonPollosBedrock.bedrockModels(null, false));
        assertTrue(DonPollosBedrock.bedrockModels("true", false));
        assertFalse(DonPollosBedrock.bedrockModels("false", true));
    }

    @Test
    void everyModelIsConvertedForGeyserModelEngine() throws IOException {
        Set<String> entries = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(resource(DonPollosBedrock.MODEL_ENGINE_RESOURCE))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                entries.add(entry.getName());
            }
        }
        for (String model : DonPollosModelInstaller.allModels()) {
            assertTrue(entries.contains(model + "/" + model + ".geo.json"), model);
            assertTrue(entries.contains(model + "/" + model + ".animation.json"), model);
            assertTrue(entries.contains(model + "/config.json"), model);
        }
    }

    @Test
    void eachDonPolloLooksDifferentForBedrockPlayers() {
        Set<String> looks = new HashSet<>();
        for (DonPolloVariant variant : DonPolloVariant.values()) {
            looks.add(DonPollosBedrock.villagerLook(variant));
        }
        assertEquals(DonPolloVariant.values().length, looks.size());
    }

    private InputStream resource(String name) {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(name);
        assertNotNull(stream, name);
        return stream;
    }
}
