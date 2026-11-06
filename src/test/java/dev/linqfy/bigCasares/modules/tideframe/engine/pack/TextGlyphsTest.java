package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.LiveText;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.RenderQuality;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Lower case, accented letters and symbols: glyph table, text case, live grids, schema 9. */
class TextGlyphsTest {
    private static Screen screen(String text, String textCase, double y) {
        var style = new Screen.Style(1, "#000000", "#000000", "#ff8800", false, 0, 0, 1, null, "none", "left", 0).withTextCase(textCase);
        var label = new Screen.Element("tag", text, .3, y, .9, .12, "label", "", "rectangle", false, null, false, null, style, 5, null);
        return new Screen("live", "LIVE", List.of(label), RenderQuality.custom(1024, 576), new Screen.Presentation("menu", 1, "cross"));
    }

    @Test void everyDrawableCharacterHasAFiveBySevenGlyph() {
        String all = LiveText.CHARSET + LiveText.EXTRA;
        var seen = new HashSet<String>();
        for (char c : all.toCharArray()) {
            assertTrue(Glyphs.has(c), String.valueOf(c));
            String[] rows = Glyphs.pattern(c).split("/");
            assertEquals(7, rows.length, String.valueOf(c));
            boolean ink = false;
            for (String row : rows) { assertTrue(row.matches("[01]{5}"), c + " " + row); ink |= row.contains("1"); }
            assertTrue(ink, c + " draws something");
            if (Character.isLetter(c)) assertTrue(seen.add(Glyphs.pattern(c)) || duplicateByDesign(c), "duplicate glyph for " + c);
        }
        for (char c = 'a'; c <= 'z'; c++) assertNotEquals(PackGenerator.pattern(c), Glyphs.pattern(c), "real lower case " + c);
        assertFalse(Glyphs.has('☃'));
    }
    /** A 7-row cell leaves accented O at the same 5-row body in both cases. */
    private static boolean duplicateByDesign(char c) { return "óòôöõ".indexOf(c) >= 0; }

    @Test void upperCaseMapsEveryAccentedLetterAndNothingElse() {
        for (int i = 0; i < LiveText.LATIN_LOWER.length(); i++) {
            char lower = LiveText.LATIN_LOWER.charAt(i), upper = LiveText.LATIN_UPPER.charAt(i);
            assertEquals(upper, LiveText.upper(lower)); assertEquals(Character.toUpperCase(lower), upper);
            assertEquals(Glyphs.pattern(upper), Glyphs.pattern(lower, true), "capitals in upper mode");
        }
        assertEquals('µ', LiveText.upper('µ'), "no Greek capital the font lacks");
        assertEquals("AÑO ★", LiveText.upper("año ★"));
        assertEquals(68 + 24 + 24, LiveText.UPPER_GRID.length());
        assertTrue(LiveText.UPPER_GRID.chars().noneMatch(c -> c >= 'a' && c <= 'z' || LiveText.LATIN_LOWER.indexOf(c) >= 0));
        assertEquals(94 + 72, LiveText.MIXED_GRID.length());
    }

    @Test void textCaseDecidesHowTheCanvasDrawsLetters() {
        var upper = HudRaster.render(screen("Año", "upper", .5), null, 8);
        var caps = HudRaster.render(screen("AÑO", "upper", .5), null, 8);
        var original = HudRaster.render(screen("Año", "original", .5), null, 8);
        boolean same = true, differs = false;
        for (int y = 0; y < upper.getHeight(); y++) for (int x = 0; x < upper.getWidth(); x++) {
            same &= upper.getRGB(x, y) == caps.getRGB(x, y);
            differs |= upper.getRGB(x, y) != original.getRGB(x, y);
        }
        assertTrue(same, "upper case draws the capitals, accents included");
        assertTrue(differs, "original draws the lower-case glyphs");
    }

    @Test void sanitizeKeepsTheCaseTheElementAsksFor() {
        assertEquals("AÑO ¿QUÉ?", LiveText.sanitize("año ¿qué?", 20));
        assertEquals("año ¿qué?", LiveText.sanitize("año ¿qué?", 20, false));
        assertEquals("Hi ?!", LiveText.sanitize("Hi 😀!", 20, false), "one ? per emoji");
        assertEquals("❤ ✔ ★ 'x' - \"y\"", LiveText.sanitize("♥️ ✓ ⭐ ‘x’ – “y”", 40, false), "look-alikes");
        assertEquals("ab", LiveText.sanitize("abc", 2, false));
        assertTrue(LiveText.drawable("¡Hola, Ñandú! 3×2 → ★\nsegunda línea"));
        assertFalse(LiveText.drawable("snow ☃")); assertFalse(LiveText.drawable("tab\t"));
        assertTrue(LiveText.extended("año")); assertFalse(LiveText.extended("PLAIN\nASCII"));
    }

    @Test void liveGridsCarryTheCharactersOfTheirCase() throws Exception {
        var upper = screen("{player} ★", "upper", .5); var mixed = screen("{player} ★", "original", .5);
        var upperAssets = HudAssets.create(List.of(upper)); var mixedAssets = HudAssets.create(List.of(mixed));
        String upperJson = font(upperAssets, upper, upper.elements().getFirst());
        String mixedJson = font(mixedAssets, mixed, mixed.elements().getFirst());
        for (char c : "AZ0Ñ¿★❤→".toCharArray()) assertTrue(upperJson.contains(escaped(c)), "upper grid has " + c);
        for (char c : "añ".toCharArray()) assertFalse(upperJson.contains(escaped(c)), "upper grid has no " + c);
        for (char c : "AaÑñ¿★".toCharArray()) assertTrue(mixedJson.contains(escaped(c)), "mixed grid has " + c);
        // Both cases in one pack get their own fonts.
        var both = HudAssets.create(List.of(upper, new Screen("other", "OTHER", mixed.elements(), mixed.quality(), mixed.presentation())));
        assertNotEquals(both.liveFont(upper, upper.elements().getFirst(), 4), both.liveFont(mixed, mixed.elements().getFirst(), 4));
    }

    @Test void mixedCaseLiveTextMatchesTheBakedTextPixelForPixel() throws Exception {
        for (double y : new double[]{.85, .5, -.2, -.75}) {
            var baked = screen("Año ¿Sí? ★", "original", y); var live = screen("{player}", "original", y);
            var bakedAssets = HudAssets.create(List.of(baked)); var liveAssets = HudAssets.create(List.of(live));
            var expected = new LiveTextTest.Sim(bakedAssets.entries());
            expected.draw("tideframe:hud", bakedAssets.frame(baked, null, 8), 0);
            var actual = new LiveTextTest.Sim(liveAssets.entries());
            actual.draw("tideframe:hud", liveAssets.frame(live, null, 8), 0);
            var element = live.elements().getFirst();
            String text = LiveText.sanitize("Año ¿Sí? ★", element);
            assertEquals("Año ¿Sí? ★", text);
            int prefix = LiveText.lineLeft(element, text.length()) - 128;
            actual.draw("tideframe:hud", HudAssets.hudSpace(prefix), 0);
            actual.draw(liveAssets.liveFont(live, element, 4), HudAssets.liveGlyphs(text, 1, 4), 0xff8800);
            actual.draw("tideframe:hud", HudAssets.hudSpace(-prefix - text.length() * 6), 0);
            assertEquals(0, actual.x, 1e-9);
            int compared = 0;
            for (int py = 0; py < 576; py++) for (int px = 0; px < 1024; px++) {
                boolean want = (expected.canvas.getRGB(px, py) & 0xffffff) == 0xff8800 && (expected.canvas.getRGB(px, py) >>> 24) == 255;
                boolean got = (actual.canvas.getRGB(px, py) & 0xffffff) == 0xff8800 && (actual.canvas.getRGB(px, py) >>> 24) == 255;
                assertEquals(want, got, "y=" + y + " pixel " + px + "," + py);
                if (want) compared++;
            }
            assertTrue(compared > 300, "pixels compared: " + compared);
        }
    }

    @Test void lowerRowsUseShorterLiveCells() throws Exception {
        int[] cells = new int[3]; int k = 0;
        for (double y : new double[]{.85, .5, -.2}) {
            var live = screen("{player}", "upper", y); var assets = HudAssets.create(List.of(live));
            var element = live.elements().getFirst();
            String json = font(assets, live, element);
            var m = Pattern.compile("\"file\":\"tideframe:([^\"]+)\",\"ascent\":(-?\\d+),\"height\":(\\d+)").matcher(json);
            assertTrue(m.find());
            var image = ImageIO.read(new ByteArrayInputStream(assets.entries().get("assets/tideframe/textures/" + m.group(1))));
            int rows = (LiveText.UPPER_GRID.length() + 15) / 16;
            cells[k++] = image.getHeight() / rows;
            for (int q : assets.sizes()) {
                String sized = new String(assets.entries().get("assets/" + assets.liveFont(live, element, q).replace(":", "/font/") + ".json"), StandardCharsets.UTF_8);
                var s = Pattern.compile("\"ascent\":(-?\\d+),\"height\":(\\d+)").matcher(sized); assertTrue(s.find());
                assertTrue(Integer.parseInt(s.group(1)) <= Integer.parseInt(s.group(2)), "client rejects ascent > height");
                assertEquals(cells[k - 1] * q / 64, Integer.parseInt(s.group(2)), "same units per pixel at every cell height");
            }
        }
        assertEquals(256, cells[0], "a row near the top needs the full cell");
        assertEquals(128, cells[1]);
        assertEquals(64, cells[2], "low rows take a quarter of the memory");
    }

    @Test void schemaNineCarriesTextCaseAndAccents() {
        var gradient = new Screen.Style(1, "#000000", "#000000", "#ff8800", false, 0, 0, 1, null, "none", "left", 0).withGradient("#112233", "vertical");
        var base = screen("PLAIN", "upper", .5);
        var plain = base.withElements(List.of(base.elements().getFirst().withStyle(gradient)));
        String v8 = ScreenCodec.encode(plain);
        assertTrue(v8.startsWith("{\"version\":8"), v8.substring(0, 20));
        assertThrows(IllegalArgumentException.class, () -> ScreenCodec.decode(v8.replace("\"textGradient\":null", "\"textGradient\":null,\"textCase\":\"original\"")),
                "schema 8 has no textCase");
        for (var screen : List.of(screen("Año", "upper", .5), screen("Mixed", "original", .5))) {
            String json = ScreenCodec.encode(screen);
            assertTrue(json.startsWith("{\"version\":9"), json.substring(0, 20));
            assertEquals(screen, ScreenCodec.decode(json));
        }
        assertTrue(ScreenCodec.encode(screen("Mixed", "original", .5)).contains("\"textCase\":\"original\""));
        assertFalse(ScreenCodec.encode(screen("Año", "upper", .5)).contains("textCase"), "upper is the default and is not written");
        assertThrows(IllegalArgumentException.class, () -> screen("x", "lower", .5));
        var titled = new Screen("acentos", "Menú Ñandú", plain.elements(), plain.quality(), plain.presentation());
        String json = ScreenCodec.encode(titled);
        assertTrue(json.startsWith("{\"version\":9"));
        assertEquals(titled, ScreenCodec.decode(json));
    }

    private static String font(HudAssets assets, Screen screen, Screen.Element element) {
        String name = assets.liveFont(screen, element, 4); assertNotNull(name);
        return new String(assets.entries().get("assets/" + name.replace(":", "/font/") + ".json"), StandardCharsets.UTF_8);
    }
    private static String escaped(char c) { return String.format(java.util.Locale.ROOT, "\\u%04x", (int) c); }
}
