package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.ItemIcons;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ActionParser;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Element "item": the client's own item texture, named by the pack, placed where the title can draw it. */
class ItemIconAssetsTest {
    // A 16-pixel box: 16/128 wide, 16/72 tall.
    private static final double W = 16 / 128.0, H = 16 / 72.0;
    private static Screen.Element icon(String id, String item, double x, double y) {
        return new Screen.Element(id, "", x, y, W, H, "decoration", "", "rectangle", false, null, false, null, Screen.Style.defaults(), 0, null,
                null, null, item);
    }
    private static Screen screen(Screen.Element... elements) {
        return new Screen("shop", "SHOP", List.of(elements), dev.linqfy.bigCasares.modules.tideframe.engine.core.RenderQuality.low());
    }

    @Test void theTableNamesTheClientTextureAndItsPaintedColumns() {
        var diamond = ItemIcons.find("diamond").orElseThrow();
        assertEquals("item/diamond", diamond.texture());
        assertEquals(1, diamond.rows());
        assertTrue(ItemIcons.all().size() > 1000);
        var stone = ItemIcons.find("stone").orElseThrow();
        assertTrue(stone.block(), "a block item is drawn as one face");
        assertTrue(ItemIcons.find("leather_helmet").orElseThrow().tinted());
        assertTrue(ItemIcons.find("chest").isEmpty(), "special 3D models are left out");
        var magma = ItemIcons.find("magma_block").orElseThrow();
        assertEquals(magma.height() / magma.width(), magma.rows(), "animated textures: frames are rows, the first is drawn");
        // Vanilla's advance: (int)(0.5 + ink * height / cell) + 1.
        var narrow = new ItemIcons.Icon("x", "item/x", 16, 16, 1, 13, false, false);
        assertEquals(4, narrow.advance(4), "13 * 4/16 = 3.25 rounds to 3, plus 1");
        assertEquals(7, narrow.advance(7), "13 * 7/16 = 5.69 rounds to 6, plus 1");
        assertEquals(10, narrow.advance(11));
    }

    @Test void iconsComeInWholeSixteensAndOnlyInTheLowerPartOfTheMenu() {
        assertEquals(16, ItemIcons.size(W, H));
        assertEquals(16, ItemIcons.size(30 / 128.0, H), "the smaller side counts");
        assertEquals(32, ItemIcons.size(40 / 128.0, 40 / 72.0));
        assertEquals(64, ItemIcons.size(2, 2));
        assertEquals(16, ItemIcons.size(.01, .01));
        // Bottom edge exactly at y 64 (centre at 56): allowed; one pixel higher is not.
        assertTrue(ItemIcons.drawable(1 - 56 / 72.0, W, H));
        assertFalse(ItemIcons.drawable(1 - 55 / 72.0, W, H));
        var high = assertThrows(IllegalArgumentException.class, () -> icon("top", "diamond", 0, .8));
        assertTrue(high.getMessage().contains("lower part"), high.getMessage());
        assertThrows(IllegalArgumentException.class, () -> icon("x", "not_an_item", 0, -.5));
        var listLogic = ActionParser.element(ScreenCodec.parse("{\"list\":{\"source\":\"items\",\"rows\":2,\"spacing\":18}}"));
        assertThrows(IllegalArgumentException.class, () -> icon("list", "diamond", 0, -.5).withLogic(listLogic));
    }

    @Test void schemaNineCarriesTheItemAndOlderSchemasDoNot() {
        var shop = screen(icon("gem", "diamond", .3, -.4));
        String json = ScreenCodec.encode(shop);
        assertTrue(json.contains("\"item\":\"diamond\""), json);
        assertTrue(json.contains("\"version\":9"));
        assertEquals(shop, ScreenCodec.decode(json));
        String v8 = json.replace("\"version\":9", "\"version\":8");
        assertThrows(IllegalArgumentException.class, () -> ScreenCodec.decode(v8), "an older schema does not know item");
    }

    @Test void thePackNamesTheClientTextureAndEverySizeKeepsTheBaselineAndTheDrawablePart() throws Exception {
        var shop = screen(icon("gem", "diamond", .3, -.4), icon("ore", "magma_block", -.3, -.4));
        var assets = HudAssets.create(List.of(shop));
        var entries = assets.entries();
        assertFalse(entries.keySet().stream().anyMatch(path -> path.startsWith("assets/minecraft/")), "no game texture is copied into the pack");
        var provider = Pattern.compile("\\{\"type\":\"bitmap\",\"file\":\"minecraft:([a-z0-9_/]+)\\.png\",\"ascent\":(-?\\d+),\"height\":(\\d+),\"chars\":\\[\"\\\\u([0-9a-f]{4})\"((?:,\"\\\\u0000\")*)\\]\\}");
        for (int q : assets.sizes()) {
            String font = new String(entries.get("assets/" + HudAssets.fontName(q).replace(":", "/font/") + ".json"), StandardCharsets.UTF_8);
            var advances = new HashMap<>(HudAssetsTest.advances(entries, font));
            var found = provider.matcher(font);
            var textures = new HashMap<String, Integer>();
            while (found.find()) {
                int ascent = Integer.parseInt(found.group(2)), height = Integer.parseInt(found.group(3));
                assertTrue(ascent <= height, "the client rejects an ascent above the height");
                assertEquals(16 * q / 16, height, "a 16-pixel icon is q font units tall at size q");
                var icon = ItemIcons.all().values().stream().filter(i -> i.texture().equals(found.group(1))).findFirst().orElseThrow();
                assertEquals(icon.rows() - 1, found.group(5).split("u0000", -1).length - 1, "one grid row per stacked frame");
                advances.put((char) Integer.parseInt(found.group(4), 16), (double) icon.advance(height));
                textures.merge(found.group(1), 1, Integer::sum);
            }
            assertEquals(Map.of("item/diamond", 1, "block/magma", 1), textures, "size " + q);
            String glyphs = assets.displayText(assets.frame(shop, null, 8, q), q);
            double sum = 0;
            for (char ch : glyphs.toCharArray()) { assertTrue(advances.containsKey(ch), "unknown glyph at size " + q); sum += advances.get(ch); }
            assertEquals(0, sum, 1e-6, "the icons leave the pen where it started at size " + q);
        }
    }

    @Test void theIconKeepsOnlyLooksTheGameCanDrawAndHidesWithItsElement() {
        var plain = icon("gem", "diamond", 0, -.5);
        // Opacity, rotation, brightness, shadow and moving hover effects change the element but could not change the icon.
        java.util.function.Function<Screen.Style, Screen.Element> styled = plain::withStyle;
        for (var look : List.of(
                new Screen.Style(.5, "#28384b", "#ffffff", "#ffffff", false, 0, .08, 1, null, "none", "center", 0, 1),
                new Screen.Style(1, "#28384b", "#ffffff", "#ffffff", false, 10, .08, 1, null, "none", "center", 0, 1),
                new Screen.Style(1, "#28384b", "#ffffff", "#ffffff", false, 0, .08, 1, null, "none", "center", 0, 1.2),
                new Screen.Style(1, "#28384b", "#ffffff", "#ffffff", true, 0, .08, 1, null, "none", "center", 0, 1),
                new Screen.Style(1, "#28384b", "#ffffff", "#ffffff", false, 0, .08, 1, null, "translate", "center", 0, 1),
                new Screen.Style(1, "#28384b", "#ffffff", "#ffffff", false, 0, .08, 1, null, "scale", "center", 0, 1),
                Screen.Style.defaults().withBlur(2, 0, 0))) {
            var refused = assertThrows(IllegalArgumentException.class, () -> styled.apply(look), look.toString());
            assertTrue(refused.getMessage().contains("cannot follow the element's look"), refused.getMessage());
        }
        assertDoesNotThrow(() -> styled.apply(new Screen.Style(1, "#28384b", "#ffffff", "#ffffff", false, 0, .08, 1, "#ff0000", "glow", "center", 0, 1)),
                "a glow around the box and a hover colour are fine");
        var hidden = styled.apply(new Screen.Style(0, "#28384b", "#ffffff", "#ffffff", false, 0, .08, 1, null, "none", "center", 0, 1));
        var assets = HudAssets.create(List.of(screen(hidden)));
        String font = new String(assets.entries().get("assets/tideframe/font/hud.json"), StandardCharsets.UTF_8);
        assertFalse(font.contains("\"file\":\"minecraft:"), "opacity 0 hides the element, and its icon too");
    }

    @Test void anIconOnAnElementThatChangesAtRuntimeGoesWithItsLayer() {
        var hidden = icon("sale", "emerald", 0, -.5).withLogic(ActionParser.element(ScreenCodec.parse("{\"hidden\":true}")));
        var shop = screen(hidden, icon("gem", "diamond", .5, -.5));
        var assets = HudAssets.create(List.of(shop));
        String layer = assets.layer(shop, "sale", 4), frame = assets.frame(shop, null, 8, 4);
        assertNotNull(layer);
        var onlyInLayer = new java.util.HashSet<Character>();
        for (char ch : layer.toCharArray()) onlyInLayer.add(ch);
        for (char ch : frame.toCharArray()) onlyInLayer.remove(ch);
        assertFalse(onlyInLayer.isEmpty(), "the hidden element's icon is drawn only with its layer");
    }
}
