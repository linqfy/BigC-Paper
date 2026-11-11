package dev.linqfy.bigCasares.modules.tideframe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The background glyphs line up with the chest window: each texture is the window's size (176 x rows*18+114), its
 * top sits on the window's top edge (ascent 13 against the title row at y 6), and the spaces bring the title back.
 */
class MenuBackgroundAssetsTest {
    private static final Path ASSETS = Path.of("resourcepack/java/assets/bigcasares");

    @Test
    void everyMenuScreenHasAGlyphOfItsWindowSize() throws IOException {
        JsonObject font = JsonParser.parseString(Files.readString(ASSETS.resolve("font/tideframe_ui.json"), StandardCharsets.UTF_8))
            .getAsJsonObject();
        Map<Character, JsonObject> bitmaps = new HashMap<>();
        Map<Character, Integer> spaces = new HashMap<>();
        for (JsonElement element : font.getAsJsonArray("providers")) {
            JsonObject provider = element.getAsJsonObject();
            switch (provider.get("type").getAsString()) {
                case "bitmap" -> bitmaps.put(provider.getAsJsonArray("chars").get(0).getAsString().charAt(0), provider);
                case "space" -> provider.getAsJsonObject("advances").entrySet()
                    .forEach(entry -> spaces.put(entry.getKey().charAt(0), entry.getValue().getAsInt()));
                default -> throw new AssertionError("unexpected provider " + provider);
            }
        }
        assertEquals(-8, spaces.get(MenuTitle.BACK_TO_EDGE), "from the title (x 8) to the window's left edge");
        assertEquals(-(176 + 1 - 8), spaces.get(MenuTitle.BACK_TO_TITLE), "from the glyph's advance back to the title");

        for (MenuScreen screen : MenuScreen.values()) {
            JsonObject bitmap = bitmaps.get(screen.glyph());
            assertNotNull(bitmap, screen + " has a glyph");
            int height = screen.rows() * 18 + 114;
            assertEquals(height, bitmap.get("height").getAsInt(), screen + " height");
            assertEquals(13, bitmap.get("ascent").getAsInt(), screen + " ascent");
            String file = bitmap.get("file").getAsString();
            assertTrue(file.startsWith("bigcasares:"), file);
            Path texture = ASSETS.resolve("textures").resolve(file.substring("bigcasares:".length()));
            int[] size = pngSize(texture);
            assertEquals(176, size[0], texture + " width");
            assertEquals(height, size[1], texture + " height");
        }
    }

    private static int[] pngSize(Path png) throws IOException {
        try (InputStream in = Files.newInputStream(png); DataInputStream data = new DataInputStream(in)) {
            byte[] signature = new byte[8];
            data.readFully(signature);
            assertEquals((byte) 0x89, signature[0], png + " is a PNG");
            data.readInt();
            byte[] type = new byte[4];
            data.readFully(type);
            assertEquals("IHDR", new String(type, StandardCharsets.US_ASCII));
            return new int[] {data.readInt(), data.readInt()};
        }
    }
}
