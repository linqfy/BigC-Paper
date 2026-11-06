package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BossBarAssetsTest {
    @Test void progressSelectsTwentyOneBoundedFramesWithoutInventingHealth() {
        assertEquals(0, BossBarAssets.frameIndex(0));
        assertEquals(0, BossBarAssets.frameIndex(.024f));
        assertEquals(1, BossBarAssets.frameIndex(.026f));
        assertEquals(15, BossBarAssets.frameIndex(.75f));
        assertEquals(20, BossBarAssets.frameIndex(1));
        for(float value:new float[]{Float.NaN,Float.NEGATIVE_INFINITY,-.01f,1.01f})
            assertThrows(IllegalArgumentException.class,()->BossBarAssets.frame(value));
    }

    @Test void allFramesHaveEqualAdvanceAndLabelIsCenteredWithoutChangingBarPosition() {
        var assets=BossBarAssets.create();
        for(int frame=0;frame<=20;frame++) {
            assertEquals(256,assets.advance(BossBarAssets.frame(frame/20f)));
            assertEquals(256,assets.advance(BossBarAssets.title("AURORA | "+(frame*5)+"%",frame/20f)));
        }
        assertThrows(IllegalArgumentException.class,()->BossBarAssets.title("A".repeat(100),1));
    }

    @Test void atlasTilesStayWithinVanillaLimitPreserveTransparencyAndAreDeterministic() throws Exception {
        var first=BossBarAssets.create().entries(); var second=BossBarAssets.build().entries();
        assertEquals(first.keySet(),second.keySet());
        String font=new String(first.get("assets/tideframe/font/boss.json"),StandardCharsets.UTF_8);
        assertTrue(font.contains("\"ascent\":2,\"height\":40"));
        assertTrue(font.contains("\"id\":\"tideframe:ui\""));
        int pngs=0,flashes=0;
        for(var entry:first.entrySet()) {
            assertArrayEquals(entry.getValue(),second.get(entry.getKey()));
            if(!entry.getKey().endsWith(".png"))continue;
            if(entry.getKey().contains("/flash_"))flashes++;else pngs++;
            var tile=ImageIO.read(new ByteArrayInputStream(entry.getValue()));
            assertEquals(256,tile.getWidth());assertEquals(160,tile.getHeight());
            assertEquals(0,tile.getRGB(0,0)>>>24,"Corner padding must stay transparent");
        }
        assertEquals(84,pngs,"Four tiles for each of twenty-one original frames");
        assertEquals(56,flashes,"0.23: the hit flash over each fill, only in the tiles the fill reaches");
        byte[] exposed=first.get("assets/tideframe/font/boss.json");exposed[0]=0;
        assertNotEquals(0,BossBarAssets.create().entries().get("assets/tideframe/font/boss.json")[0]);
    }

    @Test void fullAndEmptyStatesHaveDifferentFillButSameOpaqueNativeBarMask() throws Exception {
        var full=BossBarAssets.preview(1);var empty=BossBarAssets.preview(0);
        assertEquals(1024,full.getWidth());assertEquals(160,full.getHeight());
        assertEquals(0,full.getRGB(0,0)>>>24);
        // Title y3 + glyph bearing5 puts this raster at y8; native bar y12..17.
        for(int y=16;y<40;y++)for(int x=148;x<880;x++) {
            assertEquals(255,full.getRGB(x,y)>>>24,"The original plate covers native bar pixels");
            assertEquals(255,empty.getRGB(x,y)>>>24);
        }
        assertNotEquals(full.getRGB(600,32),empty.getRGB(600,32),"Health fill responds to progress");
        assertEquals(full.getRGB(64,70),empty.getRGB(64,70),"Own guardian medallion is not health");
    }
}
