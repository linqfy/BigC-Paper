package dev.linqfy.bigCasares.modules.tideframe.engine.core;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.RichText.Run;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Idea 28: words in their own colour, with the tag rules editor/richtext.mjs shares (editor/richtext.test.mjs). */
class RichTextTest {
    @Test void namedAndHexTagsColourTheirWordsAndNest() {
        assertEquals(List.of(new Run("Saldo: ", null), new Run("100", "#ffaa00"), new Run(" ok", null)), RichText.runs("Saldo: <gold>100</gold> ok"));
        assertEquals(List.of(new Run("a", "#ffcc00"), new Run("b", "#ff5555"), new Run("c", "#ffcc00"), new Run("d", null)),
                RichText.runs("<#FFCC00>a<red>b</red>c</#ffcc00>d"), "hex is read in any case; the inner tag wins until it closes");
        assertEquals(List.of(new Run("a", "#ff5555"), new Run("b", "#5555ff"), new Run("c", null)), RichText.runs("<red>a<blue>b</red>c"),
                "closing a tag also closes the ones opened after it");
        assertEquals(List.of(new Run("rest", "#55ff55")), RichText.runs("<green>rest"), "an unclosed tag runs to the end");
        assertEquals(List.of(new Run("ab", "#aaaaaa")), RichText.runs("<grey>a</grey><gray>b</gray>"), "same colour, one run");
        assertEquals(List.of("#ffaa00", "#aa0000"), RichText.colors("<gold>a</gold> <dark_red>b <gold>c"));
    }
    @Test void anythingThatIsNotAColourTagStaysText() {
        for (String text : List.of("<3 a<b <bold>x</bold>", "</gold> closes nothing", "<#ffcc0>x", "<GOLD>x", "<gold >x", "x < y > z", "{coins < 5 ? \"a\" : \"b\"}"))
            assertEquals(List.of(new Run(text, null)), RichText.runs(text), text);
        assertFalse(RichText.hasTags("<3 a<b"));
        assertEquals(List.of(new Run("<gold>x", null)), RichText.runs("\\<gold>x"), "\\< draws a < a tag would take");
        assertEquals("a\\b", RichText.plain("a\\b"), "a backslash before anything else is text");
        assertTrue(RichText.hasTags("\\<gold>x") && RichText.hasTags("<red></red>"));
    }
    @Test void escapingKeepsOldTextsAndFilledInValuesAsWritten() {
        for (String old : List.of("<gold>hi</gold>", "a\\<b", "\\\\<red>x", "<3 <b>", "x</red>", "plain"))
            assertEquals(List.of(new Run(old, null)), RichText.runs(RichText.escape(old)), old);
        assertEquals("<3 <b>", RichText.escape("<3 <b>"), "a < that no tag takes is left alone");
        var filled = List.of(new Run("Hi ", null), new Run("<red>Steve\\<x</red>", "#ffaa00"), new Run("C:\\", "#55ff55"), new Run(" ok", null));
        assertEquals(filled, RichText.decompose(RichText.compose(filled)), "a value cannot open or close a tag, nor a trailing backslash take one");
        assertEquals(List.of(new Run("plain", null)), RichText.decompose("plain"), "a value without the frame is one run");
    }
    @Test void oldPagesKeepTheirTagsAsTextAndColouredTextsAreSchemaNine() {
        String v8 = "{\"version\":8,\"id\":\"old\",\"title\":\"OLD\",\"quality\":{\"preset\":\"low\",\"width\":512,\"height\":288},\"presentation\":{\"mode\":\"menu\",\"scale\":1,\"cursor\":\"cross\"},"
                + "\"elements\":[{\"id\":\"t\",\"text\":\"<RED>OK</RED> <red>x\",\"x\":0,\"y\":0,\"width\":1,\"height\":0.2,\"kind\":\"label\",\"action\":\"\",\"shape\":\"rectangle\",\"border\":false,"
                + "\"messageCondition\":null,\"background\":false,\"image\":null,\"style\":{\"opacity\":1,\"fillColor\":\"#28384b\",\"borderColor\":\"#ffffff\",\"textColor\":\"#ffffff\",\"shadow\":false,"
                + "\"rotation\":0,\"radius\":0.08,\"outlineWidth\":1,\"hoverColor\":null,\"hoverEffect\":\"none\",\"textAlign\":\"center\",\"wrapWidth\":0,\"brightness\":1,\"textScale\":1,"
                + "\"textOutline\":null,\"textShadow\":false,\"gradientColor\":null,\"gradientDirection\":\"vertical\",\"textGradient\":null},\"layer\":0,\"actionArea\":null}]}";
        var old = ScreenCodec.decode(v8);
        assertTrue(old.legacyText(), "read from schema 8: its texts are drawn as written");
        assertEquals("<RED>OK</RED> <red>x", old.elements().get(0).text(), "the text itself is not touched (no escape can push it past 512 characters)");
        String saved = ScreenCodec.encode(old);
        assertFalse(saved.contains("\"version\":9"), "an old page saved again stays an old page");
        assertEquals(old, ScreenCodec.decode(saved));
        var grown = ScreenCodec.decode(ScreenCodec.encode(old.withLogic(dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ActionParser.screen(ScreenCodec.parse("{\"command\":\"old\"}")))));
        assertFalse(grown.legacyText(), "it gained logic: written as schema 9");
        assertEquals("<RED>OK</RED> <red>x", RichText.plain(grown.elements().get(0).text()), "with its tag escaped, so it still draws as text");
        assertEquals(List.of(), RichText.colors(grown.elements().get(0).text()));
        var coloured = old.elements().get(0).withText("Saldo: <gold>100</gold>");
        assertTrue(ScreenCodec.encode(new Screen("c", "C", List.of(coloured), RenderQuality.low())).contains("\"version\":9"));
        var many = new StringBuilder();
        for (String c : List.of("black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray", "blue")) many.append('<').append(c).append(">x");
        var nine = old.elements().get(0).withText(many.toString());
        assertThrows(IllegalArgumentException.class, () -> new Screen("c", "C", List.of(nine), RenderQuality.low()), "at most 8 word colours in schema 9");
        assertDoesNotThrow(() -> old.withElements(List.of(nine)), "an old page's tags are only text: no limit");
    }
}
