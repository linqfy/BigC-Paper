package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.ImageAsset;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.RenderQuality;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.testing.PngFixtures;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Blurs as drawn: element blur, frosted-glass backdrop (static canvas and runtime layers) and blurred shadows. */
class BlurRenderTest {
    private static final Screen.Style RED = new Screen.Style(1, "#ff0000", "#ffffff", "#ffffff", false);

    private static Screen.Element box(String id, String shape, boolean background, Screen.Style style, ImageAsset image, double width, double height, int layer) {
        return new Screen.Element(id, "", 0, 0, width, height, "decoration", "", shape, false, null, background, image, style, layer, null);
    }
    /** 64 x 36 black and white one-pixel columns, stretched over the whole screen. */
    private static Screen.Element stripes() throws Exception {
        int[] pixels = new int[64 * 36];
        for (int i = 0; i < pixels.length; i++) pixels[i] = (i % 64) % 2 == 0 ? 0xff000000 : 0xffffffff;
        return box("stripes", "rectangle", false, Screen.Style.defaults(), ImageAsset.fromPng(PngFixtures.rgba(64, 36, pixels)), 2, 2, 0);
    }
    private static BufferedImage render(Screen.Element... elements) {
        return new HudRaster.Batch(new Screen("blur", "BLUR", List.of(elements), RenderQuality.low())).render(null);
    }
    private static int alpha(BufferedImage image, int x, int y) { return image.getRGB(x, y) >>> 24; }
    private static boolean gray(int argb) {
        for (int shift : new int[]{16, 8, 0}) { int c = (argb >>> shift) & 255; if (c < 0x50 || c > 0xb0) return false; }
        return argb >>> 24 == 255;
    }

    @Test void elementBlurSoftensTheEdgesAndKeepsTheMiddle() {
        // Low quality: 512 x 288, two canvas pixels per logical pixel. The box spans x 128..383, y 72..215.
        var sharp = render(box("box", "rectangle", true, RED, null, 1, 1, 0));
        var soft = render(box("box", "rectangle", true, RED.withBlur(2, 0, 0), null, 1, 1, 0));
        assertEquals(0, alpha(sharp, 124, 144)); assertEquals(255, alpha(sharp, 128, 144));
        assertTrue(alpha(soft, 124, 144) > 0 && alpha(soft, 124, 144) < 128, "spills softly past the edge: " + alpha(soft, 124, 144));
        assertTrue(alpha(soft, 128, 144) > 80 && alpha(soft, 128, 144) < 200, "about half at the edge: " + alpha(soft, 128, 144));
        assertEquals(0xffff0000, soft.getRGB(256, 144), "far from the edges the colour is exact");
        assertEquals(0, alpha(soft, 100, 144), "nothing beyond three standard deviations");
    }

    @Test void backdropBlurSoftensWhatIsUnderneathOnlyInsideTheShape() throws Exception {
        var under = render(stripes());
        var glass = render(stripes(), box("glass", "ellipse", false, Screen.Style.defaults().withBlur(0, 4, 0), null, 1, 1, 1));
        assertTrue(gray(glass.getRGB(256, 144)), "stripes inside the glass become gray: " + Integer.toHexString(glass.getRGB(256, 144)));
        assertTrue(gray(glass.getRGB(300, 150)));
        assertEquals(under.getRGB(40, 40), glass.getRGB(40, 40), "outside the element nothing changes");
        assertEquals(under.getRGB(130, 74), glass.getRGB(130, 74), "the ellipse's corner, outside its shape, stays sharp");
        var drawnOver = render(stripes(), box("glass", "rounded", true, Screen.Style.defaults().withBlur(0, 4, 0), null, 1, 1, 1), box("label", "rectangle", true, RED, null, .2, .2, 2));
        assertEquals(0xffff0000, drawnOver.getRGB(256, 144), "elements above the glass stay sharp");
    }

    @Test void aRuntimeLayerBlursTheStaticCanvasItIsShownOver() throws Exception {
        var staticScreen = new Screen("blur", "BLUR", List.of(stripes()), RenderQuality.low());
        var staticCanvas = new HudRaster.Batch(staticScreen).render(null);
        var layerScreen = staticScreen.withElements(List.of(box("panel", "rectangle", false, Screen.Style.defaults().withBlur(0, 4, 0), null, 1, 1, 1)));
        var alone = new HudRaster.Batch(layerScreen).render(null);
        assertEquals(0, alpha(alone, 256, 144), "without the static canvas there is nothing to blur");
        var layer = new HudRaster.Batch(layerScreen).over(staticCanvas).render(null);
        assertTrue(gray(layer.getRGB(256, 144)), "the layer carries the blurred stripes: " + Integer.toHexString(layer.getRGB(256, 144)));
        assertEquals(0, alpha(layer, 40, 40), "outside the panel the layer stays transparent");
        assertThrows(IllegalArgumentException.class, () -> new HudRaster.Batch(layerScreen).over(new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB)));
    }

    @Test void blurredShadowFadesOutsideAndNeverDarkensTheElement() {
        var style = new Screen.Style(.5, "#ff0000", "#ffffff", "#ffffff", true).withBlur(0, 0, 3);
        var plain = render(box("card", "rounded", true, new Screen.Style(.5, "#ff0000", "#ffffff", "#ffffff", false), null, 1, 1, 0));
        var shadowed = render(box("card", "rounded", true, style, null, 1, 1, 0));
        int near = alpha(shadowed, 256, 220), far = alpha(shadowed, 256, 226);
        assertTrue(near > far && far > 0, "the shadow fades with distance: " + near + " then " + far);
        assertEquals(0, alpha(shadowed, 256, 260), "and ends");
        assertEquals(plain.getRGB(256, 144), shadowed.getRGB(256, 144), "a translucent card is not darkened by its own shadow");
        var classic = render(box("card", "rounded", true, new Screen.Style(.5, "#ff0000", "#ffffff", "#ffffff", true), null, 1, 1, 0));
        assertNotEquals(classic.getRGB(256, 226), shadowed.getRGB(256, 226), "shadowBlur replaces the classic shadow");
    }

    @Test void replaceMixesInPremultipliedAlpha() {
        assertEquals(0xff0000ff, HudRaster.replace(0x00ff0000, 0xff0000ff, 255));
        int half = HudRaster.replace(0x00ff0000, 0xff0000ff, 128);
        assertEquals(128, half >>> 24, 1);
        assertEquals(0, (half >>> 16) & 255, "an invisible red pixel adds no red");
        assertEquals(255, half & 255);
        assertEquals(0x12345678, HudRaster.replace(0x12345678, 0xffffffff, 0));
    }
}
