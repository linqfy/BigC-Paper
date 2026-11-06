package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.ImageAsset;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BossPortraitAssetsTest {
    @Test void nullPortraitKeepsTheOriginalRegistryAndEveryPixel() {
        assertSame(BossBarAssets.create(), BossBarAssets.create(null));
        var expected = BossBarAssets.preview(.75f);
        var actual = BossBarAssets.preview(.75f, null);
        assertArrayEquals(expected.getRGB(0, 0, 1024, 160, null, 0, 1024),
                actual.getRGB(0, 0, 1024, 160, null, 0, 1024));
    }

    @Test void customPortraitChangesOnlyTheFirstTileAndKeepsEveryGlyphMetric() throws Exception {
        var portrait = portrait(16, 8, 0xff26e85a);
        var original = BossBarAssets.create();
        var custom = BossBarAssets.create(portrait);
        var expected = original.entries();
        var actual = custom.entries();
        assertEquals(expected.keySet(), actual.keySet());
        int changedPortraits = 0;
        for (var entry : expected.entrySet()) {
            String path = entry.getKey();
            if (path.endsWith("_0.png") && path.contains("/frame_")) { // the hit flash (flash_*) has no face
                assertFalse(java.util.Arrays.equals(entry.getValue(), actual.get(path)), path);
                changedPortraits++;
            } else assertArrayEquals(entry.getValue(), actual.get(path), path);
            if (path.endsWith(".png")) {
                var decoded = ImageIO.read(new ByteArrayInputStream(actual.get(path)));
                assertEquals(256, decoded.getWidth());
                assertEquals(160, decoded.getHeight());
            }
        }
        assertEquals(21, changedPortraits);
        for (int frame = 0; frame <= 20; frame++) {
            String glyphs = BossBarAssets.frame(frame / 20f);
            for (char character : glyphs.toCharArray())
                assertEquals(original.advance(String.valueOf(character)), custom.advance(String.valueOf(character)));
            assertEquals(256, custom.advance(glyphs));
        }
        byte[] exposed = actual.get("assets/tideframe/textures/font/boss/frame_0_0.png");
        exposed[0] = 0;
        assertEquals(137, custom.entries().get("assets/tideframe/textures/font/boss/frame_0_0.png")[0] & 255);
    }

    @Test void rectangularPortraitRetainsItsAspectRatioAndDoesNotTouchBarOrGoldBorder() throws Exception {
        var actual = BossBarAssets.preview(1, portrait(16, 8, 0xff26e85a));
        var original = BossBarAssets.preview(1);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1, colored = 0;
        for (int y = 0; y < 160; y++) for (int x = 0; x < 1024; x++) {
            if (actual.getRGB(x, y) == 0xff26e85a) {
                colored++;
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
            }
            if (x >= 256 || x < 28 || x >= 144 || y < 20 || y >= 128)
                assertEquals(original.getRGB(x, y), actual.getRGB(x, y), "Non-portrait pixel at " + x + "," + y);
        }
        assertTrue(colored > 1000, "The imported image must appear in the medallion");
        // A wide source is fitted without stretching; clipped polygon corners can shorten its ink width.
        assertTrue(maxX - minX + 1 > (maxY - minY + 1) * 1.7);
        assertEquals(original.getRGB(24, 40), actual.getRGB(24, 40), "Own gold border stays visible");
        assertEquals(original.getRGB(600, 32), actual.getRGB(600, 32), "Health is independent of the face");
    }

    @Test void alphaIsCompositedAndInputAndOtherInstancesRemainImmutable() throws Exception {
        var image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(4, 4, 0x8026e85a);
        var portrait = normalized(image);
        byte[] before = portrait.png();
        var actual = BossBarAssets.preview(1, portrait);
        assertEquals(0xff182532, actual.getRGB(60, 70), "Transparent source reveals the medallion backing");
        int blended = actual.getRGB(93, 84);
        assertEquals(255, blended >>> 24, "Portrait alpha is composited over the opaque backing");
        assertNotEquals(0xff182532, blended);
        assertNotEquals(0xff26e85a, blended);
        assertArrayEquals(before, portrait.png());
        actual.setRGB(60, 70, 0);
        assertEquals(0xff182532, BossBarAssets.preview(1, portrait).getRGB(60, 70));
        assertArrayEquals(BossBarAssets.build().entries().get("assets/tideframe/font/boss.json"),
                BossBarAssets.create().entries().get("assets/tideframe/font/boss.json"));
    }

    private static ImageAsset portrait(int width, int height, int color) throws Exception {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) image.setRGB(x, y, color);
        return normalized(image);
    }

    private static ImageAsset normalized(BufferedImage image) throws Exception {
        var output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "PNG", output));
        return ImageAsset.fromPng(output.toByteArray());
    }
}
