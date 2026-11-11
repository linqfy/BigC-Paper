package dev.linqfy.bigCasares.modules.jeremy;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class JeremySafetyContractTest {

    @Test
    void jeremyCreatesNoResourcePackAssetsOrModels() throws Exception {
        try (var paths = Files.walk(Path.of("resourcepack"))) {
            assertFalse(paths.anyMatch(path -> path.getFileName().toString().toLowerCase().contains("jeremy")));
        }
        try (var paths = Files.walk(Path.of("src/main/resources"))) {
            assertFalse(paths.anyMatch(path -> {
                String name = path.getFileName().toString().toLowerCase();
                return name.contains("jeremy") && (name.endsWith(".png") || name.endsWith(".bbmodel") || name.endsWith(".json"));
            }));
        }
    }

}
