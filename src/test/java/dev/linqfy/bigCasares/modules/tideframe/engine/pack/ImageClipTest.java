package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.ImageAsset;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.RenderQuality;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import dev.linqfy.bigCasares.modules.tideframe.engine.testing.PngFixtures;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** style.imageClip (schema 9): the picture takes the element's shape; without it the game draws it square. */
class ImageClipTest {
    private static Screen.Element photo(String shape, boolean clip) throws Exception {
        int[] blue = new int[32 * 32];
        Arrays.fill(blue, 0xff2050e0);
        // A 32 x 32 logical box (64 x 64 canvas pixels at low quality), in the middle: x 224..287, y 112..175.
        return new Screen.Element("photo", "", 0, 0, .25, 32 / 72.0, "decoration", "", shape, false, null, false,
                ImageAsset.fromPng(PngFixtures.rgba(32, 32, blue)), Screen.Style.defaults().withImageClip(clip), 0, null);
    }
    private static BufferedImage render(Screen.Element element) {
        return new HudRaster.Batch(new Screen("clip", "CLIP", List.of(element), RenderQuality.low())).render(null);
    }
    private static int alpha(BufferedImage image, int x, int y) { return image.getRGB(x, y) >>> 24; }

    @Test void aClippedPictureTakesTheEllipseAndAnUnclippedOneStaysSquare() throws Exception {
        var square = render(photo("ellipse", false));
        var round = render(photo("ellipse", true));
        assertEquals(255, alpha(square, 225, 113), "without the option the corner is drawn");
        assertEquals(0, alpha(round, 225, 113), "with it the corner is cut away");
        assertEquals(0xff2050e0, round.getRGB(256, 144), "the middle keeps its colour");
        assertEquals(255, alpha(round, 256, 113), "the top of the circle is still there");
        boolean partial = false;
        for (int x = 224; x < 256 && !partial; x++) { int a = alpha(round, x, 120); partial = a > 0 && a < 255; }
        assertTrue(partial, "the rim is antialiased like the fill");
    }
    @Test void aNineSlicePictureIsCutToo() throws Exception {
        int[] blue = new int[32 * 32];
        Arrays.fill(blue, 0xff2050e0);
        for (boolean clip : new boolean[]{false, true}) {
            var framed = new Screen.Element("frame", "", 0, 0, .25, 32 / 72.0, "decoration", "", "ellipse", false, null, false,
                    ImageAsset.fromPng(PngFixtures.rgba(32, 32, blue)), Screen.Style.defaults().withImageClip(clip).withSlice(4, 1), 0, null);
            var image = render(framed);
            assertEquals(clip ? 0 : 255, alpha(image, 225, 113), clip ? "cut to the ellipse" : "square without the option");
            assertEquals(0xff2050e0, image.getRGB(256, 144), "the middle is drawn either way");
        }
    }
    @Test void roundedCornersCutOnlyTheCornersAndRectanglesNothing() throws Exception {
        var rounded = render(photo("rounded", true));
        var rectangle = render(photo("rectangle", true));
        assertEquals(0, alpha(rounded, 224, 112), "the very corner of a rounded box is outside");
        assertEquals(255, alpha(rounded, 256, 112), "its straight edge is inside");
        assertEquals(255, alpha(rectangle, 224, 112), "a rectangle has nothing to cut");
    }
    @Test void schemaNineCarriesTheOptionAndOlderSchemasRefuseIt() throws Exception {
        var screen = new Screen("clip", "CLIP", List.of(photo("ellipse", true)), RenderQuality.low());
        String json = ScreenCodec.encode(screen);
        assertTrue(json.contains("\"imageClip\":true"), json);
        assertEquals(screen, ScreenCodec.decode(json));
        String off = ScreenCodec.encode(new Screen("clip", "CLIP", List.of(photo("ellipse", false)), RenderQuality.low()));
        assertFalse(off.contains("imageClip"), "pages without the option keep their exact JSON");
        assertThrows(IllegalArgumentException.class, () -> ScreenCodec.decode(json.replace("\"version\":9", "\"version\":8")));
        assertThrows(IllegalArgumentException.class, () -> ScreenCodec.decode(json.replace("\"imageClip\":true", "\"imageClip\":\"yes\"")));
    }
}
