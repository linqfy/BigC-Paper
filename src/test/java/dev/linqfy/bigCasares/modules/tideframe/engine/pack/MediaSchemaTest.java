package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.ImageAsset;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.RenderQuality;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ActionParser;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ElementLogic;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.LogicJson;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import dev.linqfy.bigCasares.modules.tideframe.engine.testing.PngFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Schema 9 blurs in screen JSON, and GIF frame timing as the pack plays it. */
class MediaSchemaTest {
    private static Screen screen(Screen.Style style, RenderQuality quality) {
        return new Screen("glass", "GLASS", List.of(new Screen.Element("panel", "OK", 0, 0, .8, .4, "label", "", "rounded", true, null, true, null, style, 0, null)), quality);
    }

    @Test void blursRoundTripInSchemaNineOnly() {
        var style = Screen.Style.defaults().withBlur(3, 8, 5);
        var json = ScreenCodec.encode(screen(style, RenderQuality.low()));
        assertTrue(json.startsWith("{\"version\":9"), json);
        assertTrue(json.contains("\"blur\":3") && json.contains("\"backdropBlur\":8") && json.contains("\"shadowBlur\":5"));
        assertEquals(style, ScreenCodec.decode(json).elements().get(0).style());
        assertThrows(IllegalArgumentException.class, () -> ScreenCodec.decode(json.replace("\"version\":9", "\"version\":8")), "schema 8 knows no blurs");
        assertThrows(IllegalArgumentException.class, () -> ScreenCodec.encode(screen(style, RenderQuality.legacy())), "legacy screens cannot carry blurs");
        assertThrows(IllegalArgumentException.class, () -> ScreenCodec.decode(json.replace("\"blur\":3", "\"blur\":17")));
        assertThrows(IllegalArgumentException.class, () -> Screen.Style.defaults().withBlur(-1, 0, 0));
    }

    @Test void screensBuiltInCodeTakeBlursAsProperties() {
        var screen = dev.linqfy.bigCasares.modules.tideframe.engine.ScreenBuilder.create("coded").title("Coded")
                .block("glass", 660, 240, 600, 600, "#101418").property("glass", "style.backdropBlur", 6).property("glass", "style.shadow", true)
                .property("glass", "style.shadowBlur", 4).build();
        var style = screen.element("glass").orElseThrow().style();
        assertEquals(6, style.backdropBlur()); assertEquals(4, style.shadowBlur()); assertEquals(0, style.blur());
        assertThrows(IllegalArgumentException.class, () -> dev.linqfy.bigCasares.modules.tideframe.engine.ScreenBuilder.create("bad").title("Bad")
                .block("glass", 0, 0, 100, 100, "#101418").property("glass", "style.blur", 40).build());
    }

    @Test void screensWithoutBlursEncodeExactlyAsBefore() {
        var json = ScreenCodec.encode(screen(Screen.Style.defaults().withText(2, null, false), RenderQuality.low()));
        assertFalse(json.contains("lur\""), json);
        assertTrue(json.startsWith("{\"version\":7"), "no blur keeps the lowest schema: " + json);
        assertEquals(0, ScreenCodec.decode(json).elements().get(0).style().blur());
        var only = Screen.Style.defaults().withBlur(0, 0, 2);
        assertTrue(only.hasBlur()); assertFalse(Screen.Style.defaults().hasBlur());
        assertEquals(only, only.withTextCase("upper").withHoverEffect("none"), "other style edits keep the blurs");
        assertEquals(2, only.withGradient("#000000", "radial").withTextGradient("#ffffff").withText(3, "#000000", true).shadowBlur());
    }

    @Test void intervalMsIsOptionalAndLeftOutWhenAutomatic() {
        var auto = ActionParser.element(ScreenCodec.parse("{\"frames\":{\"folder\":\"fire\"}}"));
        assertEquals(ElementLogic.Frames.AUTO, auto.frames().intervalMs());
        assertFalse(LogicJson.element(auto).contains("interval-ms"), LogicJson.element(auto));
        var fixed = ActionParser.element(ScreenCodec.parse("{\"frames\":{\"folder\":\"fire\",\"interval-ms\":150}}"));
        assertTrue(LogicJson.element(fixed).contains("\"interval-ms\":150"));
        assertThrows(IllegalArgumentException.class, () -> ActionParser.element(ScreenCodec.parse("{\"frames\":{\"folder\":\"fire\",\"interval-ms\":20}}")));
    }

    @Test void theSampledGifPaceWinsAndPngFoldersKeepTheirOwn() throws Exception {
        var frame = ImageAsset.fromPng(PngFixtures.rgba(4, 4, 0xffff0000));
        var other = ImageAsset.fromPng(PngFixtures.rgba(4, 4, 0xff0000ff));
        var elements = List.of(
                animated("gif", -.6, "{\"frames\":{\"folder\":\"fire\"}}"),
                animated("gifset", -.2, "{\"frames\":{\"folder\":\"smoke\",\"interval-ms\":50}}"),
                animated("png", .2, "{\"frames\":{\"folder\":\"coins\",\"interval-ms\":150}}"),
                animated("pngauto", .6, "{\"frames\":{\"folder\":\"stars\"}}"));
        var screen = new Screen("media", "MEDIA", elements, RenderQuality.low());
        var images = Map.of("fire", List.of(frame, other), "smoke", List.of(frame, other), "coins", List.of(frame, other), "stars", List.of(frame, other));
        var assets = HudAssets.create(List.of(screen), List.of(4), HudAssets.DEFAULT_CURSOR_SCALE, images, Map.of("fire", 100, "smoke", 200));
        assertEquals(100, assets.frameInterval(screen.element("gif").orElseThrow()), "a GIF at its own pace");
        assertEquals(200, assets.frameInterval(screen.element("gifset").orElseThrow()), "a GIF sampled more sparsely plays at the sampled pace");
        assertEquals(150, assets.frameInterval(screen.element("png").orElseThrow()));
        assertEquals(50, assets.frameInterval(screen.element("pngauto").orElseThrow()), "PNG folders still default to one frame per tick");
        assertEquals(2, assets.frameCount(screen, "gif"));
        assertThrows(IllegalArgumentException.class, () -> HudAssets.create(List.of(screen), List.of(4), HudAssets.DEFAULT_CURSOR_SCALE, images, Map.of("fire", 10)));
    }
    private static Screen.Element animated(String id, double x, String logic) {
        return new Screen.Element(id, "", x, 0, .3, .3, "decoration", "", "rectangle", false, null, false, null, Screen.Style.defaults(), 0, null)
                .withLogic(ActionParser.element(ScreenCodec.parse(logic)));
    }
}
