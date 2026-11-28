package dev.linqfy.bigCasares.modules.tideframe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PanelPackTest {
    @Test
    void missingEditableFontCannotPublishAnUnreadableMenu(@TempDir Path root) {
        assertThrows(IOException.class, () -> PanelPack.assemble(root));
    }

    @Test
    void menuGlyphsFitThe26_2OverlayAndEachItemUsesItsActualRow(@TempDir Path root) throws IOException {
        Path font = Path.of("assets/bigcasares/textures/font/fuente.png");
        Files.createDirectories(root.resolve(font).getParent());
        Files.copy(Path.of("resourcepack/java").resolve(font), root.resolve(font));
        Files.copy(Path.of("resourcepack/java/pack.mcmeta"), root.resolve("pack.mcmeta"));
        PanelPack.assemble(root);

        JsonObject metadata = read(root.resolve("pack.mcmeta"));
        var entry = metadata.getAsJsonObject("overlays").getAsJsonArray("entries").get(0).getAsJsonObject();
        assertEquals(88, entry.get("min_format").getAsInt());
        assertEquals(88, entry.get("max_format").getAsInt());
        String shader = Files.readString(root.resolve(entry.get("directory").getAsString())
                .resolve("assets/minecraft/shaders/core/text.vsh"));
        for (int rgb : new int[]{0xffffff, 0xffaa00, 0x5ed10f, 0x170f1d, 0xa83232}) {
            assertTrue(shader.contains("case " + PanelColours.marker(rgb) + ":"));
        }
        Map<Integer, JsonObject> glyphs = new HashMap<>();
        for (var element : read(root.resolve("assets/bigcasares/font/pantalla.json")).getAsJsonArray("providers")) {
            var provider = element.getAsJsonObject();
            if (provider.has("advances")) {
                var spaces = provider.getAsJsonObject("advances");
                assertEquals(5.0 / 32, spaces.get(PanelPack.glyph(PanelPack.TITLE_START)).getAsDouble() * 4);
                assertEquals(5.0 / 32, spaces.get(PanelPack.glyph(PanelPack.POINTER_START)).getAsDouble() * 2);
                // Inside the title's 40/256, a 32-wide run on row 2 of its group lands at (48 + 16 + 5)/256:
                // the shader reads row 2 and power 5 from it.
                assertEquals(29.0 / 256, spaces.get(PanelPack.glyph(PanelPack.runStart(2, 5))).getAsDouble() * 4);
                assertEquals(0.0, spaces.get(PanelPack.glyph(PanelPack.runStart(2, 5))).getAsDouble()
                        + spaces.get(PanelPack.glyph(PanelPack.runEnd(2, 5))).getAsDouble());
            }
            if (provider.has("chars")) {
                for (var row : provider.getAsJsonArray("chars")) {
                    row.getAsString().codePoints().forEach(code -> assertNull(glyphs.put(code, provider)));
                }
            }
        }
        for (int y : new int[]{53, 54, 73, 83, 92, 116}) {
            for (int index = 0; index < PanelPack.ICONS.size(); index++) {
                var sprite = glyphs.get(PanelPack.icon(index, y));
                assertNotNull(sprite);
                int top = 60 - 4 * sprite.get("ascent").getAsInt() + y % 4;
                assertEquals(y, top);
            }
        }
        for (int alpha : new int[]{56, 128, 242, 255}) {
            assertNotNull(glyphs.get(PanelPack.run(143, 7, PanelPack.opacity(alpha))));
        }
        // A barrel keeps its top distinct from its sides, a carved pumpkin its face; plain blocks share one texture.
        int barrel = PanelPack.iconIndex("barrel");
        assertEquals("minecraft:block/barrel_top.png", glyphs.get(PanelPack.icon(barrel, 0, 53)).get("file").getAsString());
        assertEquals("minecraft:block/barrel_side.png", glyphs.get(PanelPack.icon(barrel, 1, 53)).get("file").getAsString());
        assertEquals(PanelPack.icon(barrel, 1, 53), PanelPack.icon(barrel, 2, 53));
        int pumpkin = PanelPack.iconIndex("carved_pumpkin");
        // As in the vanilla inventory, the carved face is on the right.
        assertEquals("minecraft:block/pumpkin_side.png", glyphs.get(PanelPack.icon(pumpkin, 1, 83)).get("file").getAsString());
        assertEquals("minecraft:block/carved_pumpkin.png", glyphs.get(PanelPack.icon(pumpkin, 2, 83)).get("file").getAsString());
        int stone = PanelPack.iconIndex("stone");
        assertEquals(PanelPack.icon(stone, 0, 116), PanelPack.icon(stone, 2, 116));
        // The shader knows the three face markers it projects.
        assertTrue(shader.contains("0xfdfaf4") && shader.contains("gl_VertexID % 4"));
        // Run cells are solid, so the shader can cut each quad to its run's row and width whatever the GUI scale.
        var strips = javax.imageio.ImageIO.read(root.resolve("assets/bigcasares/textures/font/pantalla/trazos_3.png").toFile());
        for (int y : new int[]{0, 63, 64 * 3 + 40}) {
            for (int x : new int[]{0, 127, 6 * 128 + 64, 8 * 128 - 1}) assertEquals(255, strips.getRGB(x, y) >>> 24);
        }
        assertTrue(PanelColours.cubeMarker(2, 3) <= 0xfdfaff);
    }

    @Test
    void blocksAreCubesAndInventorySpritesStayFlat() {
        for (String block : new String[]{"stone", "cobblestone", "barrel", "carved_pumpkin", "sand", "bricks"}) {
            assertTrue(PanelPack.ICONS.get(PanelPack.iconIndex(block)).cube(), block);
        }
        for (String flat : new String[]{"ladder", "bread", "apple", "diamond_sword", "writable_book"}) {
            assertEquals(false, PanelPack.ICONS.get(PanelPack.iconIndex(flat)).cube(), flat);
        }
    }

    @Test
    void aHoveredSlotHighlightsBehindItsItemAndTheTooltipStaysInFront() {
        Panel panel = new Panel(true, 0, 1);
        PixelImage highlight = new PixelImage(18, 18).fill(0, 0, 18, 18, PixelImage.rgb(0x8b8b8b));
        PixelImage tooltip = new PixelImage(40, 10).fill(0, 0, 40, 10, PixelImage.rgb(0x170f1d));
        panel.item("stone", 25, 53);
        panel.highlight(24, 52, 18, 18, highlight, 24, 52);
        panel.hover(24, 52, 18, 18, tooltip, 44, 53);
        String drawn = flatten(PanelText.render(panel, 30, 60));
        int stoneFace = drawn.indexOf(PanelPack.glyph(PanelPack.icon(PanelPack.iconIndex("stone"), 0, 53)));
        int highlightRun = drawn.indexOf(PanelPack.glyph(PanelPack.run(52, 4, 3)));
        int tooltipRun = drawn.lastIndexOf(PanelPack.glyph(PanelPack.run(53, 5, 3)));
        assertTrue(highlightRun >= 0 && stoneFace > highlightRun, "highlight before the item");
        assertTrue(tooltipRun > stoneFace, "tooltip after the item");
    }

    @Test
    void aTooltipOverANeighboursCountOnlyUsesColoursTheShaderDraws() {
        // The shop's first two products, each with its "64", and the first one's tooltip over the second.
        Panel panel = new Panel(true, 0, 1);
        panel.item("stone", 25, 53);
        panel.text("64", 33, 61, ScreenArt.WHITE);
        panel.item("cobblestone", 52, 53);
        panel.text("64", 60, 61, ScreenArt.WHITE);
        panel.highlight(24, 52, 18, 18, ScreenArt.slotHover(), 24, 52);
        panel.hover(24, 52, 18, 18, ScreenArt.tooltip(List.of("Piedra x64", "Clic: comprar $ 64", "Clic der.: vender $ 4"),
                List.of(ScreenArt.WHITE, ScreenArt.GOLD, ScreenArt.GREEN)), 44, 53);
        Set<Integer> known = new HashSet<>();
        Matcher cases = Pattern.compile("case (\\d+):").matcher(PanelColours.shaderCases());
        while (cases.find()) known.add(Integer.parseInt(cases.group(1)));
        for (int marker = 0xfdfaf0; marker <= 0xfdfaff; marker++) known.add(marker);
        List<Integer> unknown = new ArrayList<>();
        collectColours(PanelText.render(panel, 30, 60), known, unknown);
        assertTrue(unknown.isEmpty(), "runs the shader would leave undrawn: " + unknown);
    }

    private static void collectColours(net.kyori.adventure.text.Component component, Set<Integer> known, List<Integer> unknown) {
        if (component.color() != null && !known.contains(component.color().value())) unknown.add(component.color().value());
        for (var child : component.children()) collectColours(child, known, unknown);
    }

    private static String flatten(net.kyori.adventure.text.Component component) {
        StringBuilder text = new StringBuilder();
        if (component instanceof net.kyori.adventure.text.TextComponent plain) text.append(plain.content());
        for (var child : component.children()) text.append(flatten(child));
        return text.toString();
    }

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }
}
