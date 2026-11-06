package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DisplayFontTest {
    @Test void scaledFramesAndCursorKeepZeroAdvanceUsingActualPngMetrics() throws Exception {
        var screen=new Screen("small","SMALL",List.of(new Screen.Element("go","GO",0,0,.4,.2,"button","close")));
        var assets=HudAssets.create(List.of(screen));var entries=assets.entries();
        for(int scale:assets.sizes()) {
            if(scale==4)continue;
            var advances=HudAssetsTest.advances(entries,new String(entries.get("assets/"+HudAssets.fontName(scale).replace(":","/font/")+".json"),StandardCharsets.UTF_8));
            for(int step=0;step<=8;step++) {
                String glyphs=assets.displayText(assets.frame(screen,null,step)+assets.cursor(screen,.51,-.72),scale);
                double sum=0;for(char ch:glyphs.toCharArray()){assertTrue(advances.containsKey(ch));sum+=advances.get(ch);}
                assertEquals(0,sum,.00001,"Each tile must restore the baseline independently");
            }
        }
    }
    @Test void largerDisplayFontsShareTexturesAndScaleEveryAdvanceTogether() {
        var assets=HudAssets.create(List.of(new Screen("small","SMALL",List.of(
            new Screen.Element("go","GO",0,0,.4,.2,"button","close")))));
        var entries=assets.entries();
        assertEquals(List.of(4,7,8,11),assets.sizes());
        String base=new String(entries.get("assets/tideframe/font/hud.json"),StandardCharsets.UTF_8);
        String large=new String(entries.get("assets/tideframe/font/hud_q7.json"),StandardCharsets.UTF_8);
        assertTrue(base.contains("\"ascent\":15,\"height\":16"), "1x canvas top sits exactly 72 GUI pixels above centre");
        assertTrue(large.contains("\"ascent\":28,\"height\":28"), "1.75x fills 1080p within the 256-pixel glyph limit");
        String twice=new String(entries.get("assets/tideframe/font/hud_q8.json"),StandardCharsets.UTF_8);
        assertTrue(twice.contains("\"ascent\":32,\"height\":32"), "2x: the canvas top is as high as the glyph limit allows");
        assertFalse(entries.containsKey("assets/tideframe/font/hud_x2.json"));
        assertEquals(base.split("\"file\":",-1).length,large.split("\"file\":",-1).length);
    }
}
