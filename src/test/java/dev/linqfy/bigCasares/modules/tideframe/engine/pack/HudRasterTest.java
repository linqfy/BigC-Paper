package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.ImageAsset;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.testing.PngFixtures;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HudRasterTest {
    @Test void transparentDefaultAndIndependentBorderUseScreenCoordinates() {
        var element = new Screen.Element("test", "", 0, 0, 1, 1, "button", "");
        var screen = new Screen("menu", "MENU", List.of(element));
        var image = HudRaster.render(screen, null, 8);
        assertEquals(1024, image.getWidth()); assertEquals(576, image.getHeight());
        assertEquals(0, pixel(image, 128, 72));
        assertEquals(0xffffffff, pixel(image, 64, 36));
        assertEquals(0, pixel(image, 63, 36));
        assertEquals(0xff61dfd1, pixel(HudRaster.render(screen, "test", 8), 64, 36));
        var withoutBorder = new Screen("menu", "MENU", List.of(new Screen.Element("test", "", 0, 0, 1, 1,
                "button", "", "rectangle", false, null, false, null)));
        assertEquals(0, pixel(HudRaster.render(withoutBorder, null, 8), 64, 36));
    }

    @Test void shapesAreOwnGeometryAndBackgroundIsExplicit() {
        for (String shape : List.of("rounded", "ellipse")) {
            var screen = new Screen("shape", "SHAPE", List.of(new Screen.Element("shape", "", 0, 0, 1, 1,
                    "label", "", shape, false, null, true, null)));
            var image = HudRaster.render(screen, null, 8);
            assertEquals(0, pixel(image, 64, 36), shape + " transparent corner");
            assertNotEquals(0, pixel(image, 128, 72) >>> 24, shape + " filled center");
            assertEquals(image.getRGB(320, 160), image.getRGB(703, 415), shape + " symmetric geometry");
        }
    }

    @Test void customPixelsPreserveAlphaAndAspectAndTextUsesAuthoredStrokes() throws Exception {
        var own = ImageAsset.fromPng(PngFixtures.rgba(2, 1, 0x804080c0));
        var screen = new Screen("image", "IMAGE", List.of(new Screen.Element("image", "", 0, 0, .25, .5,
                "label", "", "rectangle", false, null, false, own)));
        var image = HudRaster.render(screen, null, 8);
        assertEquals(0x804080c0, pixel(image, 128, 72));
        assertEquals(0, pixel(image, 128, 55));
        var text = new Screen("text", "TEXT", List.of(new Screen.Element("text", "A", 0, 0, .5, .5,
                "label", "", "rectangle", false, null, false, null)));
        var textImage = HudRaster.render(text, null, 8);
        // The five-pixel glyph starts at center 128 - floor(5/2) = 126; A's first stroke is column 2.
        assertEquals(0xffffffff, pixel(textImage, 128, 68));
        assertEquals(0, pixel(textImage, 125, 68));
    }

    @Test void opaqueImageCannotEraseTheConfiguredBorderOrHoverFeedback() throws Exception {
        var own = ImageAsset.fromPng(PngFixtures.rgba(16, 9, 0xff123456));
        var screen = new Screen("image", "IMAGE", List.of(new Screen.Element("image", "", 0, 0, 1, 1,
                "button", "", "rectangle", true, null, false, own)));
        var normal = HudRaster.render(screen, null, 8);
        var hover = HudRaster.render(screen, "image", 8);
        assertEquals(0xffffffff, pixel(normal, 64, 36));
        assertEquals(0xff61dfd1, pixel(hover, 64, 36));
        assertEquals(0xff123456, pixel(normal, 128, 72));
        assertEquals(0xff123456, pixel(hover, 128, 72));
    }

    @Test void scaledPhotosInterpolateAdjacentPixelsRatherThanRepeatingHardBlocks() throws Exception {
        var own = ImageAsset.fromPng(PngFixtures.rgba(2, 1, 0xffff0000, 0xff0000ff));
        var screen = new Screen("photo", "PHOTO", List.of(new Screen.Element("photo", "", 0, 0, .25, .5,
                "label", "", "rectangle", false, null, false, own)));
        var rendered = HudRaster.render(screen, null, 8);
        assertEquals(0xff81007e, rendered.getRGB(511, 288), "Center-left mixes red129 and blue126");
        assertEquals(0xff7e0081, rendered.getRGB(512, 288), "Center-right mixes red126 and blue129");
        assertEquals(0xffff0000, rendered.getRGB(448, 288), "Source edges are clamped without fading");
        assertEquals(0xff0000ff, rendered.getRGB(575, 288));
        assertEquals(0, rendered.getRGB(512, 255), "Aspect-fit still leaves empty space above the landscape");
    }

    @Test void photoFilteringPremultipliesAlphaToAvoidColoredOrDarkHalos() throws Exception {
        var own = ImageAsset.fromPng(PngFixtures.rgba(2, 1, 0x00ff0000, 0xff4080c0));
        var screen = new Screen("photo", "PHOTO", List.of(new Screen.Element("photo", "", 0, 0, .25, .5,
                "label", "", "rectangle", false, null, false, own)));
        var rendered = HudRaster.render(screen, null, 8);
        assertEquals(0x7e4080c0, rendered.getRGB(511, 288), "Transparent red contributes no RGB; alpha is126");
        assertEquals(0x814080c0, rendered.getRGB(512, 288), "Visible shade stays exact at alpha129");
        assertEquals(0, rendered.getRGB(448, 288));
        assertEquals(0xff4080c0, rendered.getRGB(575, 288));
    }

    @Test void photoFilteringUsesBothAxesAndKeepsOneToOneRgbaPixelsExact() throws Exception {
        var own = ImageAsset.fromPng(PngFixtures.rgba(2, 2, 0xffff0000, 0xff00ff00, 0xff0000ff, 0xffffffff));
        var screen = new Screen("photo", "PHOTO", List.of(new Screen.Element("photo", "", 0, 0, .25, .5,
                "label", "", "rectangle", false, null, false, own)));
        var rendered = HudRaster.render(screen, null, 8);
        assertEquals(0xff807e7e, rendered.getRGB(511, 287), "Four samples contribute at the upper-left center");
        assertEquals(0xff808181, rendered.getRGB(512, 288), "Four samples contribute at the lower-right center");
        int[] pixels = new int[128 * 64];
        for (int index = 0; index < pixels.length; index++)
            pixels[index] = ((index % 2 == 0 ? 128 : 255) << 24) | (index * 3571 & 0xffffff);
        var exact = ImageAsset.fromPng(PngFixtures.rgba(128, 64, pixels));
        var exactScreen = new Screen("exact", "EXACT", List.of(new Screen.Element("photo", "", 0, 0, .25, .5,
                "label", "", "rectangle", false, null, false, exact)));
        var oneToOne = HudRaster.render(exactScreen, null, 8);
        assertArrayEquals(pixels, oneToOne.getRGB(448, 256, 128, 64, null, 0, 128));
    }

    @Test void entranceHasFadeSlideAndScaleAndInputIsBounded() {
        var screen = new Screen("menu", "MENU", List.of(new Screen.Element("box", "", 0, 0, 1, 1,
                "button", "", "rectangle", false, null, true, null)));
        assertEquals(0, pixel(HudRaster.render(screen, null, 0), 128, 72));
        var middle = HudRaster.render(screen, null, 4);
        var end = HudRaster.render(screen, null, 8);
        assertTrue((pixel(middle, 128, 77) >>> 24) > 0);
        assertTrue((pixel(middle, 128, 77) >>> 24) < (pixel(end, 128, 72) >>> 24));
        assertEquals(0, pixel(middle, 64, 36));
        assertNotEquals(0, pixel(end, 64, 36));
        assertThrows(IllegalArgumentException.class, () -> HudRaster.render(screen, null, -1));
        assertThrows(IllegalArgumentException.class, () -> HudRaster.render(screen, null, 9));
    }

    @Test void quadrupledTextureKeepsTextAndBorderAtTheirOriginalLogicalSize() {
        var text = new Screen("text", "TEXT", List.of(new Screen.Element("letter", "A", 0, 0, 1, 1,
                "label", "", "rectangle", false, null, false, null)));
        var rendered = HudRaster.render(text, null, 8);
        int painted = 0;
        for (int y = 0; y < rendered.getHeight(); y++) for (int x = 0; x < rendered.getWidth(); x++)
            if ((rendered.getRGB(x, y) >>> 24) != 0) {
                painted++;
                assertTrue(x >= 504 && x < 524 && y >= 272 && y < 300, "A remains 5 by 7 logical units");
            }
        assertEquals(256, painted, "16 authored strokes each occupy 4 by 4 texture pixels");
        var bordered = HudRaster.render(new Screen("box", "BOX", List.of(
                new Screen.Element("box", "", 0, 0, 1, 1, "label", ""))), null, 8);
        assertEquals(255, bordered.getRGB(256, 200) >>> 24);
        assertEquals(255, bordered.getRGB(259, 200) >>> 24);
        assertEquals(0, bordered.getRGB(260, 200) >>> 24, "Outline remains one logical unit wide");
    }

    @Test void curvedEdgesHaveCoverageAlphaAndRoundedCornersStaySubtle() {
        for (String shape : List.of("rounded", "ellipse")) {
            var rendered = HudRaster.render(new Screen("curve", "CURVE", List.of(new Screen.Element(
                    "curve", "", 0, 0, 1, 1, "label", "", shape, true, null, false, null))), null, 8);
            boolean partialAlpha = false;
            for (int y = 144; y < 208; y++) for (int x = 256; x < 352; x++) {
                int alpha = rendered.getRGB(x, y) >>> 24;
                partialAlpha |= alpha > 0 && alpha < 255;
            }
            assertTrue(partialAlpha, shape + " uses partial coverage rather than hard staircase edges");
        }
        var rounded = HudRaster.render(new Screen("rounded", "ROUNDED", List.of(new Screen.Element(
                "curve", "", 0, 0, 1, 1, "label", "", "rounded", false, null, true, null))), null, 8);
        assertEquals(224, rounded.getRGB(288, 144) >>> 24, "Top edge starts within 8 logical units of the corner");
        assertEquals(0, rounded.getRGB(256, 144) >>> 24, "Corner is still rounded");
    }

    private static int pixel(BufferedImage image, int logicalX, int logicalY) {
        return image.getRGB(logicalX * 4, logicalY * 4);
    }
}
