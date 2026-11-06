package dev.linqfy.bigCasares.modules.tideframe.engine.core;

import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Text that shrinks to fit its box: the shared cases (src/test/resources/textfit.json), also run by editor/textfit.test.mjs. */
class TextFitTest {
    @SuppressWarnings("unchecked")
    private static Map<String, Object> cases() throws Exception {
        try (var in = TextFitTest.class.getResourceAsStream("/textfit.json")) {
            return (Map<String, Object>) ScreenCodec.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> cases, String key) { return (List<Map<String, Object>>) cases.get(key); }
    private static int number(Map<String, Object> entry, String key) { return ((BigDecimal) entry.get(key)).intValueExact(); }
    @SuppressWarnings("unchecked")
    private static Screen.Element element(Map<String, Object> entry) {
        var e = (Map<String, Object>) entry.get("element");
        var style = new Screen.Style(1, "#000000", "#000000", "#ffffff", false, 0, 0, 1, null, "none", (String) e.get("textAlign"), 0)
                .withText(((BigDecimal) e.get("textScale")).intValueExact(), null, false);
        return new Screen.Element("t", (String) e.getOrDefault("text", "{player}"), ((BigDecimal) e.get("x")).doubleValue(), ((BigDecimal) e.get("y")).doubleValue(),
                ((BigDecimal) e.get("width")).doubleValue(), ((BigDecimal) e.get("height")).doubleValue(), "label", "", "rectangle", false, null, false, null, style, 0, null);
    }

    @Test void charactersThatFitAtEachSize() throws Exception {
        for (var entry : list(cases(), "maxChars"))
            assertEquals(number(entry, "chars"), TextFit.maxChars(number(entry, "width"), number(entry, "textScale"), number(entry, "size")), entry.toString());
    }
    @Test void theLargestSizeThatFitsAmongThoseThePackHas() throws Exception {
        for (var entry : list(cases(), "sizes")) {
            var available = entry.containsKey("available") ? ((List<?>) entry.get("available")).stream().map(v -> ((BigDecimal) v).intValueExact()).toList() : TextFit.SIZES;
            assertEquals(number(entry, "size"), TextFit.size(element(entry), number(entry, "length"), available), entry.toString());
        }
        // At size 4 the count is LiveText's own: nothing changes for text that already fits.
        for (var entry : list(cases(), "sizes")) assertEquals(LiveText.maxChars(element(entry)), TextFit.maxChars(element(entry), TextFit.FULL));
    }
    @Test void aLineCutEvenAtHalfSizeEndsInAnEllipsis() throws Exception {
        for (var entry : list(cases(), "cut")) assertEquals(entry.get("shown"), TextFit.cut((String) entry.get("line"), number(entry, "max")), entry.toString());
    }
    @Test void theLongestALiveTextCanBeDecidesWhichSmallerFontsThePackCarries() throws Exception {
        for (var entry : list(cases(), "longest")) assertEquals(number(entry, "chars"), TextFit.longest((String) entry.get("text")), entry.toString());
        for (var entry : list(cases(), "liveSizes"))
            assertEquals(((List<?>) entry.get("sizes")).stream().map(v -> ((BigDecimal) v).intValueExact()).toList(), TextFit.liveSizes(element(entry)), entry.toString());
    }
    @Test void shrunkLinesKeepTheirCentreAndAlignment() throws Exception {
        for (var entry : list(cases(), "lineTop")) assertEquals(number(entry, "quarters"), TextFit.lineTopQuarters(element(entry), number(entry, "size")), entry.toString());
        for (var entry : list(cases(), "lineLeft"))
            assertEquals(number(entry, "quarters"), TextFit.lineLeftQuarters(element(entry), number(entry, "length"), number(entry, "size")), entry.toString());
    }
    /** A schema 8 file with one label {@code text} in a box {@code width} wide. */
    private static String v8(String text, double width) {
        return "{\"version\":8,\"id\":\"old\",\"title\":\"OLD\",\"quality\":{\"preset\":\"low\",\"width\":512,\"height\":288},\"presentation\":{\"mode\":\"menu\",\"scale\":1,\"cursor\":\"cross\"},"
                + "\"elements\":[{\"id\":\"t\",\"text\":\"" + text + "\",\"x\":0,\"y\":0,\"width\":" + width + ",\"height\":0.2,\"kind\":\"label\",\"action\":\"\",\"shape\":\"rectangle\",\"border\":false,"
                + "\"messageCondition\":null,\"background\":false,\"image\":null,\"style\":{\"opacity\":1,\"fillColor\":\"#28384b\",\"borderColor\":\"#ffffff\",\"textColor\":\"#ffffff\",\"shadow\":false,"
                + "\"rotation\":0,\"radius\":0.08,\"outlineWidth\":1,\"hoverColor\":null,\"hoverEffect\":\"none\",\"textAlign\":\"center\",\"wrapWidth\":0,\"brightness\":1,\"textScale\":1,"
                + "\"textOutline\":null,\"textShadow\":false,\"gradientColor\":null,\"gradientDirection\":\"vertical\",\"textGradient\":null},\"layer\":0,\"actionArea\":null}]}";
    }
    @Test void oldFilesKeepCuttingAndPagesThatShrinkAreWrittenAsSchemaNine() {
        // Live text or a line too long for its box: an old page is marked, so it keeps cutting, and stays an old page.
        for (String text : new String[]{"HOLA {player}", "UN TEXTO FIJO DEMASIADO LARGO PARA LA CAJA"}) {
            var old = ScreenCodec.decode(v8(text, .4));
            assertTrue(old.legacyText(), text);
            assertFalse(TextFit.allowed(old), text);
            String saved = ScreenCodec.encode(old);
            assertFalse(saved.contains("\"version\":9"), "saved again it stays schema 8: " + text);
            assertEquals(old, ScreenCodec.decode(saved));
        }
        // Text that fits: the page reads and compares exactly as before.
        assertFalse(ScreenCodec.decode(v8("CORTO", .4)).legacyText());
        // A page built in code shrinks; written to a file it becomes schema 9, so it still shrinks when read back.
        var label = ScreenCodec.decode(v8("CORTO", .4)).elements().getFirst().withText("Hola {player}");
        var current = new Screen("c", "C", List.of(label), RenderQuality.low());
        String saved = ScreenCodec.encode(current);
        assertTrue(saved.contains("\"version\":9"), saved);
        assertEquals(current, ScreenCodec.decode(saved));
        assertTrue(TextFit.allowed(ScreenCodec.decode(saved)));
        // The internal legacy quality never shrinks, so it never asks for schema 9 (which it cannot write).
        assertDoesNotThrow(() -> ScreenCodec.encode(new Screen("l", "L", List.of(new Screen.Element("t", "HOLA {player}", 0, 0, .4, .2, "label", "")))));
    }
    @Test void onlyCurrentPagesShrinkTheirText() {
        var live = new Screen.Element("hola", "Hola {player}", 0, 0, .78, .12, "label", "");
        var current = new Screen("p", "P", List.of(live), RenderQuality.low());
        assertTrue(TextFit.allowed(current));
        assertFalse(TextFit.allowed(new Screen("p", "P", List.of(live), RenderQuality.low(), Screen.Presentation.defaults(), dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ScreenLogic.EMPTY, true)), "schema 1-8");
        assertFalse(TextFit.allowed(new Screen("p", "P", List.of(live))), "the internal legacy quality");
        assertTrue(TextFit.mayShrink(live, live.text()));
        assertFalse(TextFit.mayShrink(new Screen.Element("s", "CORTO", 0, 0, .78, .12, "label", ""), "CORTO"));
        assertTrue(TextFit.mayShrink(new Screen.Element("s", "UN TEXTO FIJO MUY LARGO", 0, 0, .78, .12, "label", ""), "UN TEXTO FIJO MUY LARGO"));
        assertFalse(TextFit.mayShrink(new Screen.Element("d", "UN TEXTO FIJO MUY LARGO", 0, 0, .78, .12, "decoration", ""), "UN TEXTO FIJO MUY LARGO"), "decorations draw no text");
    }
}
