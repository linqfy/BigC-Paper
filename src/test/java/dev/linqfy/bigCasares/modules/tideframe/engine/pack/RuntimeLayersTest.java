package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.ImageAsset;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.RenderQuality;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ActionParser;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import dev.linqfy.bigCasares.modules.tideframe.engine.testing.PngFixtures;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeLayersTest {
    private static byte[] solid(int width, int height, int argb) {
        var pixels = new int[width * height]; java.util.Arrays.fill(pixels, argb);
        try { return PngFixtures.rgba(width, height, pixels); } catch (java.io.IOException impossible) { throw new IllegalStateException(impossible); }
    }
    private static Screen.Element element(String id, String kind, double y, String logic) {
        var e = new Screen.Element(id, id.toUpperCase(java.util.Locale.ROOT), 0, y, .5, .14, kind, "");
        return logic == null ? e : e.withLogic(ActionParser.element(ScreenCodec.parse(logic)));
    }
    private static Screen screen() {
        return new Screen("logic", "LOGIC", List.of(
                element("static", "button", .7, null),
                element("panel", "label", .3, "{\"hidden\":true}"),
                element("toggle", "button", 0, "{\"actions\":[{\"type\":\"toggle\",\"elements\":[\"panel\"]}]}"),
                element("row", "button", -.3, "{\"list\":{\"source\":\"items\",\"rows\":2,\"spacing\":24}}"),
                element("spin", "decoration", -.8, "{\"frames\":{\"folder\":\"spin\",\"interval-ms\":100}}")), RenderQuality.low());
    }

    @Test void runtimeElementsBakeAsLayersAndLeaveTheCanvas() {
        var screen = screen();
        var frames = Map.of("spin", List.of(ImageAsset.fromPng(solid(8, 8, 0xffff0000)), ImageAsset.fromPng(solid(8, 8, 0xff00ff00))));
        var assets = HudAssets.create(List.of(screen), List.of(4, 8), HudAssets.DEFAULT_CURSOR_SCALE, frames);
        assertEquals(Set.of("panel", "row", "spin"), assets.runtimeIds(screen));
        for (int quarters : List.of(4, 8)) {
            assertNotNull(assets.layer(screen, "panel", quarters));
            assertNotNull(assets.layer(screen, "row#0", quarters)); assertNotNull(assets.layer(screen, "row#1", quarters));
            assertNotNull(assets.layer(screen, "row#1@hover", quarters));
            assertNotNull(assets.layer(screen, "spin@0", quarters)); assertNotNull(assets.layer(screen, "spin@1", quarters));
            assertNull(assets.layer(screen, "static", quarters), "static elements stay in the canvas");
            assertNotEquals(assets.layer(screen, "row#0", quarters), assets.layer(screen, "row#1", quarters), "rows sit at different heights");
            assertEquals(assets.frame(screen, null, 8, quarters), assets.frame(screen, "row#1", 8, quarters), "a row's hover is drawn by its layer");
            assertNotEquals(assets.frame(screen, null, 8, quarters), assets.frame(screen, "static", 8, quarters));
        }
        assertEquals(2, assets.frameCount(screen, "spin"));
        assertEquals(0, assets.frameCount(screen, "panel"));
        var plain = new Screen("plain", "PLAIN", List.of(element("static", "button", .7, null)), RenderQuality.low());
        var both = HudAssets.create(List.of(screen, plain), List.of(4), HudAssets.DEFAULT_CURSOR_SCALE, frames);
        assertEquals(Set.of(), both.runtimeIds(plain));
        var alone = HudAssets.create(List.of(plain), List.of(4));
        var canvases = alone.entries().keySet().stream().filter(path -> path.contains("/font/hud/canvas_")).toList();
        assertFalse(canvases.isEmpty());
        assertTrue(both.entries().keySet().containsAll(canvases), "screens without logic bake exactly the same canvas PNGs as before");
    }
    @Test void missingFramesFallBackToTheElementItself() {
        var screen = screen();
        var assets = HudAssets.create(List.of(screen), List.of(4), HudAssets.DEFAULT_CURSOR_SCALE, Map.of());
        assertEquals(0, assets.frameCount(screen, "spin"));
        assertNotNull(assets.layer(screen, "spin", 4));
    }
    @Test void tooManyFramesAreRefused() {
        var screen = screen();
        var many = new java.util.ArrayList<ImageAsset>();
        var png = ImageAsset.fromPng(solid(4, 4, 0xff0000ff));
        for (int i = 0; i <= HudAssets.MAX_FRAMES; i++) many.add(png);
        assertThrows(IllegalArgumentException.class, () -> HudAssets.create(List.of(screen), List.of(4), HudAssets.DEFAULT_CURSOR_SCALE, Map.of("spin", many)));
    }
}
