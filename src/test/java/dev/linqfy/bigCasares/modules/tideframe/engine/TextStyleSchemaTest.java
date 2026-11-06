package dev.linqfy.bigCasares.modules.tideframe.engine;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.LiveText;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.RenderQuality;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Schema 7: big text, outline, shadow and hover glow, shared by the web editor and the plugin. */
class TextStyleSchemaTest {
    @Test void editorVersionSevenExportDecodesInThePlugin() throws Exception {
        String json;
        try (var in = getClass().getResourceAsStream("/fixtures/editor-v7.json")) { json = new String(in.readAllBytes(), StandardCharsets.UTF_8); }
        var screen = ScreenCodec.decode(json);
        var first = screen.elements().getFirst().style();
        assertEquals(2, first.textScale()); assertEquals("#102030", first.textOutline()); assertTrue(first.textShadow());
        assertTrue(screen.elements().stream().anyMatch(LiveText::isBar));
        assertTrue(screen.elements().stream().anyMatch(e -> e.style().hoverEffect().equals("glow")));
        assertEquals(screen, ScreenCodec.decode(ScreenCodec.encode(screen)));
        assertTrue(ScreenCodec.encode(screen).startsWith("{\"version\":7"));
    }

    @Test void plainScreensKeepTheirOlderSchemaVersion() {
        var label = new Screen.Element("a", "HELLO", 0, 0, .5, .2, "label", "", "rectangle", false, null, false, null,
                new Screen.Style(1, "#000000", "#000000", "#ffffff", false, 0, .08, 1, null, "none", "center", 0), 0, null);
        var screen = new Screen("plain", "PLAIN", List.of(label), RenderQuality.custom(1024, 576));
        assertTrue(ScreenCodec.encode(screen).startsWith("{\"version\":4"));
        // Keep the text inside the box: a too-narrow box now intentionally enables schema-9 shrink-to-fit.
        var styled = new Screen("big", "BIG", List.of(new Screen.Element("a", "HELLO", 0, 0, 1, .2, "label", "", "rectangle", false, null, false, null,
                label.style().withText(3, null, false), 0, null)), RenderQuality.custom(1024, 576));
        String json = ScreenCodec.encode(styled);
        assertTrue(json.startsWith("{\"version\":7"));
        assertEquals(styled, ScreenCodec.decode(json));
    }

    @Test void invalidTextStylesAreRejected() {
        var base = Screen.Style.defaults();
        assertThrows(IllegalArgumentException.class, () -> base.withText(0, null, false));
        assertThrows(IllegalArgumentException.class, () -> base.withText(5, null, false));
        assertThrows(IllegalArgumentException.class, () -> base.withText(2, "red", false));
        assertThrows(IllegalArgumentException.class, () -> base.withHoverEffect("spin"));
        assertEquals(1, base.withText(1, "#000000", false).outlineThickness());
        assertEquals(2, base.withText(4, "#000000", false).outlineThickness());
    }

    @Test void gradientsRoundTripAsSchemaEightAndPaintTheBackground() {
        var style = new Screen.Style(1, "#000000", "#ffffff", "#ffffff", false, 0, 0, 0, null, "none", "center", 0)
                .withGradient("#ffffff", "vertical");
        var panel = new Screen.Element("panel", "", 0, 0, 1, 1, "decoration", "", "rectangle", false, null, true, null, style, 0, null);
        var screen = new Screen("grad", "GRAD", List.of(panel), RenderQuality.custom(1024, 576));
        String json = ScreenCodec.encode(screen);
        assertTrue(json.startsWith("{\"version\":8"));
        assertEquals(screen, ScreenCodec.decode(json));
        var raster = dev.linqfy.bigCasares.modules.tideframe.engine.pack.HudRaster.render(screen, null, 8);
        int top = raster.getRGB(512, 576 / 4 + 1) & 0xff, middle = raster.getRGB(512, 288) & 0xff, bottom = raster.getRGB(512, 576 * 3 / 4 - 2) & 0xff;
        assertTrue(top < 10 && bottom > 245 && middle > 100 && middle < 155, top + " " + middle + " " + bottom);
        for (String direction : Screen.Style.GRADIENT_DIRECTIONS) assertEquals(direction, style.withGradient("#ffffff", direction).gradientDirection());
        assertThrows(IllegalArgumentException.class, () -> style.withGradient("#ffffff", "spiral"));
        assertThrows(IllegalArgumentException.class, () -> style.withTextGradient("gold"));
    }
}
