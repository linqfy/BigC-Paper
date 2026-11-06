package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.ImageAsset;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.RenderQuality;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Style "slice" (9-slice frames): corners keep their size, edges and centre stretch, nearest pixels. */
class SliceTest {
    private static final int CORNER_TL = 0xffff0000, CORNER_TR = 0xff00ff00, CORNER_BL = 0xff0000ff, CORNER_BR = 0xffffff00,
            EDGE = 0xff00ffff, MIDDLE = 0xffff00ff;
    /** 12 x 12: 3-pixel corners in four colours, cyan edges, magenta middle. */
    private static BufferedImage frame() {
        var image = new BufferedImage(12, 12, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 12; y++) for (int x = 0; x < 12; x++) {
            boolean left = x < 3, right = x >= 9, top = y < 3, bottom = y >= 9;
            image.setRGB(x, y, top && left ? CORNER_TL : top && right ? CORNER_TR : bottom && left ? CORNER_BL : bottom && right ? CORNER_BR
                    : left || right || top || bottom ? EDGE : MIDDLE);
        }
        return image;
    }
    private static BufferedImage canvas() { return new BufferedImage(64, 40, BufferedImage.TYPE_INT_ARGB); }

    @Test void cornersKeepTheirSizeAndTheRestStretches() {
        var canvas = canvas();
        HudRaster.sliced(canvas, frame(), 2, 4, 40, 20, 3, 1, 1);
        assertEquals(CORNER_TL, canvas.getRGB(2, 4)); assertEquals(CORNER_TL, canvas.getRGB(4, 6), "a 3-pixel corner stays 3 pixels");
        assertEquals(EDGE, canvas.getRGB(5, 4), "the top edge starts right after the corner");
        assertEquals(CORNER_TR, canvas.getRGB(41, 4)); assertEquals(CORNER_TR, canvas.getRGB(39, 6));
        assertEquals(EDGE, canvas.getRGB(38, 4));
        assertEquals(CORNER_BL, canvas.getRGB(2, 23)); assertEquals(CORNER_BR, canvas.getRGB(41, 23));
        assertEquals(MIDDLE, canvas.getRGB(22, 14), "the middle stretches both ways");
        assertEquals(EDGE, canvas.getRGB(2, 14), "the left edge stretches down");
        assertEquals(0, canvas.getRGB(1, 4) >>> 24, "nothing outside the box");
        assertEquals(0, canvas.getRGB(42, 24) >>> 24);
    }

    @Test void aDenserImageGivesThinnerCornersAndATightBoxShrinksThemEvenly() {
        var dense = canvas();
        // 2 image pixels per logical pixel, drawn at 2 raster pixels per logical pixel: corners 3 raster pixels.
        HudRaster.sliced(dense, frame(), 0, 0, 30, 20, 3, 2, 2);
        assertEquals(CORNER_TL, dense.getRGB(2, 2)); assertEquals(EDGE, dense.getRGB(3, 0));
        var tight = canvas();
        // A 4 x 4 box cannot hold two 3-pixel corners: each shrinks to 2 pixels, as CSS border-image does.
        HudRaster.sliced(tight, frame(), 0, 0, 4, 4, 3, 1, 1);
        assertEquals(CORNER_TL, tight.getRGB(1, 1)); assertEquals(CORNER_TR, tight.getRGB(2, 1));
        assertEquals(CORNER_BL, tight.getRGB(1, 2)); assertEquals(CORNER_BR, tight.getRGB(3, 3));
    }

    @Test void schemaNineCarriesTheSliceAndTheBorderMustLeaveAMiddle() throws Exception {
        var out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(frame(), "png", out);
        var image = ImageAsset.fromPng(out.toByteArray());
        var style = Screen.Style.defaults().withSlice(3, 2);
        var panel = new Screen.Element("panel", "", 0, 0, .8, .5, "decoration", "", "rectangle", false, null, false, image, style, 0, null);
        var screen = new Screen("frames", "FRAMES", List.of(panel), RenderQuality.low());
        String json = ScreenCodec.encode(screen);
        assertTrue(json.contains("\"slice\":3") && json.contains("\"sliceScale\":2") && json.contains("\"version\":9"), json);
        assertEquals(screen, ScreenCodec.decode(json));
        assertFalse(ScreenCodec.encode(new Screen("plain", "PLAIN", List.of(panel.withStyle(Screen.Style.defaults())), RenderQuality.low())).contains("slice"),
                "without a slice the JSON stays as before");
        var thick = assertThrows(IllegalArgumentException.class, () -> panel.withStyle(Screen.Style.defaults().withSlice(6, 1)));
        assertTrue(thick.getMessage().contains("middle"), thick.getMessage());
        assertThrows(IllegalArgumentException.class, () -> Screen.Style.defaults().withSlice(65, 1));
        assertThrows(IllegalArgumentException.class, () -> Screen.Style.defaults().withSlice(4, 5));
        assertThrows(IllegalArgumentException.class, () -> ScreenCodec.decode(json.replace("\"version\":9", "\"version\":8")));
        // Baked into the pack like any image: the canvas shows the frame's corner colour at the box corner.
        assertNotNull(HudAssets.create(List.of(screen)).frame(screen, null, 8, 4));
    }
}
