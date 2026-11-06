package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.RenderQuality;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ActionParser;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.Motion;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Keyframes bake one runtime layer per frame, drawn with the frame's offset, scale, opacity and rotation. */
class KeyframeLayersTest {
    private static Screen screen(String keyframes, String kind) {
        var box = new Screen.Element("box", "", 0, 0, .25, .2, kind, "", "rectangle", false, null, true, null,
                new Screen.Style(1, "#ff0000", "#ffffff", "#ffffff", false, 0, 0, 0, null, "none", "center", 0), 0, null)
                .withLogic(ActionParser.element(ScreenCodec.parse("{\"keyframes\":" + keyframes + "}")));
        return new Screen("anim", "ANIM", List.of(box), RenderQuality.custom(1024, 576));
    }
    private static int[] bounds(BufferedImage image) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1, alpha = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int a = image.getRGB(x, y) >>> 24;
            if (a == 0) continue;
            minX = Math.min(minX, x); maxX = Math.max(maxX, x); minY = Math.min(minY, y); maxY = Math.max(maxY, y); alpha = Math.max(alpha, a);
        }
        return new int[]{minX, minY, maxX, maxY, alpha};
    }

    @Test void posesMoveScaleAndFadeTheElement() {
        var screen = screen("{\"steps\":[{\"at\":0},{\"at\":50}]}", "label");
        var batch = new HudRaster.Batch(screen);
        int[] base = bounds(batch.render(null, null));
        int[] moved = bounds(batch.render(null, new Motion.Pose(10, -5, 1, 1, 0)));
        assertEquals(base[0] + 40, moved[0]); assertEquals(base[2] + 40, moved[2], "x is in logical pixels (4 raster px each at 1024 wide)");
        assertEquals(base[1] - 20, moved[1]); assertEquals(base[3] - 20, moved[3]);
        int[] big = bounds(batch.render(null, new Motion.Pose(0, 0, 2, 1, 0)));
        assertEquals((base[2] - base[0] + 1) * 2, big[2] - big[0] + 1, 2, "scaled about the centre");
        assertEquals((base[0] + base[2]) / 2.0, (big[0] + big[2]) / 2.0, 1.5);
        int[] faded = bounds(batch.render(null, new Motion.Pose(0, 0, 1, .5, 0)));
        assertEquals(255, base[4]); assertEquals(128, faded[4], 1);
        assertEquals(-1, bounds(batch.render(null, new Motion.Pose(0, 0, 1, 0, 0)))[2], "opacity 0 draws nothing");
        int[] turned = bounds(batch.render(null, new Motion.Pose(0, 0, 1, 1, 90)));
        assertEquals(base[3] - base[1], turned[2] - turned[0], 2, "a quarter turn swaps width and height");
    }

    @Test void everyFrameIsALayerAndHoverFramesReplaceTheHoverLook() {
        var screen = screen("{\"trigger\":\"loop\",\"easing\":\"linear\",\"steps\":[{\"at\":0,\"x\":-20},{\"at\":200,\"x\":20}]}", "label");
        var assets = HudAssets.create(List.of(screen));
        assertEquals(java.util.Set.of("box"), assets.runtimeIds(screen));
        assertEquals(5, assets.frameCount(screen, "box"));
        var glyphs = new java.util.HashSet<String>();
        for (int k = 0; k < 5; k++) for (int q : assets.sizes()) assertNotNull(assets.layer(screen, "box@" + k, q), "frame " + k + " at " + q);
        for (int k = 0; k < 5; k++) glyphs.add(assets.layer(screen, "box@" + k, 4));
        assertEquals(5, glyphs.size(), "each frame draws the box somewhere else");
        assertNull(assets.layer(screen, "box", 4), "keyframed elements have no still layer");
        var hover = screen("{\"trigger\":\"hover\",\"steps\":[{\"at\":0},{\"at\":100,\"scale\":1.2}]}", "button");
        var hoverAssets = HudAssets.create(List.of(hover));
        assertEquals(3, hoverAssets.frameCount(hover, "box"));
        assertNull(hoverAssets.layer(hover, "box@hover", 4), "the frames are the hover look");
        // The static canvas leaves the animated element out.
        var canvas = HudRaster.render(screen.withElements(List.of()), null, 8);
        assertEquals(-1, bounds(canvas)[2]);
    }
}
