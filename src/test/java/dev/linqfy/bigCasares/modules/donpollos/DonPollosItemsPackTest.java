package dev.linqfy.bigCasares.modules.donpollos;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DonPollosItemsPackTest {

    private static final Path ASSETS = Path.of("resourcepack/java/assets/donpollos");

    @Test
    void javaPackShipsModelAndTextureForEveryProductTheMenuBucketAndBossItems() {
        Set<String> models = new HashSet<>();
        for (DonPollosProduct product : DonPollosProduct.values()) {
            models.add(product.modelKey());
        }
        models.add("balde_pollo_frito");
        models.add(DonPollosBossMeteors.ITEM_MODEL);
        models.add(DonPollosKfcBucket.ITEM_MODEL);
        for (String model : models) {
            assertTrue(Files.isRegularFile(ASSETS.resolve("items/" + model + ".json")), model);
            assertTrue(Files.isRegularFile(ASSETS.resolve("models/item/" + model + ".json")), model);
            assertTrue(Files.isRegularFile(ASSETS.resolve("textures/item/" + model + ".png")), model);
        }
        assertTrue(Files.isRegularFile(ASSETS.resolve("models/item/balde_kfc_icono.json")));
        for (String face : new String[]{"bloque_don_pollo_bueno", "bloque_don_pollo_malo"}) {
            assertTrue(Files.isRegularFile(ASSETS.resolve("items/" + face + ".json")), face);
            assertTrue(Files.isRegularFile(ASSETS.resolve("models/item/" + face + ".json")), face);
            assertTrue(Files.isRegularFile(ASSETS.resolve("textures/block/" + face + ".png")), face);
        }
    }

    @Test
    void everySoundInTheCatalogShipsInTheJavaPack() throws IOException {
        String sounds = Files.readString(ASSETS.resolve("sounds.json"));
        try (var clips = Files.list(ASSETS.resolve("sounds"))) {
            clips.filter(path -> path.toString().endsWith(".ogg")).forEach(path -> {
                String name = path.getFileName().toString().replace(".ogg", "");
                assertTrue(sounds.contains("\"donpollos:" + name + "\""), name);
            });
        }
        assertTrue(Files.isRegularFile(ASSETS.resolve("sounds/don_pollo_1.ogg")));
    }
}
