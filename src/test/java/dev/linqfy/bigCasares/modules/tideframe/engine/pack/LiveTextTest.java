package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.LiveText;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.RenderQuality;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Live text must land on exactly the pixels the baked raster would have used. */
public class LiveTextTest {
    /** Minimal 26.2 title renderer at 1x: 16 atlas px per font unit, origin y=-10, glyph top = 7 - ascent. */
    public static final class Sim {
        record Glyph(BufferedImage image, int ascent, int height, int cellX, int cellY, int cellW, int cellH) { }
        final Map<String, Map<Character, Double>> spaces = new HashMap<>();
        final Map<String, Map<Character, Glyph>> glyphs = new HashMap<>();
        public final BufferedImage canvas = new BufferedImage(1024, 576, BufferedImage.TYPE_INT_ARGB);
        public double x;
        public Sim(Map<String, byte[]> entries) throws Exception {
            for (var entry : entries.entrySet()) {
                if (!entry.getKey().startsWith("assets/tideframe/font/") || !entry.getKey().endsWith(".json")) continue;
                String font = "tideframe:" + entry.getKey().substring("assets/tideframe/font/".length()).replace(".json", "");
                String json = new String(entry.getValue(), StandardCharsets.UTF_8);
                var sp = new HashMap<Character, Double>(); var gl = new HashMap<Character, Glyph>();
                int spaceEnd = json.indexOf("\"type\":\"bitmap\"");
                var space = Pattern.compile("\"(\\\\u[0-9a-f]{4}| )\":(-?[0-9.E]+)").matcher(json.substring(0, spaceEnd < 0 ? json.length() : spaceEnd));
                while (space.find()) sp.put(ch(space.group(1)), Double.parseDouble(space.group(2)));
                var bitmap = Pattern.compile("\"file\":\"tideframe:([^\"]+)\",\"ascent\":(-?\\d+),\"height\":(\\d+),\"chars\":\\[((?:\"[^\\]]*\",?)+)\\]").matcher(json);
                while (bitmap.find()) {
                    var image = ImageIO.read(new ByteArrayInputStream(entries.get("assets/tideframe/textures/" + bitmap.group(1))));
                    var rows = new ArrayList<List<Character>>();
                    for (String row : bitmap.group(4).split("\",\"")) {
                        var cells = new ArrayList<Character>(); var m = Pattern.compile("\\\\u([0-9a-f]{4})").matcher(row);
                        while (m.find()) cells.add((char) Integer.parseInt(m.group(1), 16));
                        rows.add(cells);
                    }
                    int cw = image.getWidth() / rows.get(0).size(), chh = image.getHeight() / rows.size();
                    for (int r = 0; r < rows.size(); r++) for (int c = 0; c < rows.get(r).size(); c++) if (rows.get(r).get(c) != 0)
                        gl.put(rows.get(r).get(c), new Glyph(image, Integer.parseInt(bitmap.group(2)), Integer.parseInt(bitmap.group(3)), c * cw, r * chh, cw, chh));
                }
                spaces.put(font, sp); glyphs.put(font, gl);
            }
        }
        private static char ch(String token) { return token.equals(" ") ? ' ' : (char) Integer.parseInt(token.substring(2), 16); }
        public void draw(String font, String text, int tint) {
            var sp = spaces.get(font); var gl = glyphs.get(font);
            assertNotNull(sp, font);
            for (char c : text.toCharArray()) {
                if (sp.containsKey(c)) { x += sp.get(c); continue; }
                var g = gl.get(c); assertNotNull(g, font + " " + Integer.toHexString(c));
                assertTrue(g.ascent() <= g.height(), "client rejects ascent > height");
                double scale = (double) g.height() / g.cellH();
                int left = (int) Math.round((x * 4 + 128) * 4), top = (int) Math.round(((-10 + 7 - g.ascent()) * 4 + 72) * 4);
                int ink = 0;
                for (int px = 0; px < g.cellW(); px++) for (int py = 0; py < g.cellH(); py++) {
                    int argb = g.image().getRGB(g.cellX() + px, g.cellY() + py);
                    if ((argb >>> 24) != 0) ink = px + 1;
                    if ((argb >>> 24) < 128) continue;
                    int tx = left + px, ty = top + py;
                    if (tx >= 0 && ty >= 0 && tx < 1024 && ty < 576) canvas.setRGB(tx, ty, tint == 0 ? argb : 0xff000000 | tint);
                }
                x += (int) (0.5 + ink * scale) + 1;
            }
        }
    }

    private static Screen screen(String text, String align) {
        var label = new Screen.Element("tag", text, .3, .5, .7, .12, "label", "", "rectangle", false, null, false, null,
                new Screen.Style(1, "#000000", "#000000", "#ff8800", false, 0, 0, 1, null, "none", align, 0), 5, null);
        return new Screen("live", "LIVE", List.of(label), RenderQuality.custom(1024, 576), new Screen.Presentation("menu", 1, "cross"));
    }

    @Test void liveTextMatchesTheBakedTextPixelForPixel() throws Exception {
        for (String align : new String[]{"left", "center", "right"}) {
            var baked = screen("STEVE 42", align); var live = screen("{player}", align);
            var bakedAssets = HudAssets.create(List.of(baked)); var liveAssets = HudAssets.create(List.of(live));
            var expected = new Sim(bakedAssets.entries());
            expected.draw("tideframe:hud", bakedAssets.frame(baked, null, 8), 0);
            var actual = new Sim(liveAssets.entries());
            actual.draw("tideframe:hud", liveAssets.frame(live, null, 8), 0);
            var element = live.elements().getFirst();
            String text = LiveText.sanitize("Steve 42", LiveText.maxChars(element));
            int prefix = LiveText.lineLeft(element, text.length()) - 128;
            actual.draw("tideframe:hud", HudAssets.hudSpace(prefix), 0);
            actual.draw(liveAssets.liveFont(live, element, 4), HudAssets.liveGlyphs(text, 1, 4), 0xff8800);
            actual.draw("tideframe:hud", HudAssets.hudSpace(-prefix - text.length() * 6), 0);
            assertEquals(0, actual.x, 1e-9, "live layer has zero net advance");
            int compared = 0;
            for (int y = 0; y < 576; y++) for (int xx = 0; xx < 1024; xx++) {
                boolean want = (expected.canvas.getRGB(xx, y) & 0xffffff) == 0xff8800 && (expected.canvas.getRGB(xx, y) >>> 24) == 255;
                boolean got = (actual.canvas.getRGB(xx, y) & 0xffffff) == 0xff8800 && (actual.canvas.getRGB(xx, y) >>> 24) == 255;
                assertEquals(want, got, align + " pixel " + xx + "," + y);
                if (want) compared++;
            }
            assertTrue(compared > 200, "text pixels compared: " + compared);
        }
    }

    @Test void everyDisplaySizeKeepsLiveGlyphsValidAndZeroAdvance() throws Exception {
        var live = screen("HP {health}", "left"); var assets = HudAssets.create(List.of(live));
        var element = live.elements().getFirst();
        for (int q : assets.sizes()) {
            String font = assets.liveFont(live, element, q); assertNotNull(font);
            String json = new String(assets.entries().get("assets/" + font.replace(":", "/font/") + ".json"), StandardCharsets.UTF_8);
            var m = Pattern.compile("\"ascent\":(-?\\d+),\"height\":(\\d+)").matcher(json); assertTrue(m.find());
            assertTrue(Integer.parseInt(m.group(1)) <= Integer.parseInt(m.group(2)));
            // Each corrected glyph advances exactly 6 logical px at this size: 0.375 * q units.
            double correction = HudAssets.liveCorrection(1, q);
            int actual = (int) (0.5f + 24 * (float) (4 * q) / 256) + 1;
            assertEquals(.375 * q, actual + correction, 1e-9);
        }
        assertNull(assets.liveFont(live, new Screen.Element("x", "STATIC", 0, 0, .4, .2, "label", ""), 4));
    }

    @Test void liveElementsAreLeftOutOfTheBakedCanvas() {
        var live = screen("{player}", "left");
        var raster = HudRaster.render(live, null, 8);
        for (int y = 0; y < raster.getHeight(); y++) for (int x = 0; x < raster.getWidth(); x++)
            assertEquals(0, raster.getRGB(x, y) >>> 24, "nothing baked for a live label");
        assertTrue(LiveText.isLive("{balance} COINS")); assertTrue(LiveText.isLive("%vault_eco_balance%"));
        assertFalse(LiveText.isLive("100% FUN")); assertFalse(LiveText.isLive("{PLAYER}"));
        assertEquals("STEVEÑ", LiveText.sanitize("Steveñ", 10), "accented letters have capitals in the font");
        assertEquals("STEVE?", LiveText.sanitize("Steve\u2603", 10), "a snowman does not");
        assertEquals("ABC", LiveText.sanitize("abcdef", 3));
    }

    private static Screen styled(String text, String align) { return styled(text, align, null); }
    private static Screen styled(String text, String align, String gradient) {
        var style = new Screen.Style(1, "#000000", "#000000", "#ff8800", false, 0, 0, 1, null, "none", align, 0).withText(2, "#203040", true).withTextGradient(gradient);
        var label = new Screen.Element("tag", text, .3, .5, .9, .2, "label", "", "rectangle", false, null, false, null, style, 5, null);
        return new Screen("live", "LIVE", List.of(label), RenderQuality.custom(1024, 576), new Screen.Presentation("menu", 1, "cross"));
    }

    @Test void decoratedLiveTextMatchesTheBakedTextPixelForPixel() throws Exception {
        for (String align : new String[]{"left", "center", "right", "gradient"}) {
            String gradient = align.equals("gradient") ? "#ffee55" : null;
            if (gradient != null) align = "center";
            var baked = styled("STEVE 42", align, gradient); var live = styled("{player}", align, gradient);
            var bakedAssets = HudAssets.create(List.of(baked)); var liveAssets = HudAssets.create(List.of(live));
            var expected = new Sim(bakedAssets.entries());
            expected.draw("tideframe:hud", bakedAssets.frame(baked, null, 8), 0);
            var actual = new Sim(liveAssets.entries());
            var element = live.elements().getFirst();
            assertTrue(HudAssets.liveColorsBaked(element));
            String text = LiveText.sanitize("Steve 42", LiveText.maxChars(element));
            assertEquals("STEVE 42", text);
            int prefix = LiveText.lineLeft(element, text.length()) - HudAssets.livePad(element) - 128;
            actual.draw("tideframe:hud", HudAssets.hudSpace(prefix), 0);
            actual.draw(liveAssets.liveFont(live, element, 4), HudAssets.liveGlyphs(element, text, 1, 4), 0);
            actual.draw("tideframe:hud", HudAssets.hudSpace(-prefix - text.length() * 12), 0);
            assertEquals(0, actual.x, 1e-9, "live layer has zero net advance");
            int compared = 0, outline = 0, shadow = 0;
            for (int y = 0; y < 576; y++) for (int xx = 0; xx < 1024; xx++) {
                int want = expected.canvas.getRGB(xx, y), got = actual.canvas.getRGB(xx, y);
                assertEquals(want >>> 24 == 255 ? want : 0, got >>> 24 == 255 ? got : 0, align + " pixel " + xx + "," + y);
                if (want >>> 24 == 255) compared++;
                if ((want & 0xffffff) == 0x203040) outline++;
                if ((want & 0xffffff) == 0x080c10) shadow++;
            }
            assertTrue(compared > 2000 && outline > 500 && shadow > 100, compared + " " + outline + " " + shadow);
            if (gradient != null) {
                // Top rows keep the text colour, the bottom rows reach the gradient colour.
                var colours = new java.util.HashSet<Integer>();
                for (int y = 0; y < 576; y++) for (int xx = 0; xx < 1024; xx++) colours.add(expected.canvas.getRGB(xx, y) & 0xffffff);
                assertTrue(colours.contains(0xff8800) && colours.contains(0xffee55), "gradient endpoints present");
            }
        }
    }

    @Test void progressBarFillCoversExactlyItsFractionOfTheTrack() throws Exception {
        var track = new Screen.Element("hp", "{bar:health}", -.2, -.4, .8, .1, "label", "", "rounded", true, null, true, null,
                new Screen.Style(1, "#202020", "#ffffff", "#e04848", false, 0, .3, 1, null, "none", "center", 0), 2, null);
        var screen = new Screen("bars", "BARS", List.of(track), RenderQuality.custom(1024, 576), new Screen.Presentation("overlay", 1, "cross"));
        assertTrue(LiveText.isBar(track)); assertFalse(LiveText.isLive(track)); assertEquals("health", LiveText.barSource(track));
        var assets = HudAssets.create(List.of(screen));
        int[] box = LiveText.barBox(track);
        for (String value : new String[]{"0", "0.25", "0.5", "1", "nope"}) {
            var sim = new Sim(assets.entries());
            int filled = LiveText.barPixels(value, box[2]);
            if (filled > 0) {
                int prefix = box[0] - 128;
                sim.draw("tideframe:hud", HudAssets.hudSpace(prefix), 0);
                sim.draw(assets.barFont(screen, track, 4), HudAssets.barGlyphs(filled), 0);
                sim.draw("tideframe:hud", HudAssets.hudSpace(-prefix - filled), 0);
                assertEquals(0, sim.x, 1e-9);
            }
            for (int y = 0; y < 576; y++) for (int x = 0; x < 1024; x++) {
                boolean inside = x >= box[0] * 4 && x < (box[0] + filled) * 4 && y >= box[1] * 4 && y < (box[1] + box[3]) * 4;
                assertEquals(inside, (sim.canvas.getRGB(x, y) >>> 24) == 255, value + " pixel " + x + "," + y);
            }
        }
        for (int q : assets.sizes()) for (int k = 0; k < 7; k++) {
            int width = 1 << k, actual = (int) (0.5f + 4 * width * (float) (4 * q) / 256) + 1;
            assertEquals(width / 16.0 * q, actual + HudAssets.barCorrection(1, q, width), 1e-9);
        }
        // Each bitmap glyph must fit the client's 256x256 font sheet.
        for (var entry : assets.entries().entrySet()) if (entry.getKey().contains("/font/bar/")) {
            var image = ImageIO.read(new ByteArrayInputStream(entry.getValue()));
            assertTrue(image.getWidth() <= 256 && image.getHeight() <= 256, entry.getKey());
        }
        assertEquals(255, HudAssets.barGlyphs(255).chars().filter(c -> c >= 0xe100 && c < 0xe107)
                .map(c -> 1 << (c - 0xe100)).sum());
        // The baked canvas keeps the track but not the token text.
        var raster = HudRaster.render(screen, null, 8);
        assertEquals(0x202020, raster.getRGB((box[0] + box[2] / 2) * 4, (box[1] + 2) * 4) & 0xffffff);
    }

    @Test void bigTextScalesTheGlyphGrid() {
        var plain = HudRaster.render(screen("I", "center"), null, 8);
        var big = HudRaster.render(new Screen("s", "S", List.of(new Screen.Element("tag", "I", .3, .5, .7, .3, "label", "", "rectangle", false, null, false, null,
                new Screen.Style(1, "#000000", "#000000", "#ff8800", false, 0, 0, 1, null, "none", "center", 0).withText(3, null, false), 5, null)),
                RenderQuality.custom(1024, 576), new Screen.Presentation("menu", 1, "cross")), null, 8);
        assertEquals(count(plain) * 9, count(big));
    }
    private static int count(BufferedImage image) {
        int n = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) if ((image.getRGB(x, y) >>> 24) == 255) n++;
        return n;
    }
}
