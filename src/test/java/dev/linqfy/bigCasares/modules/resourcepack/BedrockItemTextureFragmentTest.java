package dev.linqfy.bigCasares.modules.resourcepack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockItemTextureFragmentTest {

    @TempDir
    Path tempDir;

    @Test
    void moduleFragmentsAreMergedIntoItemTexturesAndNotShipped() throws Exception {
        ResourcePackFixture fixture = ResourcePackFixture.create(tempDir, true);
        Path fragment = fixture.bedrockRoot().resolve("textures/item_texture.alexscaves.json");
        Files.createDirectories(fragment.getParent());
        Files.writeString(fragment, """
            {"texture_data": {"alexscaves_galena": {"textures": "textures/alexscaves/block/galena"}}}
            """, StandardCharsets.UTF_8);

        new ResourcePackBuilder(fixture.sourceRoot(), fixture.outputRoot()).build();

        try (ZipFile bedrock = new ZipFile(fixture.outputRoot().resolve("bigcasares-bedrock.mcpack").toFile())) {
            assertNull(bedrock.getEntry("textures/item_texture.alexscaves.json"));
            JsonObject textures = JsonParser.parseString(new String(
                    bedrock.getInputStream(bedrock.getEntry("textures/item_texture.json")).readAllBytes(),
                    StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("texture_data");
            assertEquals("textures/alexscaves/block/galena",
                textures.getAsJsonObject("alexscaves_galena").get("textures").getAsString());
        }
        assertTrue(Files.isRegularFile(fragment), "the editable source fragment is left untouched");
    }
}
