package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Idea 40: boss bar frame designs from the same shapes, and the hit's flash and shake. */
class BossBarStylesTest {
    @Test void everyStyleKeepsTheShapesAndChangesTheColours() {
        var rose = BossBarAssets.preview(.5f, null, "rose", false);
        assertArrayEquals(BossBarAssets.preview(.5f).getRGB(0, 0, 1024, 160, null, 0, 1024), rose.getRGB(0, 0, 1024, 160, null, 0, 1024), "rose is the original design");
        for (String style : new String[]{"frost", "ember"}) {
            var other = BossBarAssets.preview(.5f, null, style, false);
            for (int y = 0; y < 160; y += 2) for (int x = 0; x < 1024; x += 2)
                assertEquals(rose.getRGB(x, y) >>> 24 != 0, other.getRGB(x, y) >>> 24 != 0, style + ": same shape at " + x + "," + y);
            assertNotEquals(rose.getRGB(300, 44), other.getRGB(300, 44), style + ": its own fill colour");
            assertNotEquals(rose.getRGB(400, 12), other.getRGB(400, 12), style + ": its own frame colour");
        }
        assertSame(BossBarAssets.create(), BossBarAssets.create(null, "rose"));
        assertEquals(BossBarAssets.create().entries().keySet(), BossBarAssets.create(null, "ember").entries().keySet(), "same files, so the same glyphs");
        assertThrows(IllegalArgumentException.class, () -> BossBarAssets.create(null, "gold"));
    }
    @Test void theFlashLightsOnlyTheFillAndHitsKeepTheBarInPlace() {
        var plain = BossBarAssets.preview(.5f, null, "rose", false);
        var lit = BossBarAssets.preview(.5f, null, "rose", true);
        int fill = lit.getRGB(300, 44);
        assertTrue((fill >> 16 & 255) > 210 && (fill >> 8 & 255) > 210 && (fill & 255) > 210, "the fill turns nearly white");
        assertEquals(plain.getRGB(900, 44), lit.getRGB(900, 44), "the empty track is not lit");
        assertEquals(plain.getRGB(64, 70), lit.getRGB(64, 70), "nor the medallion");
        var assets = BossBarAssets.create();
        for (int frame = 0; frame <= 20; frame++) for (int shake = -2; shake <= 1; shake++) {
            String title = BossBarAssets.title("GOLEM | " + frame * 5 + "%", frame / 20f, shake, shake % 2 == 0);
            assertEquals(256, assets.advance(title), "frame " + frame + ", shake " + shake + ": the bar does not recentre");
        }
        assertEquals(BossBarAssets.title("GOLEM", .5f), BossBarAssets.title("GOLEM", .5f, 0, false));
        assertThrows(IllegalArgumentException.class, () -> BossBarAssets.title("GOLEM", .5f, 2, false), "more than 1 px to the right would show the native bar");
    }
}
