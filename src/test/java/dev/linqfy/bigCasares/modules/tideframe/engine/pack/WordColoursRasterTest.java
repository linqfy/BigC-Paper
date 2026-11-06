package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.RenderQuality;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Idea 28 in the baked canvas: the tags take no room and each word is drawn in its colour, its shadow too. */
class WordColoursRasterTest {
    private static BufferedImage render(String text, Screen.Style style) {
        var label = new Screen.Element("t", text, 0, 0, 1.5, .4, "label", "", "rectangle", false, null, false, null).withStyle(style);
        return new HudRaster.Batch(new Screen("w", "W", List.of(label), RenderQuality.low())).render(null);
    }
    private static final Screen.Style PLAIN = Screen.Style.defaults().withTextCase("original");

    @Test void tagsTakeNoRoomAndEachWordHasItsColour() {
        var coloured = render("Ab <red>Cd</red> e", PLAIN);
        var plain = render("Ab Cd e", PLAIN);
        int white = 0, red = 0, lastWhiteBeforeRed = -1, firstRed = Integer.MAX_VALUE, lastRed = -1, firstWhiteAfter = Integer.MAX_VALUE;
        for (int y = 0; y < plain.getHeight(); y++) for (int x = 0; x < plain.getWidth(); x++) {
            assertEquals(plain.getRGB(x, y) >>> 24, coloured.getRGB(x, y) >>> 24, "same ink at " + x + "," + y);
            int rgb = coloured.getRGB(x, y);
            if (rgb == 0xffffffff) white++;
            if (rgb == 0xffff5555) { red++; firstRed = Math.min(firstRed, x); lastRed = Math.max(lastRed, x); }
        }
        for (int y = 0; y < plain.getHeight(); y++) for (int x = 0; x < plain.getWidth(); x++) {
            if (coloured.getRGB(x, y) != 0xffffffff) continue;
            if (x < firstRed) lastWhiteBeforeRed = Math.max(lastWhiteBeforeRed, x);
            if (x > lastRed) firstWhiteAfter = Math.min(firstWhiteAfter, x);
        }
        assertTrue(white > 0 && red > 0);
        assertTrue(lastWhiteBeforeRed < firstRed && firstWhiteAfter > lastRed && firstWhiteAfter < Integer.MAX_VALUE, "white, then red, then white again");
    }
    @Test void aSchemaEightPageDrawsItsTagsAsWritten() {
        var label = new Screen.Element("t", "Ab <red>Cd</red> e", 0, 0, 1.5, .4, "label", "", "rectangle", false, null, false, null).withStyle(PLAIN);
        var legacy = new Screen("w", "W", List.of(label), RenderQuality.low(), Screen.Presentation.defaults(), dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ScreenLogic.EMPTY, true);
        var image = new HudRaster.Batch(legacy).render(null);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
            assertNotEquals(0xffff5555, image.getRGB(x, y), "no red: the tag is text on an old page");
        var literal = render("Ab \\<red>Cd\\</red> e", PLAIN);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
            assertEquals(literal.getRGB(x, y), image.getRGB(x, y), "the same pixels as the escaped text on a new page");
    }
    @Test void aColouredWordIsSolidOverAGradientAndCastsItsOwnShadow() {
        var style = PLAIN.withText(1, null, true).withTextGradient("#000000");
        var image = render("<gold>W</gold>", style);
        boolean gold = false, goldShadow = false;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int rgb = image.getRGB(x, y);
            gold |= rgb == 0xffffaa00;
            goldShadow |= rgb == 0xff3f2a00;
            if (rgb >>> 24 != 0) assertTrue(rgb == 0xffffaa00 || rgb == 0xff3f2a00, "only the word and its shadow: no gradient ink, at " + x + "," + y);
        }
        assertTrue(gold, "the word is gold from top to bottom (no gradient)");
        assertTrue(goldShadow, "its shadow is gold at a quarter brightness");
    }
}
