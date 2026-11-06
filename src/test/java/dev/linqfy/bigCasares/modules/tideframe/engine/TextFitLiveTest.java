package dev.linqfy.bigCasares.modules.tideframe.engine;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.RenderQuality;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.TextFit;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ScreenLogic;
import dev.linqfy.bigCasares.modules.tideframe.engine.pack.HudAssets;
import dev.linqfy.bigCasares.modules.tideframe.engine.pack.LiveTextTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Text wider than its box shrinks (TextFit): the live text the plugin sends lands on exactly the pixels the baked text
 * of the same size does, in the 26.2 title simulator, and returns the pen where it started (zero net advance).
 */
class TextFitLiveTest {
    private static final int ORANGE = 0xff8800;
    /** A 90-px box: 15 characters fit as authored, 20 at three quarters, 30 at half. */
    private static Screen screen(String text, String align, boolean legacy) {
        var label = new Screen.Element("tag", text, .3, .5, .7, .12, "label", "", "rectangle", false, null, false, null,
                new Screen.Style(1, "#000000", "#000000", "#ff8800", false, 0, 0, 1, null, "none", align, 0), 5, null);
        return new Screen("live", "LIVE", List.of(label), RenderQuality.custom(1024, 576), new Screen.Presentation("menu", 1, "cross"), ScreenLogic.EMPTY, legacy);
    }
    /** Draws a title component the way the client walks it: its own text, then its children, inheriting font and colour. */
    private static void draw(LiveTextTest.Sim sim, Component component, Key font, int tint) {
        Key own = component.font() != null ? component.font() : font;
        int colour = component.color() != null ? component.color().value() : tint;
        if (component instanceof TextComponent text && !text.content().isEmpty()) sim.draw(own.asString(), text.content(), colour);
        for (var child : component.children()) draw(sim, child, own, colour);
    }
    /** The characters a player reads: spaces and advance corrections (private-use characters) left out. */
    private static String text(Component component) {
        var out = new StringBuilder(component instanceof TextComponent t ? t.content().replaceAll("[\\ue000-\\uf8ff]", "") : "");
        for (var child : component.children()) out.append(text(child));
        return out.toString();
    }
    private static int orangePixels(LiveTextTest.Sim sim, List<int[]> into) {
        int count = 0;
        for (int y = 0; y < 576; y++) for (int x = 0; x < 1024; x++) {
            int argb = sim.canvas.getRGB(x, y);
            if ((argb & 0xffffff) == ORANGE && (argb >>> 24) == 255) { count++; if (into != null) into.add(new int[]{x, y}); }
        }
        return count;
    }

    @Test void shrunkLiveTextMatchesTheShrunkBakedTextPixelForPixel() throws Exception {
        // 17 characters: three quarters (an odd count there needs the half step back); 23: half; 33: half, cut with "…".
        for (String value : new String[]{"LOCOBESTIAPARDA42", "LOCOBESTIAPARDA42ABCDEF", "LOCOBESTIAPARDA42ABCDEFGHIJKLMNOP"})
            for (String align : new String[]{"left", "center", "right"}) {
                var baked = screen(value, align, false);
                var live = screen("{coins}", align, false);
                var bakedAssets = HudAssets.create(List.of(baked));
                var liveAssets = HudAssets.create(List.of(live));
                var expected = new LiveTextTest.Sim(bakedAssets.entries());
                expected.draw("tideframe:hud", bakedAssets.frame(baked, null, 8), 0);
                var actual = new LiveTextTest.Sim(liveAssets.entries());
                actual.draw("tideframe:hud", liveAssets.frame(live, null, 8), 0);
                double before = actual.x;
                var layer = LiveLayer.build(liveAssets, live, Map.of("tag", value), 4);
                draw(actual, layer, null, 0);
                assertEquals(before, actual.x, 1e-9, value + " " + align + ": the live layer returns the pen where it started");
                var want = new ArrayList<int[]>(); var got = new ArrayList<int[]>();
                int wanted = orangePixels(expected, want), drawn = orangePixels(actual, got);
                assertTrue(wanted > 150, "baked text pixels: " + wanted);
                assertEquals(wanted, drawn, value + " " + align + ": same number of text pixels");
                for (int i = 0; i < want.size(); i++)
                    assertArrayEquals(want.get(i), got.get(i), value + " " + align + ": pixel " + i);
                if (value.length() > 30) assertTrue(text(layer).contains(String.valueOf(TextFit.ELLIPSIS)), "cut with an ellipsis: " + text(layer));
            }
    }

    @Test void textThatFitsIsDrawnExactlyAsBeforeAndOldPagesStillCut() throws Exception {
        var current = screen("{player}", "center", false);
        var assets = HudAssets.create(List.of(current));
        var element = current.elements().getFirst();
        // Fits as authored: the same font as always, no smaller one.
        String fits = text(LiveLayer.build(assets, current, Map.of("tag", "Steve"), 4));
        assertTrue(fits.contains("STEVE"));
        assertEquals(assets.liveFont(current, element, 4), firstLiveFont(LiveLayer.build(assets, current, Map.of("tag", "Steve"), 4)));
        // A 16-letter name in a 15-letter box: three quarters, not cut.
        var longName = LiveLayer.build(assets, current, Map.of("tag", "locobestiaparda1"), 4);
        assertEquals(assets.liveFont(current, element, 4, null, TextFit.THREE_QUARTERS), firstLiveFont(longName));
        assertTrue(text(longName).contains("LOCOBESTIAPARDA1"));
        // {player} never needs half size (16 letters at most): the pack has no font for it, so past that it is cut at 3/4.
        assertNull(assets.liveFont(current, element, 4, null, TextFit.HALF));
        // A schema 1-8 page keeps cutting at the size it was authored, without an ellipsis, as before.
        var old = screen("{player}", "center", true);
        var oldAssets = HudAssets.create(List.of(old));
        var cut = LiveLayer.build(oldAssets, old, Map.of("tag", "locobestiaparda1"), 4);
        assertEquals(oldAssets.liveFont(old, old.elements().getFirst(), 4), firstLiveFont(cut));
        assertTrue(text(cut).contains("LOCOBESTIAPARDA") && !text(cut).contains("LOCOBESTIAPARDA1"), text(cut));
        assertFalse(text(cut).contains(String.valueOf(TextFit.ELLIPSIS)));
        assertNull(oldAssets.liveFont(old, old.elements().getFirst(), 4, null, TextFit.THREE_QUARTERS), "an old page gets no smaller fonts");
    }
    private static String firstLiveFont(Component component) {
        if (component.font() != null && component.font().asString().contains("live_")) return component.font().asString();
        for (var child : component.children()) { String found = firstLiveFont(child); if (found != null) return found; }
        return null;
    }
}
