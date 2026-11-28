package dev.linqfy.bigCasares.modules.caichet;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Los siete cuadros: JSON del datapack, texturas con el tamaño justo, claves y reglas del drop. */
class DreamPaintingsTest {

    private static final ClassLoader LOADER = DreamPaintingsTest.class.getClassLoader();
    private static final int PIXELS_PER_BLOCK = 16;

    private static JsonObject variantJson(String id) throws IOException {
        String resource = CaiChetDatapack.RESOURCE_ROOT + "data/caichet/painting_variant/" + id + ".json";
        try (InputStream stream = LOADER.getResourceAsStream(resource)) {
            assertNotNull(stream, "falta " + resource);
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }

    @Test
    void thereAreSevenPaintingsWithUniqueIds() {
        assertEquals(7, DreamPaintings.ALL.size());
        Set<String> ids = new HashSet<>(DreamPaintings.ids());
        assertEquals(DreamPaintings.ALL.size(), ids.size());
        assertEquals(Set.of("retrato", "laptop", "pintor", "esfera", "gotico", "grito", "cena"), ids);
    }

    @Test
    void everyVariantJsonMatchesItsSizeAndTitle() throws IOException {
        for (DreamPaintings.Variant variant : DreamPaintings.ALL) {
            JsonObject json = variantJson(variant.id());
            assertEquals("caichet:" + variant.id(), json.get("asset_id").getAsString(), variant.id());
            assertEquals(variant.width(), json.get("width").getAsInt(), variant.id() + " ancho");
            assertEquals(variant.height(), json.get("height").getAsInt(), variant.id() + " alto");
            assertEquals(variant.title(), json.getAsJsonObject("title").get("text").getAsString(), variant.id());
            assertTrue(json.getAsJsonObject("author").get("text").getAsString().length() > 0, variant.id() + " autor");
        }
    }

    @Test
    void variantSizesStayInsideWhatMinecraftAccepts() {
        for (DreamPaintings.Variant variant : DreamPaintings.ALL) {
            assertTrue(variant.width() >= 1 && variant.width() <= 16, variant.id());
            assertTrue(variant.height() >= 1 && variant.height() <= 16, variant.id());
        }
    }

    @Test
    void everyTextureExistsAndMeasuresSixteenPixelsPerBlock() throws IOException {
        Path folder = Path.of("resourcepack/java/assets/caichet/textures/painting");
        for (DreamPaintings.Variant variant : DreamPaintings.ALL) {
            Path file = folder.resolve(variant.id() + ".png");
            assertTrue(Files.isRegularFile(file), "falta " + file);
            BufferedImage image = ImageIO.read(file.toFile());
            assertNotNull(image, "no es un PNG: " + file);
            assertEquals(variant.width() * PIXELS_PER_BLOCK, image.getWidth(), variant.id() + " ancho en pixeles");
            assertEquals(variant.height() * PIXELS_PER_BLOCK, image.getHeight(), variant.id() + " alto en pixeles");
        }
    }

    @Test
    void theDatapackShipsEveryVariantAndDoesNotMakeThemRandom() {
        for (DreamPaintings.Variant variant : DreamPaintings.ALL) {
            assertTrue(CaiChetDatapack.FILES.contains("data/caichet/painting_variant/" + variant.id() + ".json"),
                variant.id());
        }
        // fuera del tag #minecraft:placeable no salen al azar cuando alguien cuelga un cuadro comun
        assertTrue(CaiChetDatapack.FILES.stream().noneMatch(file -> file.contains("/tags/")), "el datapack toca tags");
    }

    @Test
    void theFactoryAsksForTheRightKeyOfEachPainting() {
        List<NamespacedKey> asked = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (DreamPaintings.Variant variant : DreamPaintings.ALL) {
            assertNull(DreamPaintings.create(variant.id(), key -> {
                asked.add(key);
                return null;
            }, warnings::add));
        }
        assertEquals(DreamPaintings.ids().stream().map(id -> new NamespacedKey("caichet", id)).toList(), asked);
        assertEquals(7, warnings.size());
        assertTrue(warnings.getFirst().contains("caichet:retrato"));
    }

    @Test
    void anUnknownIdNeverReachesTheRegistry() {
        List<String> warnings = new ArrayList<>();
        assertNull(DreamPaintings.create("kebab", key -> {
            throw new AssertionError("no tendria que buscar " + key);
        }, warnings::add));
        assertTrue(warnings.isEmpty());
    }

    @Test
    void onlyOurKeysAreRecognized() {
        assertEquals("grito", DreamPaintings.idOf(Key.key("caichet", "grito")));
        assertNull(DreamPaintings.idOf(Key.key("minecraft", "grito")));
        assertNull(DreamPaintings.idOf(Key.key("caichet", "kebab")));
        assertNull(DreamPaintings.idOf(Key.key("minecraft", "kebab")));
        assertNull(DreamPaintings.idOf(null));
    }

    @Test
    void dropRuleFollowsTheGameRuleAndCreativeMode() {
        assertTrue(DreamPaintings.shouldDrop(true, false));
        assertFalse(DreamPaintings.shouldDrop(false, false));
        assertFalse(DreamPaintings.shouldDrop(true, true));
        assertFalse(DreamPaintings.shouldDrop(false, true));
    }

    @Test
    void randomIdCoversEveryPainting() {
        Random random = new Random(7);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            seen.add(DreamPaintings.randomId(random));
        }
        assertEquals(new HashSet<>(DreamPaintings.ids()), seen);
    }
}
