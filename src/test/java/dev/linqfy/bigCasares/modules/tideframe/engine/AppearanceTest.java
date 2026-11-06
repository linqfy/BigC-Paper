package dev.linqfy.bigCasares.modules.tideframe.engine;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.*;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import dev.linqfy.bigCasares.modules.tideframe.engine.pack.HudRaster;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class AppearanceTest {
    private static String json(String text, String style) {
        return "{\"version\":5,\"id\":\"appearance\",\"title\":\"APPEARANCE\",\"quality\":{\"preset\":\"low\",\"width\":512,\"height\":288},\"elements\":["
                + "{\"id\":\"box\",\"text\":\"" + text + "\",\"x\":0,\"y\":0,\"width\":1,\"height\":0.5,\"kind\":\"button\",\"action\":\"close\","
                + "\"shape\":\"rounded\",\"border\":true,\"messageCondition\":null,\"background\":true,\"image\":null,\"style\":" + style + ",\"layer\":0,\"actionArea\":null}]}";
    }
    private static String style(String changes) {
        String base = "{\"opacity\":1,\"fillColor\":\"#123456\",\"borderColor\":\"#abcdef\",\"textColor\":\"#fedcba\",\"shadow\":false,"
                + "\"rotation\":0,\"radius\":0.08,\"outlineWidth\":1,\"hoverColor\":null,\"hoverEffect\":\"none\",\"textAlign\":\"center\",\"wrapWidth\":0}";
        if (changes.isEmpty()) return base;
        for (String change : changes.split(";")) { var pair = change.split("=", 2); base = base.replaceAll("\\\"" + pair[0] + "\\\":(?:\\\"[^\\\"]*\\\"|null|[-0-9.]+)", "\"" + pair[0] + "\":" + pair[1]); }
        return base;
    }
    private static BufferedImage image(String text, String changes, boolean hover) {
        var screen = ScreenCodec.decode(json(text, style(changes)));
        return HudRaster.render(screen, hover ? "box" : null, 8);
    }

    @Test void schemaFiveRoundtripsAppearanceAndMultilineWithoutWeakeningOldSchemas() {
        var screen = ScreenCodec.decode(json("ONE\\nTWO", style("rotation=25;radius=0.2;outlineWidth=3;hoverColor=\"#00ff00\";hoverEffect=\"scale\";textAlign=\"left\";wrapWidth=60")));
        String encoded = ScreenCodec.encode(screen);
        assertTrue(encoded.startsWith("{\"version\":5")); assertTrue(encoded.contains("ONE\\nTWO"));
        assertEquals(screen, ScreenCodec.decode(encoded));
        assertEquals(512, ScreenCodec.decode(json("X".repeat(512), style(""))).elements().getFirst().text().length());
        assertThrows(IllegalArgumentException.class, () -> ScreenCodec.decode(json("X".repeat(513), style(""))));
        String versionFour = ScreenCodec.encode(ScreenCodec.decode(json("", style(""))));
        assertTrue(versionFour.startsWith("{\"version\":4"));
        for (String legacyText : new String[]{"LINE\\nTWO", "X".repeat(65)}) {
            var rejected = assertThrows(IllegalArgumentException.class, () -> ScreenCodec.decode(versionFour.replace("\"text\":\"\"", "\"text\":\"" + legacyText + "\"")));
            assertTrue(rejected.getMessage().contains("Legacy text"));
        }
        assertTrue(ScreenCodec.encode(ScreenCodec.decode(json("LINE\\nTWO", style("")))).startsWith("{\"version\":5"));
        assertThrows(IllegalArgumentException.class, () -> ScreenCodec.decode(json("TAB\\tNO", style(""))));
    }
    @Test void appearanceRangesAndExactIntegerTokensAreGuarded() {
        for (String change : new String[]{"rotation=181", "radius=0.51", "outlineWidth=1.00000000000000000001", "wrapWidth=256.000000000000000001", "hoverColor=\"bad\"", "hoverEffect=\"script\"", "textAlign=\"justify\""})
            assertThrows(IllegalArgumentException.class, () -> ScreenCodec.decode(json("", style(change))), change);
        var defaultStyle = ScreenCodec.decode(json("", style(""))).elements().getFirst().style();
        assertEquals(new Screen.Style(1, "#123456", "#abcdef", "#fedcba", false), defaultStyle);
        assertFalse(defaultStyle.isExtended());
    }
    @Test void rotationChangesVisualPixelsButNeverActionBounds() {
        var screen = ScreenCodec.decode(json("", style("rotation=90;radius=0;outlineWidth=0")));
        var rendered = HudRaster.render(screen, null, 8);
        assertEquals(0xff123456, rendered.getRGB(256, 240));
        assertEquals(0, rendered.getRGB(150, 144));
        assertEquals(new HitTester.Rect("box", 0, 0, 1, .5), screen.actionTargets().getFirst());
    }
    @Test void radiusOutlineAndHoverStylesRenderActualPixels() {
        var square = image("", "radius=0;outlineWidth=0", false);
        assertEquals(0xff123456, square.getRGB(128, 108));
        var rounded = image("", "radius=0.5;outlineWidth=0", false);
        assertEquals(0, rounded.getRGB(128, 108));
        var outlined = image("", "radius=0;outlineWidth=3", false);
        assertEquals(0xffabcdef, outlined.getRGB(133, 140));
        assertEquals(0xff123456, outlined.getRGB(134, 140));
        var hovered = image("", "radius=0;hoverColor=\"#00ff00\";hoverEffect=\"translate\"", true);
        assertEquals(0xff00ff00, hovered.getRGB(200, 104));
        assertEquals(0, hovered.getRGB(200, 178));
        var scaled = image("", "radius=0;outlineWidth=0;hoverEffect=\"scale\"", true);
        assertNotEquals(0, scaled.getRGB(125, 144));
    }
    @Test void multilineWrappingAndAlignmentUseOwnFiveBySevenStrokes() {
        var left = image("A\\nB", "radius=0;outlineWidth=0;textAlign=\"left\"", false);
        assertEquals(0xfffedcba, left.getRGB(132, 129));
        assertEquals(0xfffedcba, left.getRGB(128, 145));
        var right = image("A", "radius=0;outlineWidth=0;textAlign=\"right\"", false);
        assertEquals(0xfffedcba, right.getRGB(378, 136));
        var wrapped = image("AB", "radius=0;outlineWidth=0;wrapWidth=5", false);
        assertEquals(0xfffedcba, wrapped.getRGB(256, 129));
        assertEquals(0xfffedcba, wrapped.getRGB(252, 145));
    }
}
