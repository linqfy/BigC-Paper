package dev.linqfy.bigCasares.modules.legendary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class LegendaryLegacyData {
    private static final Path JOURNAL = Path.of("data/legendary/state.properties");

    static Path migrate(Path dataFolder, Path legacyFolder) throws IOException {
        Path destination = dataFolder.resolve(JOURNAL);
        Path original = legacyFolder.resolve(JOURNAL);
        if (!Files.isRegularFile(original)) return destination;
        if (Files.exists(destination)) {
            // Once imported, this is an independent journal. Never overwrite it
            // with the old copy on a later reload or merge ownership ledgers.
            return destination;
        }
        // Validate before copying; preserve every key and the original file.
        new LegendaryStateStore(original);
        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), "legendary-import-", ".tmp");
        try {
            Files.copy(original, temporary, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temporary, destination);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return destination;
    }
}
