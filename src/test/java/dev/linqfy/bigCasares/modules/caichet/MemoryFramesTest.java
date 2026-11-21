package dev.linqfy.bigCasares.modules.caichet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Las fotos de la Jarra de Memorias: el texto que las dibuja y lo que el pack tiene que traer para cada letra. */
class MemoryFramesTest {

    private static final Path ASSETS = Path.of("resourcepack/java/assets/caichet");
    /** El juego reemplaza por un recuadro vacio cualquier letra de fuente mas grande que esto. */
    private static final int MAX_GLYPH_PIXELS = 256;

    @Test
    void eachFrameDrawsItsTilesRowByRowWithoutGaps() {
        String text = MemoryFrames.glyphs(0);
        int tiles = MemoryFrames.COLUMNS * MemoryFrames.ROWS;
        // cada mosaico con su "sin hueco", mas una vuelta al inicio por cada fila despues de la primera
        assertEquals(tiles * 2 + MemoryFrames.ROWS - 1, text.length());
        assertEquals(MemoryFrames.FIRST_GLYPH, text.charAt(0));
        assertEquals(MemoryFrames.NO_GAP, text.charAt(1));
        assertEquals(MemoryFrames.ROW_START, text.charAt(MemoryFrames.COLUMNS * 2));
        assertEquals(MemoryFrames.glyph(0, 1, 0), text.charAt(MemoryFrames.COLUMNS * 2 + 1));
    }

    @Test
    void framesNeverShareAGlyph() {
        assertEquals(MemoryFrames.glyph(0, MemoryFrames.ROWS - 1, MemoryFrames.COLUMNS - 1) + 1,
            MemoryFrames.glyph(1, 0, 0));
        assertTrue(MemoryFrames.glyph(MemoryFrames.COUNT - 1, MemoryFrames.ROWS - 1, MemoryFrames.COLUMNS - 1)
            < MemoryFrames.ROW_START, "las letras de las fotos no pisan los espacios de la fuente");
    }

    @Test
    void theFontShipsEveryTileWithinTheGlyphSizeLimit() throws IOException {
        JsonObject font;
        try (Reader reader = Files.newBufferedReader(ASSETS.resolve("font/memorias.json"), StandardCharsets.UTF_8)) {
            font = JsonParser.parseReader(reader).getAsJsonObject();
        }
        Map<Character, JsonObject> bitmaps = new HashMap<>();
        JsonObject spaces = null;
        for (JsonElement element : font.getAsJsonArray("providers")) {
            JsonObject provider = element.getAsJsonObject();
            if (provider.get("type").getAsString().equals("space")) {
                spaces = provider.getAsJsonObject("advances");
            } else {
                JsonArray chars = provider.getAsJsonArray("chars");
                bitmaps.put(chars.get(0).getAsString().charAt(0), provider);
            }
        }
        assertNotNull(spaces, "la fuente trae los espacios de ancho negativo");
        assertEquals(-1, spaces.get(String.valueOf(MemoryFrames.NO_GAP)).getAsInt());
        int tileWidth = 0;
        for (int frame = 0; frame < MemoryFrames.COUNT; frame++) {
            for (int row = 0; row < MemoryFrames.ROWS; row++) {
                for (int column = 0; column < MemoryFrames.COLUMNS; column++) {
                    JsonObject provider = bitmaps.get(MemoryFrames.glyph(frame, row, column));
                    assertNotNull(provider, "falta el mosaico " + frame + "/" + row + "/" + column);
                    String file = provider.get("file").getAsString();
                    assertTrue(file.startsWith("caichet:"), file);
                    Path png = ASSETS.resolve("textures").resolve(file.substring("caichet:".length()));
                    BufferedImage image = ImageIO.read(png.toFile());
                    assertNotNull(image, png + " se puede leer");
                    assertTrue(image.getWidth() <= MAX_GLYPH_PIXELS && image.getHeight() <= MAX_GLYPH_PIXELS,
                        png + " entra en el limite de " + MAX_GLYPH_PIXELS + " pixeles");
                    int height = provider.get("height").getAsInt();
                    assertTrue(provider.get("ascent").getAsInt() <= height, "ascent <= height en " + file);
                    // el ancho en pantalla tiene que ser entero para que los mosaicos calcen
                    assertEquals(0, image.getWidth() * height % image.getHeight(), file + " calza sin fraccion");
                    tileWidth = image.getWidth() * height / image.getHeight();
                }
            }
        }
        // la vuelta al inicio de la fila mide justo una fila de mosaicos
        assertEquals(-tileWidth * MemoryFrames.COLUMNS, spaces.get(String.valueOf(MemoryFrames.ROW_START)).getAsInt());
    }
}
