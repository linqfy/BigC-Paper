package dev.linqfy.bigCasares.modules.tideframe.engine;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.RenderQuality;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.RichText;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.pack.HudAssets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Idea 28 in live text: each run keeps its colour, styled text gets a font baked in each word colour. */
class WordColoursLiveTest {
    private static Screen page(Screen.Style style) {
        var label = new Screen.Element("saldo", "Saldo: <gold>{balance}</gold>", 0, 0, 1.5, .2, "label", "", "rectangle", false, null, false, null).withStyle(style);
        return new Screen("w", "W", List.of(label), RenderQuality.low());
    }
    /** The glyph runs of a live layer: {font, colour, text}. */
    private static void glyphs(Component component, List<String[]> out) {
        if (component instanceof TextComponent text && component.font() != null && !component.font().value().startsWith("hud") && !text.content().isEmpty()
                && component.font().value().startsWith("live"))
            out.add(new String[]{component.font().asString(), component.color() == null ? null : component.color().asHexString().toLowerCase(java.util.Locale.ROOT), text.content()});
        for (var child : component.children()) glyphs(child, out);
    }
    private static List<String[]> runs(Screen screen) {
        var assets = HudAssets.create(List.of(screen));
        String value = RichText.compose(List.of(new RichText.Run("Saldo: ", null), new RichText.Run("1250", "#ffaa00")));
        var out = new ArrayList<String[]>();
        glyphs(LiveLayer.build(assets, screen, Map.of("saldo", value), 4), out);
        return out;
    }

    @Test void plainLiveTextTintsEachRunWithItsColour() {
        var runs = runs(page(Screen.Style.defaults().withTextCase("original")));
        assertEquals(2, runs.size());
        assertEquals(runs.get(0)[0], runs.get(1)[0], "one font: the component colour tints it");
        assertEquals("#ffffff", runs.get(0)[1]);
        assertEquals("#ffaa00", runs.get(1)[1]);
    }
    @Test void styledLiveTextUsesAFontBakedInTheWordColour() {
        var runs = runs(page(Screen.Style.defaults().withTextCase("original").withText(1, "#000000", false)));
        assertEquals(2, runs.size());
        assertNotEquals(runs.get(0)[0], runs.get(1)[0], "the gold word has its own font");
        assertEquals("#ffffff", runs.get(0)[1], "baked colours are drawn white");
        assertEquals("#ffffff", runs.get(1)[1]);
    }
    @Test void pastTheFontBudgetWordsKeepTheElementColourAndThePackStillBuilds() {
        var style = Screen.Style.defaults().withTextCase("original").withText(1, "#000000", false);
        var elements = new ArrayList<Screen.Element>();
        // 62 styled live rows and the coloured text take 63 of the 64 live fonts: of its three colours only one fits.
        for (int i = 0; i < 62; i++) elements.add(new Screen.Element("row" + i, "{v" + i + "}", -.5, .95 - i * .03, .4, .03, "label", "", "rectangle", false, null, false, null).withStyle(style));
        elements.add(new Screen.Element("rich", "<gold>{a}</gold> <red>{b}</red> <blue>{c}</blue>", .5, 0, .8, .1, "label", "", "rectangle", false, null, false, null).withStyle(style));
        var screen = new Screen("full", "FULL", List.copyOf(elements), RenderQuality.low());
        var assets = assertDoesNotThrow(() -> HudAssets.create(List.of(screen)), "a full budget no longer fails the pack");
        String value = RichText.compose(List.of(new RichText.Run("1", "#ffaa00"), new RichText.Run(" ", null), new RichText.Run("2", "#ff5555"), new RichText.Run(" ", null), new RichText.Run("3", "#5555ff")));
        var out = new ArrayList<String[]>();
        glyphs(LiveLayer.build(assets, screen, Map.of("rich", value), 4), out);
        assertEquals(5, out.size(), "every word is still drawn");
        String own = out.get(1)[0];
        assertNotEquals(own, out.get(0)[0], "gold got the last font");
        assertEquals(own, out.get(2)[0], "red falls back to the element's own font");
        assertEquals(own, out.get(4)[0], "and so does blue");
    }
    @Test void aValueCannotColourItself() {
        var screen = page(Screen.Style.defaults().withTextCase("original"));
        var assets = HudAssets.create(List.of(screen));
        String value = RichText.compose(List.of(new RichText.Run("<red>x", null)));
        var out = new ArrayList<String[]>();
        glyphs(LiveLayer.build(assets, screen, Map.of("saldo", value), 4), out);
        assertEquals(1, out.size());
        assertEquals("#ffffff", out.get(0)[1]);
        assertEquals(Key.key("tideframe", "live_0").asString(), out.get(0)[0]);
    }
}
