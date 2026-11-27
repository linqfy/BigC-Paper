package dev.linqfy.bigCasares.modules.customcrossbow;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Registering two custom items with one model data number stops the whole plugin from enabling. The crossbow books
 * have no catalog file, so their numbers are checked against the ones the catalog items declare.
 */
class CrossbowBookModelDataTest {
    private static final Pattern LEGACY = Pattern.compile("(?m)^legacy-custom-model-data:\\s*(\\d+)");

    @Test
    void modelDataIsUniqueAcrossCrossbowBooksAndCatalogItems() throws IOException {
        Map<Integer, String> owners = new HashMap<>();
        try (Stream<Path> files = Files.list(Path.of("src/main/resources/content/items"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".yml")).toList()) {
                Matcher matcher = LEGACY.matcher(Files.readString(file));
                if (matcher.find()) {
                    claim(owners, Integer.parseInt(matcher.group(1)), file.getFileName().toString());
                }
            }
        }
        for (CrossbowEnchantment enchantment : CrossbowEnchantment.values()) {
            claim(owners, enchantment.modelData(), enchantment.bookId());
        }
    }

    private static void claim(Map<Integer, String> owners, int modelData, String owner) {
        String previous = owners.put(modelData, owner);
        assertNull(previous, () -> "model data " + modelData + " is used by " + previous + " and " + owner);
    }
}
