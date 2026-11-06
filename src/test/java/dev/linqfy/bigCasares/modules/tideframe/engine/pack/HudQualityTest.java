package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.RenderQuality;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class HudQualityTest {
    private static Screen canvas(String id, RenderQuality quality) {
        return new Screen(id, "QUALITY", List.of(new Screen.Element("canvas", "", 0, 0, 2, 2,
                "decoration", "", "rectangle", false, null, true, null,
                new Screen.Style(1, "#123456", "#abcdef", "#fedcba", false), 0, null)), quality);
    }

    @Test void selectableProfilesRenderTheirActualResolutionIncludingFourK() {
        for (var quality : List.of(RenderQuality.low(), RenderQuality.medium(), RenderQuality.high(),
                RenderQuality.custom(3840, 2160), RenderQuality.custom(3808, 2142))) {
            var raster = HudRaster.render(canvas("quality", quality), null, 8);
            assertEquals(quality.width(), raster.getWidth());
            assertEquals(quality.height(), raster.getHeight());
            assertEquals(0xff123456, raster.getRGB(raster.getWidth() / 2, raster.getHeight() / 2));
        }
    }

    @Test void opacityColorsStableLayersAndDecorationHoverAreApplied() {
        var front = new Screen.Element("front", "", 0, 0, .5, .5, "decoration", "", "rectangle", false,
                null, true, null, new Screen.Style(.5, "#0000ff", "#ffffff", "#ffffff", false), 2, null);
        var back = new Screen.Element("back", "", 0, 0, 1, 1, "decoration", "", "rectangle", false,
                null, true, null, new Screen.Style(1, "#ff0000", "#ffffff", "#ffffff", false), -1, null);
        var screen = new Screen("layers", "LAYERS", List.of(front, back), RenderQuality.low());
        var normal = HudRaster.render(screen, null, 8);
        assertEquals(255, normal.getRGB(256, 144) >>> 24);
        assertEquals(0xff7f0080, normal.getRGB(256, 144));
        assertArrayEquals(normal.getRGB(0, 0, 512, 288, null, 0, 512),
                HudRaster.render(screen, "front", 8).getRGB(0, 0, 512, 288, null, 0, 512));
        var transparent = new Screen.Element("hidden", "A", 0, 0, 1, 1, "label", "", "rectangle", true,
                null, true, null, new Screen.Style(0, "#ff0000", "#00ff00", "#0000ff", true), 0, null);
        assertEquals(0, HudRaster.render(new Screen("hidden", "HIDDEN", List.of(transparent), RenderQuality.low()),
                null, 8).getRGB(256, 144));
    }

    @Test void borderTextAndSoftShadowUseAuthoredStyle() {
        var styled = new Screen.Element("styled", "A", 0, 0, 1, 1, "label", "", "rectangle", true, null,
                false, null, new Screen.Style(.5, "#123456", "#00ff00", "#0000ff", true), 0, null);
        var raster = HudRaster.render(new Screen("styled", "STYLED", List.of(styled), RenderQuality.low()), null, 8);
        assertEquals(0x8000ff00, raster.getRGB(128, 100));
        assertEquals(0x800000ff, raster.getRGB(256, 136));
        int outerAlpha = raster.getRGB(387, 160) >>> 24;
        assertTrue(outerAlpha > 0 && outerAlpha < 64, "Soft shadow extends outside the visual rectangle");
        assertEquals(0, raster.getRGB(400, 160));
    }

    @Test void elementOpacityIsAppliedOnceAfterBorderImageAndBackgroundComposition() throws Exception {
        var photo = dev.linqfy.bigCasares.modules.tideframe.engine.core.ImageAsset.fromPng(dev.linqfy.bigCasares.modules.tideframe.engine.testing.PngFixtures.rgba(16, 9, 0xff204060));
        var styled = new Screen.Element("styled", "A", 0, 0, 1, 1, "button", "", "rectangle", true, null,
                true, photo, new Screen.Style(.5, "#ff0000", "#00ff00", "#0000ff", false), 0, null);
        var raster = HudRaster.render(new Screen("group", "GROUP", List.of(styled), RenderQuality.low()), null, 8);
        assertEquals(0x8000ff00, raster.getRGB(128, 100), "Border and background share one50% element opacity");
        assertEquals(0x80204060, raster.getRGB(256, 150), "Opaque photograph and fill do not compound alpha");
        assertEquals(0x800000ff, raster.getRGB(256, 136), "Text and photograph use the same group opacity");
    }

    @Test void lowMapsEveryEntranceStepToFinalAndOnlyButtonsGetHoverVariants() {
        var screen = canvas("low", RenderQuality.low());
        var registry = HudAssets.create(List.of(screen));
        for (int step = 0; step <= 8; step++) assertEquals(registry.frame(screen, null, 8), registry.frame(screen, null, step));
        assertThrows(IllegalArgumentException.class, () -> registry.frame(screen, "canvas", 8));
    }

    @Test void dynamicTilesHaveExactScaleZeroAdvanceAndCursorAlignedBaseline() throws Exception {
        var profiles = List.of(RenderQuality.low(), RenderQuality.medium(), RenderQuality.high(), RenderQuality.custom(3840, 2160),
                RenderQuality.custom(3808, 2142));
        for (var quality : profiles) {
            var screen = canvas(quality.preset(), quality);
            var registry = HudAssets.create(List.of(screen));
            var entries = registry.entries();
            String font = new String(entries.get("assets/tideframe/font/hud.json"), StandardCharsets.UTF_8);
            var spaces = new HashMap<Character, Double>();
            var spacing = Pattern.compile("\"\\\\u([0-9a-f]{4})\":(-?[0-9.]+)").matcher(font);
            while (spacing.find()) spaces.put((char)Integer.parseInt(spacing.group(1), 16), Double.parseDouble(spacing.group(2)));
            var glyphs = providers(entries, font);
            String frame = registry.frame(screen, null, 8);
            // Rasters wider than the 1024 atlas are area-filtered; the font sheets hold the atlas.
            int atlasWidth = Math.min(1024, quality.width()), atlasHeight = atlasWidth * 9 / 16;
            var reconstructed = new java.awt.image.BufferedImage(atlasWidth, atlasHeight, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            double right = 0, bottom = 0;
            for (int index = 0; index < frame.length(); index += 3) {
                var glyph = glyphs.get(frame.charAt(index + 1));
                double prefix = spaces.get(frame.charAt(index));
                double suffix = spaces.get(frame.charAt(index + 2));
                assertEquals(0, prefix + glyph.advance + suffix, .00001);
                assertEquals(64.0 / atlasWidth, (double)glyph.height / glyph.imageHeight, .00000001);
                double leftSource = (prefix * 4 + 128) * atlasWidth / 256.0;
                double topSource = (15 - glyph.ascent) * atlasWidth / 64.0;
                assertEquals(Math.rint(leftSource), leftSource, .00001, "Source tile x never needs spacing rounding");
                assertEquals(Math.rint(topSource), topSource, .00001, "Source tile y never needs ascent rounding");
                int left = (int)Math.round(leftSource), top = (int)Math.round(topSource);
                var tile = ImageIO.read(new ByteArrayInputStream(entries.get(glyph.path)));
                int copyWidth = Math.min(tile.getWidth(), atlasWidth - left), copyHeight = Math.min(tile.getHeight(), atlasHeight - top);
                reconstructed.setRGB(left, top, copyWidth, copyHeight, tile.getRGB(0, 0, copyWidth, copyHeight, null, 0, copyWidth), 0, copyWidth);
                right = Math.max(right, prefix * 4 + glyph.imageWidth * 4.0 * glyph.height / glyph.imageHeight);
                bottom = Math.max(bottom, (15 - glyph.ascent) * 4 + glyph.imageHeight * 4.0 * glyph.height / glyph.imageHeight);
            }
            assertEquals(128, right, .00001, "Canvas ends128 logical pixels right of center");
            var expected = HudAssets.fit(HudRaster.render(screen, null, 8));
            assertArrayEquals(((java.awt.image.DataBufferInt)expected.getRaster().getDataBuffer()).getData(),
                    ((java.awt.image.DataBufferInt)reconstructed.getRaster().getDataBuffer()).getData(), "All source texels reconstruct without seams");
            assertTrue(bottom >= 144 && bottom < 144 + 256 * 256.0 / atlasWidth, "Padding reaches beyond final content only");
            var first = glyphs.get(frame.charAt(1));
            assertEquals(15, first.ascent, "Every profile uses the centred title frame");
            String cursor = registry.cursor(screen, 0, 1);
            var cursorGlyph = glyphs.get(cursor.charAt(1));
            assertEquals(15, cursorGlyph.ascent, "Cursor top follows the same centred frame");
            assertEquals(cursor, registry.cursor(0, 1), "Legacy and new layouts share one cursor frame");
            for (String sequence : List.of(frame, cursor, registry.cursor(screen, .35, -.75))) {
                double advance = 0;
                for (char character : sequence.toCharArray()) advance += spaces.containsKey(character) ? spaces.get(character) : glyphs.get(character).advance;
                assertEquals(0, advance, .00001);
            }
        }
    }

    private record Bitmap(int ascent, int height, int imageWidth, int imageHeight, int advance, String path) { }
    private static Map<Character, Bitmap> providers(Map<String, byte[]> entries, String font) throws Exception {
        var result = new HashMap<Character, Bitmap>();
        var providers = Pattern.compile("\"file\":\"tideframe:([^\"]+)\",\"ascent\":(-?[0-9]+),\"height\":([0-9]+),\"chars\":\\[\"\\\\u([0-9a-f]{4})\"\\]").matcher(font);
        while (providers.find()) {
            var image = ImageIO.read(new ByteArrayInputStream(entries.get("assets/tideframe/textures/" + providers.group(1))));
            assertTrue(image.getWidth() <= 256 && image.getHeight() <= 256);
            int inkWidth = 0;
            for (int x = 0; x < image.getWidth(); x++) for (int y = 0; y < image.getHeight(); y++)
                if ((image.getRGB(x, y) >>> 24) != 0) inkWidth = Math.max(inkWidth, x + 1);
            int ascent = Integer.parseInt(providers.group(2)), height = Integer.parseInt(providers.group(3));
            assertTrue(ascent <= height);
            int advance = Math.round(inkWidth * ((float)height / image.getHeight())) + 1;
            char glyph = (char)Integer.parseInt(providers.group(4), 16);
            assertTrue((glyph >= 0xe000 && glyph <= 0xe7ff) || (glyph >= 0xf000 && glyph <= 0xf8ff));
            result.put(glyph, new Bitmap(ascent, height, image.getWidth(), image.getHeight(), advance,
                    "assets/tideframe/textures/" + providers.group(1)));
        }
        return result;
    }
}
