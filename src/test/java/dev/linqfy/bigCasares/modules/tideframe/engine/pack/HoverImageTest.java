package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.ImageAsset;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.RenderQuality;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ActionParser;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.UltimateUiImporter;
import dev.linqfy.bigCasares.modules.tideframe.engine.testing.PngFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** A button that shows another image while the cursor is on it (Ultimate UI's hover image swap). */
class HoverImageTest {
    private static ImageAsset solid(int argb) throws Exception { return ImageAsset.fromPng(PngFixtures.rgba(4, 4, argb)); }
    /** A borderless, backgroundless button: before hover images it needed no hover look at all. */
    private static Screen.Element icon(ImageAsset normal, ImageAsset hovered) {
        return new Screen.Element("icon", "", 0, 0, .5, .5, "button", "", "rectangle", false, null, false, normal, Screen.Style.defaults(), 0, null)
                .withHoverImage(hovered);
    }
    private static Screen screen(Screen.Element... elements) { return new Screen("hover", "HOVER", List.of(elements), RenderQuality.low()); }

    @Test void theHoverImageIsDrawnOnlyWhileTheCursorIsOnTheButton() throws Exception {
        var screen = screen(icon(solid(0xffff0000), solid(0xff0000ff)));
        var batch = new HudRaster.Batch(screen);
        assertEquals(0xffff0000, batch.render(null).getRGB(256, 144));
        assertEquals(0xff0000ff, batch.render("icon").getRGB(256, 144));
        var plain = screen(icon(solid(0xffff0000), null));
        assertEquals(0xffff0000, new HudRaster.Batch(plain).render("icon").getRGB(256, 144), "without a hover image the image stays");
    }

    @Test void theHoverLookIsBakedEvenForABareButtonAndInRuntimeLayers() throws Exception {
        var swap = screen(icon(solid(0xffff0000), solid(0xff0000ff)));
        var assets = HudAssets.create(List.of(swap), List.of(4, 8));
        for (int quarters : List.of(4, 8))
            assertNotEquals(assets.frame(swap, null, 8, quarters), assets.frame(swap, "icon", 8, quarters), "a hover frame of its own at " + quarters);
        var bare = screen(icon(solid(0xffff0000), null));
        var plainAssets = HudAssets.create(List.of(bare), List.of(4));
        assertEquals(plainAssets.frame(bare, null, 8, 4), plainAssets.frame(bare, "icon", 8, 4), "a bare button without hover image still shares its frame");
        var hidden = screen(icon(solid(0xffff0000), solid(0xff0000ff)).withLogic(ActionParser.element(ScreenCodec.parse("{\"hidden\":true}"))));
        var layered = HudAssets.create(List.of(hidden), List.of(4, 8));
        for (int quarters : List.of(4, 8)) assertNotNull(layered.layer(hidden, "icon@hover", quarters), "the runtime layer bakes its hover look");
    }

    @Test void hoverImagesTravelInSchemaNineAndOnlyOnButtons() throws Exception {
        var screen = screen(icon(solid(0xffff0000), solid(0xff0000ff)));
        var json = ScreenCodec.encode(screen);
        assertTrue(json.startsWith("{\"version\":9") && json.contains("\"hoverImage\":\""), json.substring(0, 40));
        assertEquals(screen, ScreenCodec.decode(json));
        assertThrows(IllegalArgumentException.class, () -> ScreenCodec.decode(json.replace("\"version\":9", "\"version\":8")));
        assertThrows(IllegalArgumentException.class, () -> new Screen.Element("tag", "", 0, 0, .5, .5, "label", "", "rectangle", false, null, false, null,
                Screen.Style.defaults(), 0, null).withHoverImage(solid(0xff00ff00)), "labels are never hovered");
        var moved = screen.move("icon", .2, .1).element("icon").orElseThrow();
        assertNotNull(moved.hoverImage(), "moving keeps the hover image");
        assertNotNull(moved.withText("x").withStyle(Screen.Style.defaults()).withLogic(dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ElementLogic.EMPTY).hoverImage());
    }

    @Test void ultimateUiHoverImageSwapsAreImported() throws Exception {
        byte[] red = PngFixtures.rgba(4, 4, 0xffff0000), blue = PngFixtures.rgba(4, 4, 0xff0000ff);
        var sources = new UltimateUiImporter.Sources() {
            public byte[] image(String name) { return switch (name) { case "icon3" -> red; case "icon3_glow" -> blue; default -> null; }; }
            public String component(String name) { return null; }
            public String effect(String name) { return null; }
        };
        var result = UltimateUiImporter.convert("""
            name: swap
            blocks:
              - {type: image, id: icon, image: icon3, position: {x: 760, y: 400}, size: {width: 400, height: 200}, hover: {image: icon3_glow}}
            """, "swap.yml", sources);
        var icon = result.screen().element("icon").orElseThrow();
        assertEquals("button", icon.kind(), "a hover target");
        assertEquals(ImageAsset.fromPng(blue), icon.hoverImage());
        assertTrue(result.warnings().stream().noneMatch(w -> w.contains("hover image")), result.warnings().toString());
    }
}
