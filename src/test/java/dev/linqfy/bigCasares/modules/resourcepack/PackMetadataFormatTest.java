package dev.linqfy.bigCasares.modules.resourcepack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A 26.x client rejects a pack whose {@code pack_format} is above 64 without {@code min_format} and
 * {@code max_format}, and only loads it through a fallback after logging an error; found with a real client.
 */
class PackMetadataFormatTest {

    @Test
    void editablePacksDeclareTheFormatRangeNewClientsRequire() throws IOException {
        for (Path file : List.of(Path.of("resourcepack/pack.mcmeta"), Path.of("resourcepack/java/pack.mcmeta"))) {
            JsonObject pack = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("pack");
            int format = pack.get("pack_format").getAsInt();
            assertTrue(pack.has("min_format") && pack.has("max_format"), file + " has no min_format/max_format");
            assertTrue(pack.get("min_format").getAsInt() <= format && format <= pack.get("max_format").getAsInt(),
                file + " range leaves out its own pack_format");
        }
    }
}
