package dev.linqfy.bigCasares.modules.alexscaves;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtraModelsTest {
    /** Models drawn without a mob type (gum worm segments, mushroom cloud) are installed too, so they must ship. */
    @Test
    void everyExtraModelIsPackaged() {
        for (String key : AlexsCavesRuntime.EXTRA_MODELS) {
            assertTrue(Files.isRegularFile(Path.of("src/main/resources/bettermodel/models/" + key + ".bbmodel")), key);
        }
    }
}
