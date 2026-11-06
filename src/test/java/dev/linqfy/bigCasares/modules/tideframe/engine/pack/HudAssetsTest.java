package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.ImageAsset;
import dev.linqfy.bigCasares.modules.tideframe.engine.testing.PngFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class HudAssetsTest {
    @TempDir Path temporary;

    private static Screen screen(String id) {
        return new Screen(id, "MENU", List.of(new Screen.Element("button", "GO", 0, 0, .5, .25, "button", "close")));
    }

    @Test void fixedRegistryIsDeterministicDeduplicatedAndDefensive() {
        var a = screen("a"); var b = screen("b");
        var one = HudAssets.create(List.of(b, a)); var two = HudAssets.create(List.of(a, b));
        assertEquals(one.entries().keySet(), two.entries().keySet());
        for (String path : one.entries().keySet()) assertArrayEquals(one.entries().get(path), two.entries().get(path));
        assertEquals(one.frame(a, null, 8), one.frame(b, null, 8), "Equal pixels share the glyph");
        var returned = one.entries(); var path = returned.keySet().iterator().next();
        byte[] expected = one.entries().get(path); returned.get(path)[0] ^= 1;
        assertArrayEquals(expected, one.entries().get(path));
        assertThrows(UnsupportedOperationException.class, () -> returned.clear());
        assertNotEquals(one.frame(a, null, 8), one.frame(a, "button", 8));
        assertEquals(one.frame(a, null, 8), one.frame(a, null, 0), "The entrance is the title's native fade: every step shows the final frame");
        for (int step = 1; step <= 8; step++) assertFalse(one.frame(a, null, step).isEmpty());
    }

    @Test void changedLayoutCannotPublishWithoutRestartAndCountsAreBounded() {
        var known = screen("menu"); var registry = HudAssets.create(List.of(known));
        registry.requireKnown(known);
        var error = assertThrows(IllegalArgumentException.class, () -> registry.requireKnown(known.move("button", .1, 0)));
        assertEquals("HUD layout changes require a server restart.", error.getMessage());
        assertThrows(IllegalArgumentException.class, () -> registry.frame(known, "unknown", 8));
        assertThrows(IllegalArgumentException.class, () -> registry.frame(known, null, 9));
        assertThrows(IllegalArgumentException.class, () -> registry.cursor(Double.NaN, 0));
        var screens = new ArrayList<Screen>();
        for (int i = 0; i < 97; i++) screens.add(screen("screen_" + i));
        assertThrows(IllegalArgumentException.class, () -> HudAssets.create(screens));
        assertEquals(java.util.Map.of(), HudAssets.empty().entries());
    }

    @Test void fontIsTitleScaledHudWithZeroAdvanceQuarterPixelSpacingAndClippedCursor() throws Exception {
        var registry = HudAssets.create(List.of(screen("menu")));
        String font = new String(registry.entries().get("assets/tideframe/font/hud.json"), StandardCharsets.UTF_8);
        assertTrue(font.contains("\"ascent\":15,\"height\":16"), "Top row: canvas top 72 GUI pixels above centre");
        assertTrue(java.util.regex.Pattern.compile("\"ascent\":-17,\"height\":(4|8|12|16)").matcher(font).find(), "Third 256-pixel row (cropped to its content)");
        assertTrue(font.contains("\"type\":\"space\""));
        assertTrue(font.contains("-0.25")); assertTrue(font.contains("0.25"));
        assertFalse(font.contains("minecraft:"));
        assertEquals(registry.cursor(-1, -1), registry.cursor(-2, -2));
        assertEquals(registry.cursor(1, 1), registry.cursor(2, 2));
        assertNotEquals(registry.cursor(0, 0), registry.cursor(.25, .25));
        int cursorCount = 0;
        for (var entry : registry.entries().entrySet()) if (entry.getKey().contains("/cursor_")) {
            var image = ImageIO.read(new ByteArrayInputStream(entry.getValue()));
            assertTrue(image.getWidth() <= 36 && image.getHeight() % 64 == 0, "cursor tiles are cropped"); cursorCount++;
        }
        assertTrue(cursorCount > 0 && cursorCount <= 576, "Equal pixels at different vertical offsets share PNG bytes");
        var rowSequences = new java.util.HashSet<String>();
        for (int row = 0; row < 144; row++) rowSequences.add(registry.cursor(0, 1 - row / 72.0));
        assertEquals(144, rowSequences.size(), "Each logical vertical pixel remains independently addressable");
        // Independently sum provider advances using actual texture dimensions and declared font height.
        java.util.Map<Character, Double> advances = advances(registry.entries(), font);
        for (int step = 0; step <= 8; step++)
            assertEquals(0, sum(registry.frame(screen("menu"), null, step), advances), .00001);
        for (double x : new double[]{-1, -.33, 0, .51, 1}) for (double y : new double[]{-1, 0, 1})
            assertEquals(0, sum(registry.cursor(x, y), advances), .00001);
    }

    @Test void denserCanvasAndCursorPreserveLogicalFootprintAndSpacing() throws Exception {
        var menu = new Screen("menu", "MENU", List.of(new Screen.Element("canvas", "", 0, 0, 2, 2,
                "label", "", "rectangle", false, null, true, null)));
        var registry = HudAssets.create(List.of(menu), HudAssets.DEFAULT_SIZES, 1.0); // original cursor geometry
        var entries = registry.entries();
        String font = new String(entries.get("assets/tideframe/font/hud.json"), StandardCharsets.UTF_8);
        var advances = advances(entries, font);
        String frame = registry.frame(menu, null, 8);
        assertEquals(-32.0, advances.get(frame.charAt(0)));
        assertEquals(9.0, advances.get(frame.charAt(1)), "128 texture columns map to 8 font units plus 1 gap");
        assertEquals(23.0, advances.get(frame.charAt(2)), "32 back minus the 9-unit glyph advance");
        assertEquals(72, frame.length(), "24 independently zero-advance 128-wide tiles cover the canvas");
        var canvas = composite(entries, font, frame, advances);
        assertEquals(1024, canvas.getWidth()); assertEquals(576, canvas.getHeight());
        assertEquals(224, canvas.getRGB(0, 0) >>> 24);
        assertEquals(224, canvas.getRGB(1023, 575) >>> 24, "Bottom-right tile reaches the unchanged logical footprint");
        String center = registry.cursor(0, 0);
        assertTrue(center.length() >= 3 && center.length() <= 12, "Only occupied vertical tiles are emitted");
        var cursor = composite(entries, font, center, advances);
        assertEquals(-0.75, advances.get(center.charAt(0)), "Cursor (cropped one logical pixel on the left) stays centred on x0");
        int painted = 0;
        for (int y = 0; y < cursor.getHeight(); y++) for (int x = 0; x < cursor.getWidth(); x++)
            if ((cursor.getRGB(x, y) >>> 24) != 0) {
                painted++;
                assertTrue(x >= 500 && x < 528 && y >= 276 && y < 304, "Cross stays7 by7 logical units");
            }
        assertEquals(208, painted, "13 cross squares each use4 by4 texture pixels");
        assertNotEquals(registry.cursor(0, 0), registry.cursor(0, 1.0 / 72), "Each logical pixel row is its own cursor sprite");
        assertEquals(0, sum(center, advances), .00001);
    }

    @Test void everyGlyphFitsVanillaAtlasAndTiledFramesReconstructWithoutSeams() throws Exception {
        var screen = new Screen("curve", "CURVE", List.of(new Screen.Element("box", "TILE TEST", 0, 0, 1.75, 1.75,
                "button", "close", "rounded", true, null, true, null)));
        var registry = HudAssets.create(List.of(screen));
        var entries = registry.entries();
        String font = new String(entries.get("assets/tideframe/font/hud.json"), StandardCharsets.UTF_8);
        for (var entry : entries.entrySet()) if (entry.getKey().endsWith(".png")) {
            var image = ImageIO.read(new ByteArrayInputStream(entry.getValue()));
            assertTrue(image.getWidth() <= 256 && image.getHeight() <= 256,
                    "Vanilla FontTexture.SIZE=256 rejects oversized glyphs: " + entry.getKey());
            assertTrue(image.getHeight() % 64 == 0, "Vertical crops keep whole 4-unit steps");
        }
        var providers = providers(font);
        while (providers.find()) {
            int ascent = Integer.parseInt(providers.group(2));
            int height = Integer.parseInt(providers.group(3));
            assertTrue(height % 4 == 0 && height <= 16, "height " + height);
            assertTrue(ascent <= height, "Vanilla provider validates ascent <= height");
        }
        var advances = advances(entries, font);
        for (int step = 0; step <= 8; step++) for (String hover : new String[]{null, "box"}) {
            var reconstructed = composite(entries, font, registry.frame(screen, hover, step), advances);
            // The entrance is the native title fade: every step shows the final canvas (hover only once complete).
            var raster = HudRaster.render(screen, step < 8 ? null : hover, 8);
            assertArrayEquals(raster.getRGB(0, 0, 1024, 576, null, 0, 1024),
                    reconstructed.getRGB(0, 0, 1024, 576, null, 0, 1024), "Every pixel round-trips across tile seams");
        }
    }

    @Test void packAddsHudWithoutChangingLegacyOverloadAndStaysBounded() throws Exception {
        var legacy = PackGenerator.generate(temporary.resolve("legacy"));
        var empty = PackGenerator.generate(temporary.resolve("empty"), ImageGlyphs.empty(), HudAssets.empty());
        assertArrayEquals(Files.readAllBytes(legacy.path()), Files.readAllBytes(empty.path()));
        var registry = HudAssets.create(List.of(screen("menu")));
        var artifact = PackGenerator.generate(temporary.resolve("hud"), ImageGlyphs.empty(), registry);
        assertTrue(Files.size(artifact.path()) <= 64 * 1024 * 1024);
        try (var zip = new ZipFile(artifact.path().toFile())) {
            for (var entry : registry.entries().entrySet()) {
                assertNotNull(zip.getEntry(entry.getKey()));
                assertArrayEquals(entry.getValue(), zip.getInputStream(zip.getEntry(entry.getKey())).readAllBytes());
            }
            String provenance = new String(zip.getInputStream(zip.getEntry("ASSET_PROVENANCE.txt")).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(provenance.contains("HUD canvases"));
        }
    }

    @Test void incompressibleCanvasVariantsStopAtThePackByteBudget() throws Exception {
        int[] pixels = new int[1920 * 1080]; // near native resolution, so the large-size canvases stay incompressible
        var random = new java.util.Random(26144);
        var screens = new ArrayList<Screen>();
        // 1x atlas canvases plus native per-size canvases of incompressible noise exceed the 200 MiB budget.
        for (int i = 0; i < 14; i++) { // ~16 MB of incompressible PNG per screen across all sizes
            for (int pixel = 0; pixel < pixels.length; pixel++) pixels[pixel] = 0xff000000 | random.nextInt(0x1000000);
            var own = ImageAsset.fromPng(PngFixtures.rgba(1920, 1080, pixels));
            screens.add(new Screen("noise_" + i, "NOISE", List.of(
                new Screen.Element("picture", "SCREEN " + i, 0, 0, 2, 2, "label", "",
                        "rectangle", false, null, false, own)), dev.linqfy.bigCasares.modules.tideframe.engine.core.RenderQuality.custom(1024, 576)));
        }
        var error = assertThrows(IllegalArgumentException.class, () -> HudAssets.create(screens));
        assertEquals("HUD resource pack exceeds the 200 MiB limit", error.getMessage());
    }

    @Test void actualStoredZipSizeMatchesPreallocationBudgetIncludingTimestampExtras() throws Exception {
        var artifact = PackGenerator.generate(temporary.resolve("budget"), ImageGlyphs.empty(), HudAssets.create(List.of(screen("menu"))));
        var entries = new java.util.LinkedHashMap<String, byte[]>();
        try (var zip = new ZipFile(artifact.path().toFile())) {
            for (var enumeration = zip.entries(); enumeration.hasMoreElements();) {
                var entry = enumeration.nextElement();
                entries.put(entry.getName(), zip.getInputStream(entry).readAllBytes());
            }
        }
        assertEquals(Files.size(artifact.path()), PackGenerator.storedZipBytes(entries));
    }

    private static double sum(String text, java.util.Map<Character, Double> advances) {
        double total = 0;
        for (char character : text.toCharArray()) { assertTrue(advances.containsKey(character)); total += advances.get(character); }
        return total;
    }

    static java.util.Map<Character, Double> advances(java.util.Map<String, byte[]> entries, String font) throws Exception {
        var result = new java.util.HashMap<Character, Double>();
        var spaces = java.util.regex.Pattern.compile("\"\\\\u([0-9a-f]{4})\":(-?[0-9.]+)").matcher(font);
        while (spaces.find()) result.put((char)Integer.parseInt(spaces.group(1), 16), Double.parseDouble(spaces.group(2)));
        var bitmaps = providers(font);
        while (bitmaps.find()) {
            var image = ImageIO.read(new ByteArrayInputStream(entries.get("assets/tideframe/textures/" + bitmaps.group(1))));
            int inkWidth = 0;
            for (int x = 0; x < image.getWidth(); x++) for (int y = 0; y < image.getHeight(); y++)
                if ((image.getRGB(x, y) >>> 24) != 0) inkWidth = Math.max(inkWidth, x + 1);
            int height = Integer.parseInt(bitmaps.group(3));
            result.put((char)Integer.parseInt(bitmaps.group(4), 16), (double)(Math.round(inkWidth * ((float)height / image.getHeight())) + 1));
        }
        return result;
    }

    private static java.util.regex.Matcher providers(String font) {
        return java.util.regex.Pattern.compile("\"file\":\"tideframe:([^\"]+)\",\"ascent\":(-?[0-9]+),\"height\":([0-9]+),\"chars\":\\[\"\\\\u([0-9a-f]{4})\"\\]").matcher(font);
    }

    private static java.awt.image.BufferedImage composite(java.util.Map<String, byte[]> entries, String font,
                                                          String sequence, java.util.Map<Character, Double> advances) throws Exception {
        var result = new java.awt.image.BufferedImage(1024, 576, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int index = 0; index < sequence.length(); index += 3) {
            var providers = providers(font);
            boolean found = false;
            while (providers.find()) if ((char)Integer.parseInt(providers.group(4), 16) == sequence.charAt(index + 1)) {
                var tile = ImageIO.read(new ByteArrayInputStream(entries.get("assets/tideframe/textures/" + providers.group(1))));
                int left = (int)Math.round((advances.get(sequence.charAt(index)) * 4 + 128) * 4);
                int top = (15 - Integer.parseInt(providers.group(2))) * 16;
                for (int y = 0; y < tile.getHeight(); y++) for (int x = 0; x < tile.getWidth(); x++) {
                    int px = left + x, py = top + y;
                    if (px >= 0 && px < 1024 && py >= 0 && py < 576 && (tile.getRGB(x, y) >>> 24) != 0) result.setRGB(px, py, tile.getRGB(x, y));
                }
                found = true;
                break;
            }
            assertTrue(found, "Missing bitmap glyph");
        }
        return result;
    }
}
